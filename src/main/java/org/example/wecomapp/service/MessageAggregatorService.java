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

/**
 * 消息聚合与防抖服务
 *
 * <p>按 external_userid 维护独立的消息队列，解决：</p>
 * <ol>
 *   <li>多用户消息混杂 — sync_msg 返回客服帐号下所有用户的消息，需按用户分组</li>
 *   <li>增量过滤 — 按每用户的 lastMsgId 过滤已处理消息</li>
 *   <li>全局 cursor — 增量拉取新消息，避免重复获取</li>
 *   <li>防抖 — 每用户独立的 3 秒防抖窗口，聚合快速连发消息</li>
 *   <li>图文混排 — 同一用户发送的图片+文字合并处理</li>
 * </ol>
 *
 * @author dixonyen
 */
@Service
public class MessageAggregatorService {

    private static final long DEBOUNCE_MS = 3000;

    private final WecomApiClient wecomApiClient;
    private final DifyApiClient difyApiClient;
    private final DifyConversationService difyConversationService;
    private final ProcessedMessageTracker messageTracker;
    private final SessionStateService sessionStateService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    /**
     * 按 openKfid 维护的全局状态
     */
    private final ConcurrentHashMap<String, OpenKfidState> openKfidStates = new ConcurrentHashMap<>();

    /**
     * 客服帐号全局状态
     */
    static class OpenKfidState {
        volatile String token;
        volatile String globalCursor;
        final ConcurrentHashMap<String, UserState> userStates = new ConcurrentHashMap<>();
    }

    /**
     * 单个用户的消息队列状态
     */
    static class UserState {
        volatile String lastProcessedMsgId;
        final List<SyncMsgResponse.MsgItem> bufferedMessages = new CopyOnWriteArrayList<>();
        ScheduledFuture<?> scheduledTask;
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
     * 入口方法 — MessageService 调用
     *
     * @param token    回调 token
     * @param openKfid 客服帐号 ID
     */
    public void onCallback(String token, String openKfid) {
        System.out.println("\n========== [Aggregator] onCallback ==========");
        System.out.println("  openKfid: " + openKfid);

        // 获取或创建 openKfid 全局状态
        OpenKfidState state = openKfidStates.computeIfAbsent(openKfid, k -> new OpenKfidState());
        state.token = token;

        // 增量拉取消息（使用全局 cursor）
        List<SyncMsgResponse.MsgItem> allNewMessages = fetchIncrementalMessages(token, openKfid, state.globalCursor);
        System.out.println("  增量拉取: " + allNewMessages.size() + " 条消息");

        if (allNewMessages.isEmpty()) {
            System.out.println("  无新消息，跳过");
            return;
        }

        // 按 external_userid 分组（只保留用户消息 origin=3）
        Map<String, List<SyncMsgResponse.MsgItem>> grouped = groupByUser(allNewMessages);
        System.out.println("  涉及用户数: " + grouped.size());

        // 每个用户独立处理
        for (var entry : grouped.entrySet()) {
            String userId = entry.getKey();
            List<SyncMsgResponse.MsgItem> userMessages = entry.getValue();
            processPerUserMessages(state, userId, userMessages, openKfid);
        }

        System.out.println("=============================================");
    }

    /**
     * 增量拉取消息
     *
     * <p>使用全局 cursor 从上次拉取位置继续，分页获取所有新消息后更新 cursor。</p>
     */
    private List<SyncMsgResponse.MsgItem> fetchIncrementalMessages(String token, String openKfid, String cursor) {
        List<SyncMsgResponse.MsgItem> allMessages = new ArrayList<>();
        String currentCursor = cursor;

        do {
            SyncMsgResponse syncResult = wecomApiClient.syncMsg(token, openKfid, currentCursor, 1000);
            if (syncResult.getMsg_list() != null) {
                allMessages.addAll(syncResult.getMsg_list());
            }
            if (syncResult.getHas_more() != null && syncResult.getHas_more() == 1) {
                currentCursor = syncResult.getNext_cursor();
            } else {
                break;
            }
        } while (currentCursor != null && !currentCursor.isEmpty());

        // 更新全局 cursor
        if (currentCursor != null && !currentCursor.isEmpty()) {
            openKfidStates.get(openKfid).globalCursor = currentCursor;
        }

        return allMessages;
    }

