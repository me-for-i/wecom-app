package org.example.wecomapp.service;

import org.example.wecomapp.client.DifyApiClient;
import org.example.wecomapp.client.WecomApiClient;
import org.example.wecomapp.constants.WecomConstants;
import org.example.wecomapp.dto.CallbackMessage;
import org.example.wecomapp.dto.DifyChatResponse;
import org.example.wecomapp.dto.GetSessionStateResponse;
import org.example.wecomapp.dto.SendMsgResponse;
import org.example.wecomapp.dto.SyncMsgResponse;
import org.example.wecomapp.util.wx.mp.aes.AesException;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息处理服务
 *
 * <p>负责处理企业微信回调消息的业务逻辑，包括：</p>
 * <ul>
 *   <li>调用 CallbackDecryptService 解密回调消息</li>
 *   <li>调用 WecomApiClient 获取聊天记录</li>
 *   <li>找到用户发送的最后一条消息并原样返回</li>
 * </ul>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/94670">企业微信客服消息接口文档</a>
 */
@Service
public class MessageService {

    private final CallbackDecryptService callbackDecryptService;
    private final WecomApiClient wecomApiClient;
    private final DifyApiClient difyApiClient;
    private final DifyConversationService difyConversationService;
    private final MessageContentBuilder messageContentBuilder;
    private final SessionStateService sessionStateService;

    /**
     * 已处理消息的 Token 缓存，用于消息去重
     * 企业微信在服务器未在5秒内响应时会重试发送消息
     */
    private final Set<String> processedTokens = ConcurrentHashMap.newKeySet();

    /**
     * 构造函数
     *
     * @param callbackDecryptService 回调解密服务
     * @param wecomApiClient         企业微信 API 客户端
     * @param difyApiClient          Dify API 客户端
     * @param messageContentBuilder  消息内容构建器
     * @param sessionStateService    会话状态服务
     */
    public MessageService(CallbackDecryptService callbackDecryptService,
                          WecomApiClient wecomApiClient,
                          DifyApiClient difyApiClient,
                          DifyConversationService difyConversationService,
                          MessageContentBuilder messageContentBuilder,
                          SessionStateService sessionStateService) {
        this.callbackDecryptService = callbackDecryptService;
        this.wecomApiClient = wecomApiClient;
        this.difyApiClient = difyApiClient;
        this.difyConversationService = difyConversationService;
        this.messageContentBuilder = messageContentBuilder;
        this.sessionStateService = sessionStateService;
    }

