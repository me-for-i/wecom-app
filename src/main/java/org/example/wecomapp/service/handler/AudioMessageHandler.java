package org.example.wecomapp.service.handler;

import org.example.wecomapp.client.DifyApiClient;
import org.example.wecomapp.client.WecomApiClient;
import org.example.wecomapp.constants.WecomConstants;
import org.example.wecomapp.dto.DifyChatResponse;
import org.example.wecomapp.dto.Reply;
import org.example.wecomapp.dto.SyncMsgResponse;
import org.example.wecomapp.service.DifyConversationService;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 语音消息处理器
 *
 * <p>将用户发送的语音下载后上传到 Dify，让 AI 结合语音内容生成回答。</p>
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>从消息中提取语音 media_id</li>
 *   <li>调用企业微信 media/get 接口下载语音文件（AMR 格式）</li>
 *   <li>将语音上传到 Dify（files/upload）</li>
 *   <li>调用 Dify chat-messages 接口，附带语音文件 ID 获取 AI 回答</li>
 *   <li>将 AI 回答作为文本消息返回给用户</li>
 * </ol>
 *
 * <p>注意：Dify 对语音文件的处理能力取决于所配置的模型是否支持音频输入。
 * 如果模型不支持，可降级为原样转发语音消息。</p>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/90266">企业微信媒体文件接口</a>
 * @see <a href="https://docs.dify.ai/guides/application-developing/developing-with-apis">Dify API 文档</a>
 */
@Component
public class AudioMessageHandler implements MessageHandler {

    private final WecomApiClient wecomApiClient;
    private final DifyApiClient difyApiClient;
    private final DifyConversationService difyConversationService;

    public AudioMessageHandler(WecomApiClient wecomApiClient,
                               DifyApiClient difyApiClient,
                               DifyConversationService difyConversationService) {
        this.wecomApiClient = wecomApiClient;
        this.difyApiClient = difyApiClient;
        this.difyConversationService = difyConversationService;
    }

    @Override
    public String getMsgType() {
        return WecomConstants.MsgType.VOICE;
    }

    @Override
    public Reply buildReplyContent(SyncMsgResponse.MsgItem userMsg, String openKfid) {
        if (userMsg.getVoice() == null || userMsg.getVoice().getMedia_id() == null) {
            System.out.println("[AudioMessageHandler] 语音内容为空，跳过处理");
            return null;
        }

        String externalUserid = userMsg.getExternal_userid();
        String mediaId = userMsg.getVoice().getMedia_id();

        System.out.println("---------- [语音] 处理语音消息 ----------");
        System.out.println("media_id: " + mediaId);

        // ==================== 步骤 1: 从企业微信下载语音 ====================
        System.out.println("--- 步骤 1: 从企微下载语音 ---");
        byte[] audioBytes = wecomApiClient.downloadMedia(mediaId);
        if (audioBytes == null) {
            System.out.println("[AudioMessageHandler] 语音下载失败，尝试原样转发");
            return buildForwardReply(mediaId);
        }

        // ==================== 步骤 2: 上传语音到 Dify ====================
        System.out.println("--- 步骤 2: 上传语音到 Dify ---");
        // 企业微信语音为 AMR 格式（Silk 编码），部分模型可能需要转为其他格式
        String difyFileId = difyApiClient.uploadFile(audioBytes, "wecom_voice.amr", "audio/amr", externalUserid);
        if (difyFileId == null) {
            System.out.println("[AudioMessageHandler] 语音上传 Dify 失败，原样转发语音");
            return buildForwardReply(mediaId);
        }

        // ==================== 步骤 3: 调用 Dify AI 处理语音 ====================
        System.out.println("--- 步骤 3: 调用 Dify AI 处理语音 ---");
        String conversationId = difyConversationService.getConversationId(externalUserid);
        System.out.println("Dify 会话 ID: " + (conversationId != null ? conversationId : "新会话"));

        DifyChatResponse difyResponse = difyApiClient.chatMessage(
                "用户发送了一段语音消息，请根据语音内容进行回复",
                externalUserid,
                conversationId,
                List.of(difyFileId)
        );
        String aiAnswer = difyResponse.getAnswer();
        System.out.println("AI 回答: " + aiAnswer);

        // 缓存新的对话 ID
        if (difyResponse.getConversationId() != null && !difyResponse.getConversationId().isEmpty()) {
            difyConversationService.saveConversationId(externalUserid, difyResponse.getConversationId());
            System.out.println("Dify 会话 ID 已缓存: " + difyResponse.getConversationId());
        }

        // Dify 回复文本，以 text 类型发送
        return Reply.text(aiAnswer);
    }

    /**
     * 构造原样转发语音的回复
     *
     * @param mediaId 语音媒体 ID
     * @return Reply 实例，msgtype 为 "voice"
     */
    private Reply buildForwardReply(String mediaId) {
        JSONObject content = new JSONObject();
        content.put("media_id", mediaId);
        return new Reply(WecomConstants.MsgType.VOICE, content);
    }
}
