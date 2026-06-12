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
import java.util.concurrent.*;

/**
 * 消息聚合与防抖服务
 *
 * <p>解决三个核心问题：</p>
 * <ol>
 *   <li>竞态条件 — 多个 @Async 回调并发处理同一批消息导致重复回复</li>
 *   <li>只处理最后一条 — 前面的消息被丢弃</li>
 *   <li>图文脱节 — 图片和后续文字问句无法关联</li>
 * </ol>
 *
 * <p>核心机制：防抖 + 消息聚合 + 已处理追踪</p>
 * <ul>
 *   <li>每次回调触发时拉取新消息放入缓冲区，启动/重置 3 秒定时器</li>
 *   <li>定时器触发后分析缓冲区内所有消息的组合，一次性处理</li>
 *   <li>图片+文字 → 上传图片，文字作为 query，附带图片文件 ID</li>
 * </ul>
 *
 * @author dixonyen
 */
@Service
public class MessageAggregatorService {

    private static final long DEBOUNCE_MS = 3000; // 防抖窗口 3 秒

    private final WecomApiClient wecomApiClient;
    private final DifyApiClient difyApiClient;
    private final DifyConversationService difyConversationService;
    private final ProcessedMessageTracker messageTracker;
    private final SessionStateService sessionStateService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ConcurrentHashMap<String, PendingConversation> pending = new ConcurrentHashMap<>();

    /**
     * 待处理的会话上下文
     */
    static class PendingConversation {
        String token;
        String openKfid;
        String externalUserid;
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
     * 入口方法 — MessageService 调用此方法
     *
     * <p>流程：</p>
     * <ol>
     *   <li>sync_msg 拉取消息</li>
     *   <li>过滤出未处理的用户消息</li>
     *   <li>加入缓冲区</li>
     *   <li>取消旧定时器，调度新的 3 秒定时器</li>
     * </ol>
     *
     * @param token    回调 token（用于 sync_msg）
     * @param openKfid 客服帐号 ID
     */
    public void onCallback(String token, String openKfid) {
        System.out.println("\n========== [MessageAggregator] onCallback ==========");
        System.out.println("  openKfid: " + openKfid);

        // 拉取消息
        List<SyncMsgResponse.MsgItem> allMessages = fetchAllMessages(token, openKfid);
        if (allMessages.isEmpty()) {
            System.out.println("  无消息，跳过");
            return;
        }

        // 取第一条用户消息获取 externalUserid
        String externalUserid = null;
        for (SyncMsgResponse.MsgItem msg : allMessages) {
            if (msg.getOrigin() != null && msg.getOrigin() == 3 && msg.getExternal_userid() != null) {
                externalUserid = msg.getExternal_userid();
                break;
            }
        }
        if (externalUserid == null) {
            System.out.println("  未找到用户消息，跳过");
            return;
        }

        // final 引用供 lambda 使用
        final String userId = externalUserid;

        // 过滤出未处理的用户消息
        List<SyncMsgResponse.MsgItem> newMessages = messageTracker.filterNewMessages(userId, allMessages);
        System.out.println("  新消息数: " + newMessages.size());

        if (newMessages.isEmpty()) {
            System.out.println("  无新消息，跳过");
            return;
        }

        // 获取或创建 PendingConversation
        PendingConversation conv = pending.compute(userId, (key, existing) -> {
            if (existing == null) {
                existing = new PendingConversation();
                existing.openKfid = openKfid;
                existing.externalUserid = userId;
            }
            // 始终更新 token 为最新的
            existing.token = token;
            return existing;
        });

        // 将新消息加入缓冲区（避免重复添加）
        for (SyncMsgResponse.MsgItem msg : newMessages) {
            boolean exists = conv.bufferedMessages.stream()
                    .anyMatch(m -> m.getMsgid().equals(msg.getMsgid()));
            if (!exists) {
                conv.bufferedMessages.add(msg);
            }
        }

        System.out.println("  缓冲区消息数: " + conv.bufferedMessages.size());

        // 取消旧定时器，调度新的（防抖）
        if (conv.scheduledTask != null && !conv.scheduledTask.isDone()) {
            conv.scheduledTask.cancel(false);
            System.out.println("  已取消旧定时器，重新调度");
        }

        conv.scheduledTask = scheduler.schedule(
                () -> processAggregatedMessages(userId),
                DEBOUNCE_MS, TimeUnit.MILLISECONDS
        );
        System.out.println("  已调度 " + DEBOUNCE_MS + "ms 后处理");
        System.out.println("=====================================================");
    }

