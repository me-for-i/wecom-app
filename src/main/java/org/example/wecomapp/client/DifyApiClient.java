package org.example.wecomapp.client;

import org.example.wecomapp.config.WecomProperties;
import org.example.wecomapp.dto.DifyChatResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final RestClient difyRestClient;
    private final WecomProperties properties;

    /**
     * 构造函数
     *
     * @param restClient     通用 RestClient 实例（用于阻塞请求）
     * @param difyRestClient Dify 专用 RestClient 实例（流式 SSE 响应，超时更长）
     * @param properties     配置属性
     */
    public DifyApiClient(
            @Qualifier("restClient") RestClient restClient,
            @Qualifier("difyRestClient") RestClient difyRestClient,
            WecomProperties properties) {
        this.restClient = restClient;
        this.difyRestClient = difyRestClient;
        this.properties = properties;
    }

    /**
     * 发送聊天消息到 Dify（阻塞模式，默认）
     *
     * <p>调用 Dify 的 chat-messages 接口，等待 Dify 完整处理后一次性返回结果。</p>
     * <p>适用于生产环境，避免流式响应的 chunked 编码问题。</p>
     *
     * <p>接口地址：POST https://api.dify.ai/v1/chat-messages</p>
     *
     * @param query          用户发送的消息内容
     * @param user           用户标识（external_userid）
     * @param conversationId 会话ID，传入时基于之前的聊天记录继续对话，为空时开启新会话
     * @param fileIds        已上传到 Dify 的文件 ID 列表（图片、音频等），可为 null
     * @param nickname       用户昵称，传入 inputs 字段供 Dify 变量引用，可为 null
     * @return Dify 返回的 AI 回答
     */
    public DifyChatResponse chatMessage(String query, String user, String conversationId, List<String> fileIds, String nickname) {
        return doChatMessage(query, user, conversationId, fileIds, nickname, false);
    }

    /**
     * 发送聊天消息到 Dify（流式模式，调试用）
     *
     * <p>调用 Dify 的 chat-messages 接口，使用 SSE 流式读取响应。</p>
     * <p>适用于调试场景，可实时观察 Dify 返回的每个事件。</p>
     *
     * @param query          用户发送的消息内容
     * @param user           用户标识（external_userid）
     * @param conversationId 会话ID，传入时基于之前的聊天记录继续对话，为空时开启新会话
     * @param fileIds        已上传到 Dify 的文件 ID 列表（图片、音频等），可为 null
     * @param nickname       用户昵称，传入 inputs 字段供 Dify 变量引用，可为 null
     * @return Dify 返回的 AI 回答
     */
    public DifyChatResponse chatMessageStreaming(String query, String user, String conversationId, List<String> fileIds, String nickname) {
        return doChatMessage(query, user, conversationId, fileIds, nickname, true);
    }

    /**
     * 发送聊天消息到 Dify 的内部实现
     *
     * @param query          用户发送的消息内容
     * @param user           用户标识
     * @param conversationId 会话ID
     * @param fileIds        文件 ID 列表
     * @param nickname       用户昵称
     * @param streaming      true 为流式模式，false 为阻塞模式
     * @return Dify 返回的 AI 回答
     */
    private DifyChatResponse doChatMessage(String query, String user, String conversationId, List<String> fileIds, String nickname, boolean streaming) {
        String mode = streaming ? "streaming" : "blocking";
        System.out.println("\n========== [Dify API] chat-messages (" + mode + ") ==========");
        System.out.println("  query: " + query);
        System.out.println("  user: " + user);
        System.out.println("  conversation_id: " + (conversationId != null ? conversationId : "（新会话）"));

        JSONObject body = buildChatBody(query, user, conversationId, fileIds, nickname, streaming);
        String url = properties.getDifyApiServer() + "/chat-messages";

        return streaming
                ? doChatStreaming(url, body)
                : doChatBlocking(url, body);
    }

    /**
     * 构建 chat-messages 请求体
     */
    private JSONObject buildChatBody(String query, String user, String conversationId, List<String> fileIds, String nickname, boolean streaming) {
        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("user", user);
        body.put("response_mode", streaming ? "streaming" : "blocking");

        HashMap<String, Object> inputs = new HashMap<>();
        if (nickname != null && !nickname.isEmpty()) {
            inputs.put("nickname", nickname);
        }
        body.put("inputs", inputs);

        if (conversationId != null && !conversationId.isEmpty()) {
            body.put("conversation_id", conversationId);
        }

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

        return body;
    }

    /**
     * 阻塞模式：等待 Dify 完整返回 JSON 响应
     *
     * <p>Dify 处理完成后直接返回 JSON：</p>
     * <pre>
     * {
     *   "event": "message",
     *   "message_id": "...",
     *   "conversation_id": "...",
     *   "answer": "完整回复内容",
     *   "created_at": 1679586595
     * }
     * </pre>
     */
    private DifyChatResponse doChatBlocking(String url, JSONObject body) {
        String response;
        try {
            response = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + properties.getDifyApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            System.err.println("  [Dify] 阻塞请求异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("  [Dify] 原因: " + e.getCause().getClass().getSimpleName() + " - " + e.getCause().getMessage());
            }
            throw e;
        }

        System.out.println("---------- Dify 响应 (blocking) ----------");
        System.out.println("  原始响应: " + response);

        JSONObject json = new JSONObject(response);

        DifyChatResponse result = new DifyChatResponse();
        result.setMessageId(json.optString("message_id", ""));
        result.setConversationId(json.optString("conversation_id", ""));

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
     * 流式模式：读取 SSE 事件流并解析
     *
     * <p>SSE 事件格式：</p>
     * <pre>
     * data: {"event": "workflow_started", ...}
     * data: {"event": "node_started", "node_type": "llm", ...}
     * data: {"event": "message", "answer": "你"}
     * data: {"event": "message", "answer": "好"}
     * data: {"event": "message_end", "id": "...", "conversation_id": "..."}
     * </pre>
     */
    private DifyChatResponse doChatStreaming(String url, JSONObject body) {
        String sseBody;
        try {
            sseBody = difyRestClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + properties.getDifyApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .exchange((req, resp) -> {
                        StringBuilder sb = new StringBuilder();
                        int lineCount = 0;
                        try (BufferedReader reader = new BufferedReader(
                                new java.io.InputStreamReader(resp.getBody(), java.nio.charset.StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                                lineCount++;
                            }
                        } catch (java.io.IOException e) {
                            // Dify 通过 nginx 反向代理，LLM 长时间生成时代理可能主动关闭连接，
                            // 导致 chunked 编码不完整（缺 closing chunk）。
                            // 只要已收到有效 SSE 数据（data: 行），就视为正常结束，使用已积累的内容。
                            System.out.println("  [Dify] IOException: " + e.getMessage());
                            if (sb.toString().contains("data: ")) {
                                System.out.println("  [Dify] 连接被提前关闭，但已收到有效数据，忽略异常");
                            } else {
                                throw e;
                            }
                        }
                        String content = sb.toString();
                        System.out.println("  [Dify] SSE 流读取完成，共 " + lineCount + " 行，" + content.length() + " 字符");
                        System.out.println("  [Dify] ---- SSE 响应内容 ----");
                        System.out.println(content);
                        System.out.println("  [Dify] ---- SSE 响应内容结束 ----");
                        return content;
                    });
        } catch (Exception e) {
            System.err.println("  [Dify] 流式请求异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("  [Dify] 原因: " + e.getCause().getClass().getSimpleName() + " - " + e.getCause().getMessage());
            }
            throw e;
        }

        System.out.println("---------- Dify 响应 (streaming) ----------");

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
                    messageId = event.optString("id", "");
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