    /**
     * 处理企业微信回调消息
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>调用 CallbackDecryptService 解密回调消息</li>
     *   <li>调用 sync_msg 接口获取消息列表</li>
     *   <li>从消息列表中找到用户发送的最后一条消息 (origin=3, 微信客户发送)</li>
     *   <li>调用 send_msg 接口原样返回给用户</li>
     * </ol>
     *
     * @param msgSignature 消息签名
     * @param timestamp    时间戳
     * @param nonce        随机数
     * @param reqBody      请求体 XML
     * @throws AesException 解密失败时抛出异常
     */
    @Async
    public void handleMessage(String msgSignature, String timestamp, String nonce, String reqBody) throws AesException {
        // ==================== 步骤 1: 解密回调消息 ====================
        System.out.println("\n========== 步骤 1: 解密回调消息 ==========");
        CallbackMessage callbackMessage = callbackDecryptService.decrypt(msgSignature, timestamp, nonce, reqBody);
        String openKfid = callbackMessage.getOpenKfid();
        String token = callbackMessage.getToken();

        // ==================== 步骤 2: 消息去重 ====================
        System.out.println("========== 步骤 2: 消息去重 ==========");
        if (processedTokens.contains(token)) {
            System.out.println("消息已处理过，跳过重复处理。Token: " + token);
            new JSONObject("{\"errcode\":0,\"errmsg\":\"ok\"}");
            return;
        }
        processedTokens.add(token);

        // ==================== 步骤 3: 拉取消息列表 ====================
        System.out.println("========== 步骤 3: 拉取消息列表（分页） ==========");
        List<SyncMsgResponse.MsgItem> allMessages = new ArrayList<>();
        String cursor = null;

        // 分页拉取所有消息
        do {
            SyncMsgResponse syncResult = wecomApiClient.syncMsg(token, openKfid, cursor, 1000);
            logSyncResult(syncResult);

            // 收集消息
            if (syncResult.getMsg_list() != null) {
                allMessages.addAll(syncResult.getMsg_list());
            }

            // 如果还有更多消息，继续拉取
            if (syncResult.getHas_more() != null && syncResult.getHas_more() == 1) {
                cursor = syncResult.getNext_cursor();
                System.out.println("---------- 还有更多消息，继续拉取，cursor: " + cursor);
            } else {
                break;
            }
        } while (cursor != null && !cursor.isEmpty());

        System.out.println("拉取完成，共 " + allMessages.size() + " 条消息");

        // ==================== 步骤 4: 查找用户最后一条消息 ====================
        System.out.println("========== 步骤 4: 查找用户最后一条消息 ==========");
        SyncMsgResponse.MsgItem lastUserMsg = findLastUserMessage(allMessages);
        if (lastUserMsg != null) {
            System.out.println("最终用户消息 msgid: " + lastUserMsg.getMsgid() + ", msgtype: " + lastUserMsg.getMsgtype());
        } else {
            System.out.println("未找到用户发送的消息");
        }

        // ==================== 步骤 5: 会话状态管理 ====================
        if (lastUserMsg != null) {
            String externalUserid = lastUserMsg.getExternal_userid();
            System.out.println("========== 步骤 5: 会话状态管理 ==========");

            // 获取会话状态
            GetSessionStateResponse sessionState = sessionStateService.getSessionState(openKfid, externalUserid);
            System.out.println("---------- 当前会话状态 ----------");
            System.out.println("  service_state: " + sessionState.getService_state() + " (" + sessionState.getServiceStateDesc() + ")");
            System.out.println("  servicer_userid: " + sessionState.getServicer_userid());
            System.out.println("---------------------------------");

            // 如果会话状态不是由智能助手接待，则变更为由智能助手接待
            if (sessionState.getService_state() != WecomConstants.ServiceState.BOT_SERVICE) {
                System.out.println("--> 非智能助手接待状态，正在变更为智能助手接待...");
                sessionStateService.transferToBotService(openKfid, externalUserid);
                System.out.println("--> 已变更为由智能助手接待");
            } else {
                System.out.println("--> 已是智能助手接待状态，无需变更");
            }

            // ==================== 步骤 6: 发送回复消息 ====================
            System.out.println("========== 步骤 6: 发送回复消息 ==========");
            sendReplyMessage(lastUserMsg, openKfid);
            System.out.println("============================================\n");
            return;
        }
    }

    /**
     * 记录 sync_msg 响应日志
     *
     * @param syncResult 同步消息响应
     */
    private void logSyncResult(SyncMsgResponse syncResult) {
        System.out.println("========== sync_msg 响应 ==========");
        System.out.println("errcode: " + syncResult.getErrcode());
        System.out.println("errmsg: " + syncResult.getErrmsg());
        System.out.println("next_cursor: " + syncResult.getNext_cursor());
        System.out.println("has_more: " + syncResult.getHas_more());

        List<SyncMsgResponse.MsgItem> msgList = syncResult.getMsg_list();
        System.out.println("msg_list 大小: " + (msgList != null ? msgList.size() : 0));

        // 只打印最后一条消息
        if (msgList != null && !msgList.isEmpty()) {
            SyncMsgResponse.MsgItem lastMsg = msgList.get(msgList.size() - 1);
            System.out.println("---------- 最后一条消息 ----------");
            System.out.println("  msgid: " + lastMsg.getMsgid());
            System.out.println("  open_kfid: " + lastMsg.getOpen_kfid());
            System.out.println("  external_userid: " + lastMsg.getExternal_userid());
            System.out.println("  send_time: " + lastMsg.getSend_time());
            System.out.println("  origin: " + lastMsg.getOrigin());
            System.out.println("  msgtype: " + lastMsg.getMsgtype());
            if (lastMsg.getText() != null) {
                System.out.println("  text.content: " + lastMsg.getText().getContent());
            }
        }
        System.out.println("====================================");
    }

