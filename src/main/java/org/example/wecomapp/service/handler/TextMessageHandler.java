package org.example.wecomapp.service.handler;

import org.example.wecomapp.client.DifyApiClient;
import org.example.wecomapp.constants.WecomConstants;
import org.example.wecomapp.dto.DifyChatResponse;
import org.example.wecomapp.dto.Reply;
import org.example.wecomapp.dto.SyncMsgResponse;
import org.example.wecomapp.service.DifyConversationService;
import org.springframework.stereotype.Component;

/**
 * 文本消息处理器
 *
 * <p>将用户发送的文本消息发送到 Dify AI，获取智能回答后返回。</p>
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>从消息中提取文本内容</li>
 *   <li>从缓存中获取该用户的 Dify 对话 ID（用于多轮对话续接）</li>
 *   <li>调用 Dify API 获取 AI 回答</li>
 *   <li>缓存新的对话 ID</li>
 *   <li>返回文本回复内容</li>
 * </ol>
 *
 * @author dixonyen
 * @see <a href="https://docs.dify.ai/guides/application-developing/developing-with-apis">Dify API 文档</a>
 */
@Component
public class TextMessageHandler implements MessageHandler {

    private final DifyApiClient difyApiClient;
    private final DifyConversationService difyConversationService;

    public TextMessageHandler(DifyApiClient difyApiClient,
                              DifyConversationService difyConversationService) {
        this.difyApiClient = difyApiClient;
        this.difyConversationService = difyConversationService;
    }

    @Override
    public String getMsgType() {
        return WecomConstants.MsgType.TEXT;
    }

    @Override
    public Reply buildReplyContent(SyncMsgResponse.MsgItem userMsg, String openKfid) {
        if (userMsg.getText() == null || userMsg.getText().getContent() == null) {
            System.out.println("[TextMessageHandler] 文本内容为空，跳过处理");
            return null;
        }

        String externalUserid = userMsg.getExternal_userid();
        String userQuery = userMsg.getText().getContent();

        System.out.println("---------- [文本] 调用 Dify AI ----------");
        System.out.println("用户消息: " + userQuery);

        // 获取该用户的对话 ID（续接多轮对话）
        String conversationId = difyConversationService.getConversationId(externalUserid);
        System.out.println("Dify 会话 ID: " + (conversationId != null ? conversationId : "新会话"));

        // 调用 Dify AI
        DifyChatResponse difyResponse = difyApiClient.chatMessage(userQuery, externalUserid, conversationId, null);
        String aiAnswer = difyResponse.getAnswer();
        System.out.println("AI 回答: " + aiAnswer);

        // 缓存新的对话 ID
        if (difyResponse.getConversationId() != null && !difyResponse.getConversationId().isEmpty()) {
            difyConversationService.saveConversationId(externalUserid, difyResponse.getConversationId());
            System.out.println("Dify 会话 ID 已缓存: " + difyResponse.getConversationId());
        }

        return Reply.text(aiAnswer);
    }
}