    /**
     * 按 external_userid 分组，只保留用户消息（origin=3）
     */
    private Map<String, List<SyncMsgResponse.MsgItem>> groupByUser(List<SyncMsgResponse.MsgItem> messages) {
        return messages.stream()
                .filter(m -> m.getOrigin() != null && m.getOrigin() == WecomConstants.MsgOrigin.WECHAT_USER)
                .filter(m -> m.getExternal_userid() != null)
                .collect(java.util.stream.Collectors.groupingBy(SyncMsgResponse.MsgItem::getExternal_userid));
    }

    /**
     * 处理单个用户的消息
     *
     * <p>按 lastMsgId 过滤增量 → 加入缓冲区 → 防抖调度</p>
     */
    private void processPerUserMessages(OpenKfidState state, String userId,
                                        List<SyncMsgResponse.MsgItem> messages, String openKfid) {
        // 获取或创建用户状态
        UserState userState = state.userStates.computeIfAbsent(userId, k -> new UserState());

        // 按 lastMsgId 过滤增量消息
        List<SyncMsgResponse.MsgItem> incremental = filterByLastMsgId(messages, userState.lastProcessedMsgId);
        System.out.println("  用户 " + userId + ": 增量消息 " + incremental.size() + " 条");

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

        // 取消旧定时器，重新调度（防抖）
        if (userState.scheduledTask != null && !userState.scheduledTask.isDone()) {
            userState.scheduledTask.cancel(false);
        }

        final String uid = userId;
        final String okfid = openKfid;
        userState.scheduledTask = scheduler.schedule(
                () -> processUserAggregatedMessages(state, uid, okfid),
                DEBOUNCE_MS, TimeUnit.MILLISECONDS
        );
        System.out.println("  用户 " + userId + ": 已调度 " + DEBOUNCE_MS + "ms 后处理");
    }

    /**
     * 按 lastMsgId 过滤出增量消息（msgid > lastMsgId）
     */
    private List<SyncMsgResponse.MsgItem> filterByLastMsgId(List<SyncMsgResponse.MsgItem> messages, String lastMsgId) {
        if (lastMsgId == null || lastMsgId.isEmpty()) {
            return new ArrayList<>(messages);
        }
        return messages.stream()
                .filter(m -> m.getMsgid() != null && m.getMsgid().compareTo(lastMsgId) > 0)
                .toList();
    }

