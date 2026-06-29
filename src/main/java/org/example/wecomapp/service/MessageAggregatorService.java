package org.example.wecomapp.service;

import org.example.wecomapp.client.DifyApiClient;
import org.example.wecomapp.client.WecomApiClient;
import org.example.wecomapp.constants.WecomConstants;
import org.example.wecomapp.dto.DifyChatResponse;
import org.example.wecomapp.dto.Reply;
import org.example.wecomapp.dto.SendMsgResponse;
import org.example.wecomapp.dto.SyncMsgResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 消息聚合与防抖服务
 *
 * <p>架构设计：</p>
 * <ul>
 *   <li>每次回调独立拉取消息（sync_msg 的 cursor 仅用于单次分页，不跨回调复用）</li>
 *   <li>增量去重靠每用户的 lastMsgId（msgid > lastMsgId 才是新消息）</li>
 *   <li>同一用户：防抖 3 秒后串行处理批次</li>
 *   <li>不同用户：并行处理</li>
 * </ul>
 *
 * @author dixonyen
 */
@Service
public class MessageAggregatorService {

    private static final long DEBOUNCE_MS = 3000;
    private static final int MAX_FILES_PER_QUERY = 3;

    private final WecomApiClient wecomApiClient;
    private final DifyApiClient difyApiClient;
    private final DifyConversationService difyConversationService;
    private final ProcessedMessageTracker messageTracker;
    private final SessionStateService sessionStateService;

    /** debounce 定时器（单线程即可，每个任务很轻量：只是提交到 processExecutor） */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /** 多用户并行处理 */
    private final ExecutorService processExecutor = Executors.newFixedThreadPool(8);

    /** 每个客服帐号的全局状态（cursor 等） */
    private final ConcurrentHashMap<String, OpenKfidState> openKfidStates = new ConcurrentHashMap<>();

    /** 每用户的处理锁，保证同一用户串行 */
    private final ConcurrentHashMap<String, Object> userLocks = new ConcurrentHashMap<>();

    /** 每用户状态：lastMsgId + 防抖定时器 + 缓冲区 */
    private final ConcurrentHashMap<String, UserState> userStates = new ConcurrentHashMap<>();

    /** 用户昵称缓存（external_userid → nickname），避免重复请求 */
    private final ConcurrentHashMap<String, String> nicknameCache = new ConcurrentHashMap<>();

    static class OpenKfidState {
        volatile String token;
        volatile String globalCursor; // 跨回调持久化的全局游标
    }

    static class UserState {
        volatile String lastProcessedMsgId;
        volatile ScheduledFuture<?> scheduledTask;
        final List<SyncMsgResponse.MsgItem> bufferedMessages = new CopyOnWriteArrayList<>();
    }

    public MessageAggregatorService(WecomApiClient wecomApiClient,
                                    DifyApiClient difyApiClient,
                                    DifyConversationService difyConversationService,
                                    ProcessedMessageTracker messageTracker,
                                    SessionStateService sessionStateService) {
        this.wecomApiClient = wecomApiClient;
        this.difyApiClient = difyApiClient;
        this.difyConversationService = difyConversationService;
        this.messageTracker = messageTracker;
        this.sessionStateService = sessionStateService;
    }

    /**
     * 入口方法
     */
    public void onCallback(String token, String openKfid) {
        System.out.println("\n========== [Aggregator] onCallback ==========");
        System.out.println("  openKfid: " + openKfid);

        // 获取或创建客服帐号全局状态
        OpenKfidState state = openKfidStates.computeIfAbsent(openKfid, k -> new OpenKfidState());
        state.token = token;

        // 使用全局 cursor 增量拉取（无 cursor 时拉取 3 天历史）
        boolean isFirstFetch = (state.globalCursor == null);
        List<SyncMsgResponse.MsgItem> allMessages = fetchWithCursor(token, openKfid, state);
        System.out.println("  拉取: " + allMessages.size() + " 条" + (isFirstFetch ? "（首次）" : "（增量）"));

        if (allMessages.isEmpty()) {
            return;
        }

        // 按用户分组（只保留用户消息）
        Map<String, List<SyncMsgResponse.MsgItem>> grouped = allMessages.stream()
                .filter(m -> m.getOrigin() != null && m.getOrigin() == WecomConstants.MsgOrigin.WECHAT_USER)
                .filter(m -> m.getExternal_userid() != null)
                .collect(Collectors.groupingBy(SyncMsgResponse.MsgItem::getExternal_userid));

        System.out.println("  涉及用户: " + grouped.size());

        for (var entry : grouped.entrySet()) {
            scheduleUserProcessing(entry.getKey(), entry.getValue(), openKfid);
        }

        System.out.println("=============================================");
    }