    /**
     * 拉取所有消息（分页）
     */
    private List<SyncMsgResponse.MsgItem> fetchAllMessages(String token, String openKfid) {
        List<SyncMsgResponse.MsgItem> allMessages = new ArrayList<>();
        String cursor = null;

        do {
            SyncMsgResponse syncResult = wecomApiClient.syncMsg(token, openKfid, cursor, 1000);
            if (syncResult.getMsg_list() != null) {
                allMessages.addAll(syncResult.getMsg_list());
            }
            if (syncResult.getHas_more() != null && syncResult.getHas_more() == 1) {
                cursor = syncResult.getNext_cursor();
            } else {
                break;
            }
        } while (cursor != null && !cursor.isEmpty());

        return allMessages;
    }

    /**
     * 定时器触发 — 处理聚合消息
     */
    private void processAggregatedMessages(String externalUserid) {
        PendingConversation conv = pending.remove(externalUserid);
        if (conv == null || conv.bufferedMessages.isEmpty()) {
            return;
        }

        List<SyncMsgResponse.MsgItem> messages = new ArrayList<>(conv.bufferedMessages);
        String openKfid = conv.openKfid;

        System.out.println("\n========== [MessageAggregator] 处理聚合消息 ==========");
        System.out.println("  externalUserid: " + externalUserid);
        System.out.println("  消息数: " + messages.size());

        try {
            // 会话状态管理
            ensureBotService(openKfid, externalUserid);

            // 按类型分组
            List<SyncMsgResponse.MsgItem> images = filterByType(messages, "image");
            List<SyncMsgResponse.MsgItem> texts = filterByType(messages, "text");
            List<SyncMsgResponse.MsgItem> voices = filterByType(messages, "voice");

            Reply reply;

            if (!images.isEmpty() && !texts.isEmpty()) {
                // 图片 + 文字 → 上传图片，文字作为 query
                reply = handleImageWithText(images, texts, externalUserid, openKfid);
            } else if (!images.isEmpty()) {
                // 只有图片 → 上传图片，默认 query
                reply = handleImageOnly(images, externalUserid, openKfid);
            } else if (!texts.isEmpty()) {
                // 只有文字 → 拼接为 query
                reply = handleTextOnly(texts, externalUserid, openKfid);
            } else if (!voices.isEmpty()) {
                // 只有语音 → 上传语音，默认 query
                reply = handleVoiceOnly(voices, externalUserid, openKfid);
            } else {
                // 不支持的类型
                reply = handleUnsupported(messages, openKfid);
            }

            // 发送回复
            if (reply != null) {
                SendMsgResponse sendResult = wecomApiClient.sendMsg(externalUserid, openKfid, reply.msgtype(), reply.content());
                System.out.println("  发送结果: " + sendResult.getErrcode() + " - " + sendResult.getErrmsg());
            }

            // 标记所有消息为已处理
            List<String> msgIds = messages.stream().map(SyncMsgResponse.MsgItem::getMsgid).toList();
            messageTracker.markAllProcessed(externalUserid, msgIds);
            System.out.println("  已标记 " + msgIds.size() + " 条消息为已处理");

        } catch (Exception e) {
            System.err.println("  处理聚合消息异常: " + e.getMessage());
            e.printStackTrace(System.err);
        }

        System.out.println("========================================================\n");
    }

    /**
     * 图片 + 文字 → 上传图片到 Dify，文字作为 query，附带图片文件 ID
     */
    private Reply handleImageWithText(List<SyncMsgResponse.MsgItem> images,
                                      List<SyncMsgResponse.MsgItem> texts,
                                      String externalUserid, String openKfid) {
        System.out.println("  模式: 图片+文字");

        // 上传所有图片到 Dify
        List<String> fileIds = uploadImages(images, externalUserid);
        System.out.println("  上传成功图片数: " + fileIds.size());

        // 拼接所有文字为 query
        String query = combineTexts(texts);
        System.out.println("  合并 query: " + query);

        // 调用 Dify
        String conversationId = difyConversationService.getConversationId(externalUserid);
        DifyChatResponse difyResponse = difyApiClient.chatMessage(query, externalUserid, conversationId,
                fileIds.isEmpty() ? null : fileIds);
        cacheConversationId(externalUserid, difyResponse.getConversationId());

        return Reply.text(difyResponse.getAnswer());
    }

    /**
     * 只有图片 → 上传图片，默认 query
     */
    private Reply handleImageOnly(List<SyncMsgResponse.MsgItem> images,
                                  String externalUserid, String openKfid) {
        System.out.println("  模式: 仅图片");

        List<String> fileIds = uploadImages(images, externalUserid);
        System.out.println("  上传成功图片数: " + fileIds.size());

        String prompt = "请分析图片内容";

        String conversationId = difyConversationService.getConversationId(externalUserid);
        DifyChatResponse difyResponse = difyApiClient.chatMessage(prompt, externalUserid, conversationId,
                fileIds.isEmpty() ? null : fileIds);
        cacheConversationId(externalUserid, difyResponse.getConversationId());

        return Reply.text(difyResponse.getAnswer());
    }