    /**
     * 防抖定时器触发 — 处理单个用户的聚合消息
     */
    private void processUserAggregatedMessages(OpenKfidState state, String userId, String openKfid) {
        UserState userState = state.userStates.get(userId);
        if (userState == null || userState.bufferedMessages.isEmpty()) {
            return;
        }

        List<SyncMsgResponse.MsgItem> messages = new ArrayList<>(userState.bufferedMessages);
        userState.bufferedMessages.clear();

        System.out.println("\n========== [Aggregator] 处理用户消息 ==========");
        System.out.println("  用户: " + userId);
        System.out.println("  消息数: " + messages.size());

        try {
            // 会话状态管理
            ensureBotService(openKfid, userId);

            // 记录本次最大 msgid
            String maxMsgId = messages.stream()
                    .map(SyncMsgResponse.MsgItem::getMsgid)
                    .filter(id -> id != null)
                    .max(String::compareTo)
                    .orElse(null);

            // 按类型分组处理
            List<SyncMsgResponse.MsgItem> images = filterByType(messages, "image");
            List<SyncMsgResponse.MsgItem> texts = filterByType(messages, "text");
            List<SyncMsgResponse.MsgItem> voices = filterByType(messages, "voice");

            Reply reply;

            if (!images.isEmpty() && !texts.isEmpty()) {
                reply = handleImageWithText(images, texts, userId);
            } else if (!images.isEmpty()) {
                reply = handleImageOnly(images, userId);
            } else if (!texts.isEmpty()) {
                reply = handleTextOnly(texts, userId);
            } else if (!voices.isEmpty()) {
                reply = handleVoiceOnly(voices, userId);
            } else {
                reply = handleUnsupported(messages);
            }

            // 发送回复
            if (reply != null) {
                SendMsgResponse sendResult = wecomApiClient.sendMsg(userId, openKfid, reply.msgtype(), reply.content());
                System.out.println("  发送结果: " + sendResult.getErrcode() + " - " + sendResult.getErrmsg());
            }

            // 更新 lastMsgId，标记已处理
            if (maxMsgId != null) {
                userState.lastProcessedMsgId = maxMsgId;
            }
            List<String> msgIds = messages.stream().map(SyncMsgResponse.MsgItem::getMsgid).toList();
            messageTracker.markAllProcessed(userId, msgIds);
            System.out.println("  已标记 " + msgIds.size() + " 条，lastMsgId=" + maxMsgId);

        } catch (Exception e) {
            System.err.println("  处理用户消息异常: " + e.getMessage());
            e.printStackTrace(System.err);
        }

        System.out.println("================================================\n");
    }

    // ==================== 消息处理 ====================

    private Reply handleImageWithText(List<SyncMsgResponse.MsgItem> images,
                                      List<SyncMsgResponse.MsgItem> texts, String userId) {
        System.out.println("  模式: 图片+文字");
        List<String> fileIds = uploadImages(images, userId);
        String query = combineTexts(texts);
        System.out.println("  query: " + query);

        String convId = difyConversationService.getConversationId(userId);
        DifyChatResponse resp = difyApiClient.chatMessage(query, userId, convId, fileIds.isEmpty() ? null : fileIds);
        cacheConversationId(userId, resp.getConversationId());
        return Reply.text(resp.getAnswer());
    }

    private Reply handleImageOnly(List<SyncMsgResponse.MsgItem> images, String userId) {
        System.out.println("  模式: 仅图片");
        List<String> fileIds = uploadImages(images, userId);

        String convId = difyConversationService.getConversationId(userId);
        DifyChatResponse resp = difyApiClient.chatMessage("请分析图片内容", userId, convId, fileIds.isEmpty() ? null : fileIds);
        cacheConversationId(userId, resp.getConversationId());
        return Reply.text(resp.getAnswer());
    }

    private Reply handleTextOnly(List<SyncMsgResponse.MsgItem> texts, String userId) {
        System.out.println("  模式: 仅文字");
        String query = combineTexts(texts);
        System.out.println("  query: " + query);

        String convId = difyConversationService.getConversationId(userId);
        DifyChatResponse resp = difyApiClient.chatMessage(query, userId, convId, null);
        cacheConversationId(userId, resp.getConversationId());
        return Reply.text(resp.getAnswer());
    }

    private Reply handleVoiceOnly(List<SyncMsgResponse.MsgItem> voices, String userId) {
        System.out.println("  模式: 仅语音");
        List<String> fileIds = uploadVoices(voices, userId);

        String convId = difyConversationService.getConversationId(userId);
        DifyChatResponse resp = difyApiClient.chatMessage("请分析语音内容", userId, convId, fileIds.isEmpty() ? null : fileIds);
        cacheConversationId(userId, resp.getConversationId());
        return Reply.text(resp.getAnswer());
    }

    private Reply handleUnsupported(List<SyncMsgResponse.MsgItem> messages) {
        String firstType = messages.get(0).getMsgtype();
        return Reply.text("抱歉，我暂时无法处理 " + firstType + " 类型的消息，请发送文字描述。");
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
}
