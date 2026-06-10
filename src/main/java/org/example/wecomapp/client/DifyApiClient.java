package org.example.wecomapp.client;

import org.example.wecomapp.config.WecomProperties;
import org.example.wecomapp.dto.DifyChatRequest;
import org.example.wecomapp.dto.DifyChatResponse;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;

/**
 * Dify API 客户端
 *
 * <p>封装 Dify 平台聊天接口的调用</p>
 *
 * @author dixonyen
 * @see <a href="https://docs.dify.ai/guides/application-developing/developing-with-apis">Dify API 文档</a>
 */
@Component
public class DifyApiClient {

    private final RestClient restClient;
    private final WecomProperties properties;

    /**
     * 构造函数
     *
     * @param restClient Spring RestClient 实例
     * @param properties 配置属性
     */
    public DifyApiClient(RestClient restClient, WecomProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * 发送聊天消息到 Dify
     *
     * <p>调用 Dify 的 chat-messages 接口，发送用户问题并获取 AI 回答</p>
     *
     * <p>接口地址：POST https://api.dify.ai/v1/chat-messages</p>
     *
     * @param query          用户发送的消息内容
     * @param user           用户标识（external_userid）
     * @param conversationId 会话ID，传入时基于之前的聊天记录继续对话，为空时开启新会话
     * @return Dify 返回的 AI 回答
     */
    public DifyChatResponse chatMessage(String query, String user, String conversationId) {
        System.out.println("\n========== [Dify API] chat-messages ==========");
        System.out.println("  query: " + query);
        System.out.println("  user: " + user);
        System.out.println("  conversation_id: " + (conversationId != null ? conversationId : "（新会话）"));

        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("user", user);
        body.put("response_mode", "blocking");
        body.put("inputs", new HashMap<>());

        // 携带 conversation_id 以继续之前的对话
        if (conversationId != null && !conversationId.isEmpty()) {
            body.put("conversation_id", conversationId);
        }

        String url = properties.getDifyApiServer() + "/chat-messages";
        String response = restClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + properties.getDifyApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);

        System.out.println("---------- Dify 响应 ----------");
        System.out.println("  body: " + response);

        JSONObject json = new JSONObject(response);
        DifyChatResponse result = new DifyChatResponse();
        result.setMessageId(json.optString("message_id", ""));
        result.setConversationId(json.optString("conversation_id", ""));

        // 提取 answer 并过滤掉 <think>...</think> 部分，去除结尾多余的 /
        String answer = json.optString("answer", "");
        answer = removeThinkTags(answer);
        answer = removeTrailingSlashes(answer);
        result.setAnswer(answer);

        result.setCreatedAt(json.optLong("created_at", 0));

        System.out.println("  message_id: " + result.getMessageId());
        System.out.println("  conversation_id: " + result.getConversationId());

        return result;
    }

    /**
     * 移除 Dify 返回内容中的 <think>...</think> 标签
     *
     * @param answer 原始回答内容
     * @return 移除 think 标签后的正文
     */
    private String removeThinkTags(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer;
        }
        // 移除 <think>...</think> 及其内容
        return answer.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    /**
     * 去除回答结尾多余的 / 字符
     *
     * <p>Dify 返回的 answer 有时会在结尾附加不定数量的 /，在发送给用户前需要清理掉。</p>
     *
     * @param answer 原始回答内容
     * @return 去除结尾 / 后的正文
     */
    private String removeTrailingSlashes(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer;
        }
        // 去除结尾连续出现的 / 及其前后的空白
        return answer.replaceAll("[\\s/]+$", "").trim();
    }
}