    /**
     * 只有文字 → 拼接为 query
     */
    private Reply handleTextOnly(List<SyncMsgResponse.MsgItem> texts,
                                 String externalUserid, String openKfid) {
        System.out.println("  模式: 仅文字");

        String query = combineTexts(texts);
        System.out.println("  合并 query: " + query);

        String conversationId = difyConversationService.getConversationId(externalUserid);
        DifyChatResponse difyResponse = difyApiClient.chatMessage(query, externalUserid, conversationId, null);
        cacheConversationId(externalUserid, difyResponse.getConversationId());

        return Reply.text(difyResponse.getAnswer());
    }

    /**
     * 只有语音 → 上传语音，默认 query
     */
    private Reply handleVoiceOnly(List<SyncMsgResponse.MsgItem> voices,
                                  String externalUserid, String openKfid) {
        System.out.println("  模式: 仅语音");

        List<String> fileIds = uploadVoices(voices, externalUserid);
        System.out.println("  上传成功语音数: " + fileIds.size());

        String prompt = "请分析语音内容";

        String conversationId = difyConversationService.getConversationId(externalUserid);
        DifyChatResponse difyResponse = difyApiClient.chatMessage(prompt, externalUserid, conversationId,
                fileIds.isEmpty() ? null : fileIds);
        cacheConversationId(externalUserid, difyResponse.getConversationId());

        return Reply.text(difyResponse.getAnswer());
    }

    /**
     * 不支持的消息类型 → 兜底回复
     */
    private Reply handleUnsupported(List<SyncMsgResponse.MsgItem> messages, String openKfid) {
        String firstType = messages.get(0).getMsgtype();
        System.out.println("  模式: 不支持的类型 " + firstType);

        return Reply.text("抱歉，我暂时无法处理 " + firstType + " 类型的消息，请发送文字描述。");
    }

    // ==================== 工具方法 ====================

    /**
     * 上传图片列表到 Dify，返回成功上传的文件 ID 列表
     */
    private List<String> uploadImages(List<SyncMsgResponse.MsgItem> images, String externalUserid) {
        List<String> fileIds = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            SyncMsgResponse.MsgItem img = images.get(i);
            String mediaId = img.getImage().getMedia_id();
            byte[] bytes = wecomApiClient.downloadMedia(mediaId);
            if (bytes != null) {
                String fileId = difyApiClient.uploadFile(bytes, "wecom_image_" + (i + 1) + ".jpg", "image/jpeg", externalUserid);
                if (fileId != null) {
                    fileIds.add(fileId);
                }
            }
        }
        return fileIds;
    }

    /**
     * 上传语音列表到 Dify，返回成功上传的文件 ID 列表
     */
    private List<String> uploadVoices(List<SyncMsgResponse.MsgItem> voices, String externalUserid) {
        List<String> fileIds = new ArrayList<>();
        for (int i = 0; i < voices.size(); i++) {
            SyncMsgResponse.MsgItem voice = voices.get(i);
            String mediaId = voice.getVoice().getMedia_id();
            byte[] bytes = wecomApiClient.downloadMedia(mediaId);
            if (bytes != null) {
                String fileId = difyApiClient.uploadFile(bytes, "wecom_voice_" + (i + 1) + ".amr", "audio/amr", externalUserid);
                if (fileId != null) {
                    fileIds.add(fileId);
                }
            }
        }
        return fileIds;
    }

    /**
     * 按消息类型过滤
     */
    private List<SyncMsgResponse.MsgItem> filterByType(List<SyncMsgResponse.MsgItem> messages, String msgtype) {
        return messages.stream()
                .filter(m -> msgtype.equals(m.getMsgtype()))
                .toList();
    }

    /**
     * 拼接多条文字消息为一个 query（按时间顺序，换行分隔）
     */
    private String combineTexts(List<SyncMsgResponse.MsgItem> texts) {
        return texts.stream()
                .filter(m -> m.getText() != null && m.getText().getContent() != null)
                .map(m -> m.getText().getContent())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    /**
     * 确保会话处于智能助手接待状态
     */
    private void ensureBotService(String openKfid, String externalUserid) {
        try {
            var sessionState = sessionStateService.getSessionState(openKfid, externalUserid);
            if (sessionState.getService_state() != WecomConstants.ServiceState.BOT_SERVICE) {
                sessionStateService.transferToBotService(openKfid, externalUserid);
                System.out.println("  已切换为智能助手接待");
            }
        } catch (Exception e) {
            System.err.println("  会话状态管理异常: " + e.getMessage());
        }
    }

    /**
     * 缓存 Dify 会话 ID
     */
    private void cacheConversationId(String externalUserid, String conversationId) {
        if (conversationId != null && !conversationId.isEmpty()) {
            difyConversationService.saveConversationId(externalUserid, conversationId);
        }
    }
}