    /**
     * 使用全局 cursor 增量拉取消息
     *
     * <p>cursor 跨回调持久化：首次无 cursor 拉取 3 天历史，后续从上次位置增量拉取。</p>
     * <p>每拿到 next_cursor 就持久化，无论 has_more 是否为 1，
     * 确保下次回调能从正确位置继续拉取。</p>
     */
    private List<SyncMsgResponse.MsgItem> fetchWithCursor(String token, String openKfid, OpenKfidState state) {
        List<SyncMsgResponse.MsgItem> allMessages = new ArrayList<>();
        String cursor = state.globalCursor;

        do {
            SyncMsgResponse syncResult = wecomApiClient.syncMsg(token, openKfid, cursor, 1000);
            if (syncResult.getMsg_list() != null) {
                allMessages.addAll(syncResult.getMsg_list());
            }

            String nextCursor = syncResult.getNext_cursor();

            // 只要返回了 next_cursor 就持久化（不论 has_more）
            if (nextCursor != null && !nextCursor.isEmpty()) {
                state.globalCursor = nextCursor;
                cursor = nextCursor;
            }

            // has_more != 1 时停止分页
            if (syncResult.getHas_more() == null || syncResult.getHas_more() != 1) {
                break;
            }
        } while (true);

        System.out.println("  globalCursor: " + state.globalCursor);
        return allMessages;
    }

    /**
     * 调度单个用户的消息处理
     *
     * <p>增量过滤（lastMsgId）→ 冷启动只取最后一条 → 缓冲 → 防抖</p>
     */
    private void scheduleUserProcessing(String userId, List<SyncMsgResponse.MsgItem> messages, String openKfid) {
        UserState userState = userStates.computeIfAbsent(userId, k -> new UserState());

        // 增量过滤：按位置顺序找到 lastProcessedMsgId，取其后的消息
        List<SyncMsgResponse.MsgItem> incremental;
        if (userState.lastProcessedMsgId == null || userState.lastProcessedMsgId.isEmpty()) {
            // 冷启动/新用户：只取最后一条，避免把 3 天历史全部发给 Dify
            SyncMsgResponse.MsgItem lastMsg = messages.get(messages.size() - 1);
            incremental = List.of(lastMsg);
            System.out.println("  用户 " + userId + ": 冷启动/新用户，取最后一条 msgid=" + lastMsg.getMsgid());
        } else {
            // 在列表中按位置查找 lastProcessedMsgId
            int lastIndex = -1;
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (userState.lastProcessedMsgId.equals(messages.get(i).getMsgid())) {
                    lastIndex = i;
                    break;
                }
            }

            if (lastIndex >= 0) {
                // 找到：取 lastMsgId 之后的消息
                incremental = messages.subList(lastIndex + 1, messages.size());
            } else {
                // 未找到（lastMsgId 不在本批次中）：取全部
                incremental = messages;
            }
            System.out.println("  用户 " + userId + ": 增量 " + incremental.size() + " 条（lastMsgId=" + userState.lastProcessedMsgId + "）");
        }

        if (incremental.isEmpty()) {
            return;
        }

        // 加入缓冲区（去重）
        for (SyncMsgResponse.MsgItem msg : incremental) {
            boolean exists = userState.bufferedMessages.stream()
                    .anyMatch(m -> m.getMsgid().equals(msg.getMsgid()));
            if (!exists) {
                userState.bufferedMessages.add(msg);
            }
        }

        System.out.println("  用户 " + userId + ": 缓冲区 " + userState.bufferedMessages.size() + " 条");

        // 取消旧定时器，重新防抖
        if (userState.scheduledTask != null && !userState.scheduledTask.isDone()) {
            userState.scheduledTask.cancel(false);
        }

