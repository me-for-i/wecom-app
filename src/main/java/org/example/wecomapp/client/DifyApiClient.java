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
     * @param query 用户发送的消息内容
     * @param user 用户标识（external_userid）
     * @return Dify 返回的 AI 回答
     */
    public DifyChatResponse chatMessage(String query, String user) {
        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("user", user);
        body.put("response_mode", "blocking");
        body.put("inputs", new HashMap<>());

        String url = properties.getDifyApiServer() + "/chat-messages";
        String response = restClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + properties.getDifyApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);

        System.out.println("========== Dify API 响应 ==========");
        System.out.println(response);
        System.out.println("====================================");

        JSONObject json = new JSONObject(response);
        DifyChatResponse result = new DifyChatResponse();
        result.setMessageId(json.optString("message_id", ""));
        result.setConversationId(json.optString("conversation_id", ""));

        // 提取 answer 并过滤掉 <think>...</think> 部分
        String answer = json.optString("answer", "");
        answer = removeThinkTags(answer);
        result.setAnswer(answer);

        result.setCreatedAt(json.optLong("created_at", 0));

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
}