    /**
     * 从消息列表中找到用户发送的最后一条消息
     *
     * @param msgList 消息列表
     * @return 用户发送的最后一条消息，如果没有找到则返回 null
     */
    private SyncMsgResponse.MsgItem findLastUserMessage(List<SyncMsgResponse.MsgItem> msgList) {
        if (msgList == null) {
            return null;
        }

        for (int i = msgList.size() - 1; i >= 0; i--) {
            SyncMsgResponse.MsgItem msg = msgList.get(i);
            System.out.println("检查消息[" + i + "] origin=" + msg.getOrigin());
            // origin=3 表示微信客户发送的消息
            if (msg.getOrigin() != null && msg.getOrigin() == WecomConstants.MsgOrigin.WECHAT_USER) {
                System.out.println("找到用户消息！消息[" + i + "]");
                return msg;
            }
        }

        return null;
    }

    /**
     * 发送回复消息给用户
     *
     * <p>对于文本消息，调用 Dify API 获取 AI 回答后发送给用户</p>
     * <p>对于非文本消息，原样返回给用户</p>
     *
     * @param lastUserMsg 用户发送的最后一条消息
     * @param openKfid    客服帐号ID
     */
    private void sendReplyMessage(SyncMsgResponse.MsgItem lastUserMsg, String openKfid) {
        String msgtype = lastUserMsg.getMsgtype();
        String externalUserid = lastUserMsg.getExternal_userid();

        if ("text".equals(msgtype) && lastUserMsg.getText() != null) {
            // ==================== 文本消息：调用 Dify AI ====================
            System.out.println("---------- [文本消息] 调用 Dify AI ----------");
            String userQuery = lastUserMsg.getText().getContent();
            System.out.println("用户消息：" + userQuery);

            // 获取该用户的会话ID（用于继续对话）
            String conversationId = difyConversationService.getConversationId(externalUserid);
            System.out.println("会话ID：" + (conversationId != null ? conversationId : "新会话"));

            // 调用 Dify API
            DifyChatResponse difyResponse = difyApiClient.chatMessage(userQuery, externalUserid, conversationId);
            String aiAnswer = difyResponse.getAnswer();
            System.out.println("AI 回答：" + aiAnswer);

            // 缓存新的会话ID
            if (difyResponse.getConversationId() != null && !difyResponse.getConversationId().isEmpty()) {
                difyConversationService.saveConversationId(externalUserid, difyResponse.getConversationId());
                System.out.println("会话ID已缓存: " + difyResponse.getConversationId());
            }

            // -------------------- 构造并发送回复 --------------------
            System.out.println("---------- 构造并发送文本回复 ----------");
            JSONObject textContent = new JSONObject();
            textContent.put("content", aiAnswer);
            JSONObject sendContent = new JSONObject();
            sendContent.put("msgtype", "text");
            sendContent.put("text", textContent);

            SendMsgResponse sendResult = wecomApiClient.sendMsg(externalUserid, openKfid, "text", textContent);
            System.out.println("发送结果：" + sendResult.getErrcode() + " - " + sendResult.getErrmsg());
        } else {
            // ==================== 非文本消息：原样转发 ====================
            System.out.println("---------- [非文本消息] 原样转发 ----------");
            System.out.println("消息类型: " + msgtype);
            JSONObject sendContent = messageContentBuilder.build(lastUserMsg, msgtype);
            if (sendContent != null) {
                SendMsgResponse sendResult = wecomApiClient.sendMsg(externalUserid, openKfid, msgtype, sendContent);
                System.out.println("转发结果：" + sendResult.getErrcode() + " - " + sendResult.getErrmsg());
            } else {
                System.out.println("不支持的消息类型，跳过回复");
            }
        }
    }
}