        // 防抖后提交到并行线程池
        final String uid = userId;
        final String okfid = openKfid;
        userState.scheduledTask = scheduler.schedule(
                () -> processExecutor.submit(() -> processUserMessages(uid, okfid)),
                DEBOUNCE_MS, TimeUnit.MILLISECONDS
        );
    }

    /**
     * 处理单个用户的聚合消息（在 processExecutor 线程中执行）
     *
     * <p>使用 per-user 锁保证同一用户串行处理。</p>
     */
    private void processUserMessages(String userId, String openKfid) {
        // per-user 锁：同一用户的多个批次串行执行
        Object lock = userLocks.computeIfAbsent(userId, k -> new Object());
        synchronized (lock) {
            UserState userState = userStates.get(userId);
            if (userState == null || userState.bufferedMessages.isEmpty()) {
                return;
            }

            List<SyncMsgResponse.MsgItem> messages = new ArrayList<>(userState.bufferedMessages);
            userState.bufferedMessages.clear();

            System.out.println("\n========== [Aggregator] 处理用户消息 ==========");
            System.out.println("  用户: " + userId);
            System.out.println("  消息总数: " + messages.size());

            try {
                ensureBotService(openKfid, userId);

                // 分批
                List<List<SyncMsgResponse.MsgItem>> batches = splitIntoBatches(messages);
                System.out.println("  分批数: " + batches.size());

                // 串行处理每一批
                for (int i = 0; i < batches.size(); i++) {
                    List<SyncMsgResponse.MsgItem> batch = batches.get(i);
                    String firstId = batch.get(0).getMsgid();
                    String lastId = batch.get(batch.size() - 1).getMsgid();
                    int fileCount = (int) batch.stream()
                            .filter(m -> "image".equals(m.getMsgtype()) || "voice".equals(m.getMsgtype()))
                            .count();

                    System.out.println("  --- 批次 " + (i + 1) + "/" + batches.size()
                            + " [msgid: " + firstId + " ~ " + lastId + "]"
                            + " 消息: " + batch.size() + "条, 文件: " + fileCount + "个 ---");

                    String answer = processSingleBatch(batch, userId);
                    if (answer != null && !answer.isEmpty()) {
                        SendMsgResponse sendResult = wecomApiClient.sendMsg(userId, openKfid, "text",
                                new org.json.JSONObject().put("content", answer));
                        System.out.println("  批次 " + (i + 1) + " 发送: " + sendResult.getErrcode());
                    }
                }

                // 更新 lastMsgId
                String maxMsgId = messages.stream()
                        .map(SyncMsgResponse.MsgItem::getMsgid)
                        .filter(id -> id != null)
                        .max(String::compareTo)
                        .orElse(null);
                if (maxMsgId != null) {
                    userState.lastProcessedMsgId = maxMsgId;
                }
                messageTracker.markAllProcessed(userId,
                        messages.stream().map(SyncMsgResponse.MsgItem::getMsgid).toList());
                System.out.println("  lastMsgId 更新为: " + maxMsgId);

            } catch (Exception e) {
                System.err.println("  处理用户消息异常: " + e.getMessage());
                e.printStackTrace(System.err);
            }

            System.out.println("================================================\n");
        }
    }

    // ==================== 分批逻辑 ====================

    /**
     * 按 Dify 文件限制（每批最多 3 个文件）分批
     */
    private List<List<SyncMsgResponse.MsgItem>> splitIntoBatches(List<SyncMsgResponse.MsgItem> messages) {
        List<SyncMsgResponse.MsgItem> sorted = new ArrayList<>(messages);
        sorted.sort((a, b) -> {
            Long tA = a.getSend_time() != null ? a.getSend_time() : 0L;
            Long tB = b.getSend_time() != null ? b.getSend_time() : 0L;
            return tA.compareTo(tB);
        });

        List<List<SyncMsgResponse.MsgItem>> batches = new ArrayList<>();
        List<SyncMsgResponse.MsgItem> currentBatch = new ArrayList<>();
        int fileCount = 0;

        for (SyncMsgResponse.MsgItem msg : sorted) {
            boolean isFile = "image".equals(msg.getMsgtype()) || "voice".equals(msg.getMsgtype());

            if (isFile && fileCount >= MAX_FILES_PER_QUERY) {
                if (!currentBatch.isEmpty()) {
                    batches.add(currentBatch);
                }
                currentBatch = new ArrayList<>();
                fileCount = 0;
            }

            currentBatch.add(msg);
            if (isFile) {
                fileCount++;
            }
        }

        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        return batches;
    }

    /**
     * 处理单个批次 → 上传文件 + 调用 Dify
     */
    private String processSingleBatch(List<SyncMsgResponse.MsgItem> batch, String userId) {
        List<SyncMsgResponse.MsgItem> images = filterByType(batch, "image");
        List<SyncMsgResponse.MsgItem> voices = filterByType(batch, "voice");
        List<SyncMsgResponse.MsgItem> texts = filterByType(batch, "text");

        List<String> fileIds = new ArrayList<>();
        fileIds.addAll(uploadImages(images, userId));
        fileIds.addAll(uploadVoices(voices, userId));

        String query;
        if (!texts.isEmpty()) {
            query = combineTexts(texts);
        } else if (!images.isEmpty()) {
            query = "请分析图片内容";
        } else if (!voices.isEmpty()) {
            query = "请分析语音内容";
        } else {
            return null;
        }

        System.out.println("  query: " + query);
        System.out.println("  文件数: " + fileIds.size());

        String convId = difyConversationService.getConversationId(userId);
        String nickname = getNickname(userId);
        DifyChatResponse resp = difyApiClient.chatMessage(query, userId, convId,
                fileIds.isEmpty() ? null : fileIds, nickname);
        cacheConversationId(userId, resp.getConversationId());

        System.out.println("  AI 回答: " + resp.getAnswer());
        return resp.getAnswer();
    }

    // ==================== 工具方法 ====================

    private List<String> uploadImages(List<SyncMsgResponse.MsgItem> images, String userId) {
        List<String> fileIds = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            String mediaId = images.get(i).getImage().getMedia_id();
            byte[] bytes = wecomApiClient.downloadMedia(mediaId);
            if (bytes != null) {
                String fileId = difyApiClient.uploadFile(bytes, "wecom_image_" + (i + 1) + ".jpg", "image/jpeg", userId);
                if (fileId != null) fileIds.add(fileId);
            }
        }
        return fileIds;
    }

    private List<String> uploadVoices(List<SyncMsgResponse.MsgItem> voices, String userId) {
        List<String> fileIds = new ArrayList<>();
        for (int i = 0; i < voices.size(); i++) {
            String mediaId = voices.get(i).getVoice().getMedia_id();
            byte[] bytes = wecomApiClient.downloadMedia(mediaId);
            if (bytes != null) {
                String fileId = difyApiClient.uploadFile(bytes, "wecom_voice_" + (i + 1) + ".amr", "audio/amr", userId);
                if (fileId != null) fileIds.add(fileId);
            }
        }
        return fileIds;
    }

    private List<SyncMsgResponse.MsgItem> filterByType(List<SyncMsgResponse.MsgItem> messages, String msgtype) {
        return messages.stream().filter(m -> msgtype.equals(m.getMsgtype())).toList();
    }

    private String combineTexts(List<SyncMsgResponse.MsgItem> texts) {
        return texts.stream()
                .filter(m -> m.getText() != null && m.getText().getContent() != null)
                .map(m -> m.getText().getContent())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private void ensureBotService(String openKfid, String userId) {
        try {
            var sessionState = sessionStateService.getSessionState(openKfid, userId);
            if (sessionState.getService_state() != WecomConstants.ServiceState.BOT_SERVICE) {
                sessionStateService.transferToBotService(openKfid, userId);
            }
        } catch (Exception e) {
            System.err.println("  会话状态管理异常: " + e.getMessage());
        }
    }

    private void cacheConversationId(String userId, String conversationId) {
        if (conversationId != null && !conversationId.isEmpty()) {
            difyConversationService.saveConversationId(userId, conversationId);
        }
    }

    /**
     * 获取用户昵称（带全局缓存）
     *
     * <p>优先从缓存读取，缓存未命中时请求企业微信接口获取并缓存。</p>
     */
    private String getNickname(String externalUserid) {
        return nicknameCache.computeIfAbsent(externalUserid, id -> {
            try {
                return wecomApiClient.getCustomerNickname(id);
            } catch (Exception e) {
                System.out.println("  获取用户昵称异常: " + e.getMessage());
                return null;
            }
        });
    }
}
