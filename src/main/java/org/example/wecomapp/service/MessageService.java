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
import org.springframework.stereotype.Service;

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
                          MessageContentBuilder messageContentBuilder,
                          SessionStateService sessionStateService) {
        this.callbackDecryptService = callbackDecryptService;
        this.wecomApiClient = wecomApiClient;
        this.difyApiClient = difyApiClient;
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
    public void handleMessage(String msgSignature, String timestamp, String nonce, String reqBody) throws AesException {
        // 1. 解密回调消息
        CallbackMessage callbackMessage = callbackDecryptService.decrypt(msgSignature, timestamp, nonce, reqBody);
        String openKfid = callbackMessage.getOpenKfid();
        String token = callbackMessage.getToken();

        // 2. 检查消息是否已经处理过（企业微信会重试发送未响应的消息）
        if (processedTokens.contains(token)) {
            System.out.println("消息已处理过，跳过重复处理。Token: " + token);
            new JSONObject("{\"errcode\":0,\"errmsg\":\"ok\"}");
            return;
        }
        processedTokens.add(token);

        // 3. 调用 sync_msg 接口
        SyncMsgResponse syncResult = wecomApiClient.syncMsg(token, openKfid, null, 1000);
        logSyncResult(syncResult);

        // 3. 从 msg_list 中找到用户发送的最后一条消息 (origin=3, 微信客户发送)
        SyncMsgResponse.MsgItem lastUserMsg = findLastUserMessage(syncResult.getMsg_list());
        System.out.println("最终用户消息：" + lastUserMsg);

        // 4. 如果找到用户消息，获取会话状态并打印结果，然后原样返回给用户
        if (lastUserMsg != null) {
            String externalUserid = lastUserMsg.getExternal_userid();

            // 获取会话状态并打印结果
            GetSessionStateResponse sessionState = sessionStateService.getSessionState(openKfid, externalUserid);
            System.out.println("========== 会话状态 ==========");
            System.out.println("service_state: " + sessionState.getService_state() + " (" + sessionState.getServiceStateDesc() + ")");
            System.out.println("servicer_userid: " + sessionState.getServicer_userid());
            System.out.println("==============================");

            // 如果会话状态不是由智能助手接待，则变更为由智能助手接待
            if (sessionState.getService_state() != WecomConstants.ServiceState.BOT_SERVICE) {
                System.out.println("会话状态不是由智能助手接待，正在变更...");
                sessionStateService.transferToBotService(openKfid, externalUserid);
                System.out.println("已变更为由智能助手接待");
            }

            sendReplyMessage(lastUserMsg, openKfid);
            return;
        }

        new JSONObject(syncResult.toString());
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

        // 对于文本消息，调用 Dify API 获取 AI 回答
        if ("text".equals(msgtype) && lastUserMsg.getText() != null) {
            String userQuery = lastUserMsg.getText().getContent();
            System.out.println("用户消息内容：" + userQuery);
            System.out.println("准备调用 Dify API...");

            // 调用 Dify API
            DifyChatResponse difyResponse = difyApiClient.chatMessage(userQuery, externalUserid);
            String aiAnswer = difyResponse.getAnswer();
            System.out.println("Dify AI 回答：" + aiAnswer);

            // 构造文本回复消息
            JSONObject textContent = new JSONObject();
            textContent.put("content", aiAnswer);
            JSONObject sendContent = new JSONObject();
            sendContent.put("msgtype", "text");
            sendContent.put("text", textContent);

            // 发送 AI 回答给用户
            SendMsgResponse sendResult = wecomApiClient.sendMsg(externalUserid, openKfid, "text", textContent);
            System.out.println("发送 AI 回答结果：" + sendResult);
        } else {
            // 非文本消息原样返回
            JSONObject sendContent = messageContentBuilder.build(lastUserMsg, msgtype);
            System.out.println("准备构造回复消息：" + sendContent);
            if (sendContent != null) {
                System.out.println("准备发送消息给用户：" + externalUserid);
                SendMsgResponse sendResult = wecomApiClient.sendMsg(externalUserid, openKfid, msgtype, sendContent);
                System.out.println("发送消息结果：" + sendResult);
            }
        }
    }
}
