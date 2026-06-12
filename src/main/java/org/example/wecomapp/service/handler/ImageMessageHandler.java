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
 * 图片消息处理器
 *
 * <p>将用户发送的图片下载后上传到 Dify，让 AI 结合图片内容生成回答。</p>
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>从消息中提取图片 media_id</li>
 *   <li>调用企业微信 media/get 接口下载图片文件</li>
 *   <li>将图片上传到 Dify（files/upload）</li>
 *   <li>调用 Dify chat-messages 接口，附带图片文件 ID 获取 AI 回答</li>
 *   <li>将 AI 回答作为文本消息返回给用户</li>
 * </ol>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/90266">企业微信媒体文件接口</a>
 * @see <a href="https://docs.dify.ai/guides/application-developing/developing-with-apis">Dify API 文档</a>
 */
@Component
public class ImageMessageHandler implements MessageHandler {

    private final WecomApiClient wecomApiClient;
    private final DifyApiClient difyApiClient;
    private final DifyConversationService difyConversationService;

    public ImageMessageHandler(WecomApiClient wecomApiClient,
                               DifyApiClient difyApiClient,
                               DifyConversationService difyConversationService) {
        this.wecomApiClient = wecomApiClient;
        this.difyApiClient = difyApiClient;
        this.difyConversationService = difyConversationService;
    }

    @Override
    public String getMsgType() {
        return WecomConstants.MsgType.IMAGE;
    }

    @Override
    public Reply buildReplyContent(SyncMsgResponse.MsgItem userMsg, String openKfid) {
        if (userMsg.getImage() == null || userMsg.getImage().getMedia_id() == null) {
            System.out.println("[ImageMessageHandler] 图片内容为空，跳过处理");
            return null;
        }

        String externalUserid = userMsg.getExternal_userid();
        String mediaId = userMsg.getImage().getMedia_id();

        System.out.println("---------- [图片] 处理图片消息 ----------");
        System.out.println("media_id: " + mediaId);

        // ==================== 步骤 1: 从企业微信下载图片 ====================
        System.out.println("--- 步骤 1: 从企微下载图片 ---");
        byte[] imageBytes = wecomApiClient.downloadMedia(mediaId);
        if (imageBytes == null) {
            System.out.println("[ImageMessageHandler] 图片下载失败，跳过回复");
            return buildForwardReply(mediaId);
        }

        // ==================== 步骤 2: 上传图片到 Dify ====================
        System.out.println("--- 步骤 2: 上传图片到 Dify ---");
        String difyFileId = difyApiClient.uploadFile(imageBytes, "wecom_image.jpg", "image/jpeg", externalUserid);
        if (difyFileId == null) {
            System.out.println("[ImageMessageHandler] 图片上传 Dify 失败，原样转发图片");
            return buildForwardReply(mediaId);
        }

        // ==================== 步骤 3: 调用 Dify AI 分析图片 ====================
        System.out.println("--- 步骤 3: 调用 Dify AI 分析图片 ---");
        String conversationId = difyConversationService.getConversationId(externalUserid);
        System.out.println("Dify 会话 ID: " + (conversationId != null ? conversationId : "新会话"));

        // query 中提示 AI 这是一张图片，让模型结合图片上下文回复
        DifyChatResponse difyResponse = difyApiClient.chatMessage(
                "用户发送了一张图片，请根据图片内容进行回复",
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
     * 构造原样转发图片的回复
     *
     * @param mediaId 图片媒体 ID
     * @return Reply 实例，msgtype 为 "image"
     */
    private Reply buildForwardReply(String mediaId) {
        JSONObject content = new JSONObject();
        content.put("media_id", mediaId);
        return new Reply(WecomConstants.MsgType.IMAGE, content);
    }
}
