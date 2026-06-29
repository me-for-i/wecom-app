package org.example.wecomapp.client;

import org.example.wecomapp.config.WecomProperties;
import org.example.wecomapp.dto.DifyChatRequest;
import org.example.wecomapp.dto.DifyChatResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;

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
     * @param fileIds        已上传到 Dify 的文件 ID 列表（图片、音频等），可为 null
     * @return Dify 返回的 AI 回答
     */
    public DifyChatResponse chatMessage(String query, String user, String conversationId, List<String> fileIds) {
        System.out.println("\n========== [Dify API] chat-messages ==========");
        System.out.println("  query: " + query);
        System.out.println("  user: " + user);
        System.out.println("  conversation_id: " + (conversationId != null ? conversationId : "（新会话）"));

        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("user", user);
        body.put("response_mode", "streaming");
        body.put("inputs", new HashMap<>());

        // 携带 conversation_id 以继续之前的对话
        if (conversationId != null && !conversationId.isEmpty()) {
            body.put("conversation_id", conversationId);
        }

        // 附加已上传的文件（图片、音频等）
        if (fileIds != null && !fileIds.isEmpty()) {
            JSONArray filesArray = new JSONArray();
            for (String fileId : fileIds) {
                JSONObject fileObj = new JSONObject();
                fileObj.put("type", "image");
                fileObj.put("transfer_method", "local_file");
                fileObj.put("upload_file_id", fileId);
                filesArray.put(fileObj);
            }
            body.put("files", filesArray);
            System.out.println("  files: " + filesArray.length() + " 个文件");
        }

        String url = properties.getDifyApiServer() + "/chat-messages";
        String sseBody = restClient.post()
                .uri(url)
                .header("Authorization", "Bearer " + properties.getDifyApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);

        System.out.println("---------- Dify 响应 (streaming) ----------");

        // 解析 SSE 事件流：拼接所有 message/agent_message 的 answer，从 message_end 取元数据
        StringBuilder answerBuilder = new StringBuilder();
        String messageId = "";
        String respConversationId = "";
        long createdAt = 0;

        try (BufferedReader reader = new BufferedReader(new StringReader(sseBody))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6).trim();
                if (data.isEmpty()) continue;

                JSONObject event = new JSONObject(data);
                String eventType = event.optString("event", "");

                if ("message".equals(eventType) || "agent_message".equals(eventType)) {
                    answerBuilder.append(event.optString("answer", ""));
                }
                if ("message_end".equals(eventType)) {
                    messageId = event.optString("message_id", "");
                    respConversationId = event.optString("conversation_id", "");
                    createdAt = event.optLong("created_at", 0);
                }
            }
        } catch (Exception e) {
            System.out.println("  解析 SSE 流异常: " + e.getMessage());
        }

        DifyChatResponse result = new DifyChatResponse();
        result.setMessageId(messageId);
        result.setConversationId(respConversationId);

        String answer = answerBuilder.toString();
        answer = removeThinkTags(answer);
        answer = removeTrailingSlashes(answer);
        result.setAnswer(answer);
        result.setCreatedAt(createdAt);

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
     * 上传文件到 Dify
     *
     * <p>上传图片、音频等媒体文件到 Dify，返回文件 ID 用于后续在 chat-messages 中引用。</p>
     *
     * <p>接口地址：POST {difyApiServer}/files/upload</p>
     *
     * <p>上传后需要在 chat-messages 的 {@code files} 参数中引用返回的 file ID，
     * Dify 会将文件内容作为上下文传递给 AI 模型。</p>
     *
     * @param fileBytes  文件二进制数据
     * @param fileName   文件名（用于 Dify 端展示）
     * @param mimeType   文件的 MIME 类型（如 image/jpeg, audio/amr）
     * @param user       用户标识（external_userid）
     * @return 上传成功后的文件 ID（upload_file_id），失败返回 null
     * @see <a href="https://docs.dify.ai/guides/application-developing/developing-with-apis">Dify API 文档</a>
     */
    public String uploadFile(byte[] fileBytes, String fileName, String mimeType, String user) {
        System.out.println("\n========== [Dify API] files/upload ==========");
        System.out.println("  fileName: " + fileName);
        System.out.println("  mimeType: " + mimeType);
        System.out.println("  fileSize: " + fileBytes.length + " bytes");

        try {
            String url = properties.getDifyApiServer() + "/files/upload";

            // 使用 MultiValueMap 构造 multipart/form-data 请求
            // byte[] 必须包装为 ByteArrayResource 并设置文件名，否则不会被序列化为 file part
            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };

            MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
            multipartBody.add("file", fileResource);
            multipartBody.add("user", user);
            multipartBody.add("type", mimeType.startsWith("image") ? "image" : "audio");

            String response = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + properties.getDifyApiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody)
                    .retrieve()
                    .body(String.class);

            JSONObject json = new JSONObject(response);
            String fileId = json.optString("id", "");
            if (!fileId.isEmpty()) {
                System.out.println("  上传成功，file ID: " + fileId);
                return fileId;
            } else {
                System.out.println("  上传失败，响应: " + response);
                return null;
            }
        } catch (Exception e) {
            System.err.println("  上传文件到 Dify 异常: " + e.getMessage());
            e.printStackTrace(System.err);
            return null;
        }
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
