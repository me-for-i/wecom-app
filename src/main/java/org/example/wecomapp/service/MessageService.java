package org.example.wecomapp.service;

import org.example.wecomapp.client.WecomApiClient;
import org.example.wecomapp.constants.WecomConstants;
import org.example.wecomapp.dto.CallbackMessage;
import org.example.wecomapp.dto.GetSessionStateResponse;
import org.example.wecomapp.dto.Reply;
import org.example.wecomapp.dto.SendMsgResponse;
import org.example.wecomapp.dto.SyncMsgResponse;
import org.example.wecomapp.service.handler.MessageHandler;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息处理服务
 *
 * <p>负责处理企业微信回调消息的业务逻辑，包括：</p>
 * <ul>
 *   <li>调用 CallbackDecryptService 解密回调消息</li>
 *   <li>调用 WecomApiClient 获取聊天记录</li>
 *   <li>找到用户发送的最后一条消息</li>
 *   <li>根据消息类型委托对应的 {@link MessageHandler} 处理回复</li>
 *   <li>对不支持的消息类型执行兜底逻辑</li>
 * </ul>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/94670">企业微信客服消息接口文档</a>
 */
@Service
public class MessageService {

    private final CallbackDecryptService callbackDecryptService;
    private final WecomApiClient wecomApiClient;
    private final SessionStateService sessionStateService;

    /**
     * 消息处理器注册表 —— 消息类型 → 处理器
     */
    private final Map<String, MessageHandler> messageHandlerRegistry = new HashMap<>();

    /**
     * 已处理消息的 Token 缓存，用于消息去重
     * 企业微信在服务器未在5秒内响应时会重试发送消息
     */
    private final Set<String> processedTokens = ConcurrentHashMap.newKeySet();

    /**
     * 当前服务支持的消息类型集合
     *
     * <p>声明此处支持的消息类型有对应的 {@link MessageHandler} 实现。
     * 后续增加新类型时，在此处添加类型常量并创建对应的 Handler 即可。</p>
     */
    private static final Set<String> SUPPORTED_MSG_TYPES = WecomConstants.SUPPORTED_MSG_TYPES;

    /**
     * 构造函数
     *
     * @param callbackDecryptService 回调解密服务
     * @param wecomApiClient         企业微信 API 客户端
     * @param sessionStateService    会话状态服务
     * @param messageHandlers        所有已注册的 MessageHandler 实现（由 Spring 自动注入）
     */
    public MessageService(CallbackDecryptService callbackDecryptService,
                          WecomApiClient wecomApiClient,
                          SessionStateService sessionStateService,
                          List<MessageHandler> messageHandlers) {
        this.callbackDecryptService = callbackDecryptService;
        this.wecomApiClient = wecomApiClient;
        this.sessionStateService = sessionStateService;

        // 注册所有 MessageHandler 实现
        for (MessageHandler handler : messageHandlers) {
            messageHandlerRegistry.put(handler.getMsgType(), handler);
            System.out.println("[MessageService] 注册消息处理器: " + handler.getMsgType()
                    + " -> " + handler.getClass().getSimpleName());
        }
        System.out.println("[MessageService] 当前支持的消息类型: " + SUPPORTED_MSG_TYPES);
    }

    /**
     * 处理企业微信回调消息
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>调用 CallbackDecryptService 解密回调消息</li>
     *   <li>消息去重</li>
     *   <li>调用 sync_msg 接口分页获取消息列表</li>
     *   <li>从消息列表中找到用户发送的最后一条消息 (origin=3)</li>
     *   <li>检查/切换会话状态为智能助手接待</li>
     *   <li>根据消息类型委托对应的 Handler 生成回复并发送</li>
     * </ol>
     *
     * @param msgSignature 消息签名
     * @param timestamp    时间戳
     * @param nonce        随机数
     * @param reqBody      请求体 XML
     */
    @Async
    public void handleMessage(String msgSignature, String timestamp, String nonce, String reqBody) {
        try {
            // ==================== 步骤 1: 解密回调消息 ====================
            System.out.println("\n========== 步骤 1: 解密回调消息 ==========");
            CallbackMessage callbackMessage = callbackDecryptService.decrypt(msgSignature, timestamp, nonce, reqBody);
            String openKfid = callbackMessage.getOpenKfid();
            String token = callbackMessage.getToken();

            // ==================== 步骤 2: 消息去重 ====================
            System.out.println("========== 步骤 2: 消息去重 ==========");
            if (processedTokens.contains(token)) {
                System.out.println("消息已处理过，跳过重复处理。Token: " + token);
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
            }
        } catch (Exception e) {
            System.err.println("========== [handleMessage 异常] ==========");
            System.err.println("消息处理失败: " + e.getMessage());
            e.printStackTrace(System.err);
            System.err.println("==========================================");
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
     * <p>根据消息类型查找已注册的 {@link MessageHandler}：</p>
     * <ul>
     *   <li>如果有对应的 Handler，委托 Handler 构建回复内容并发送</li>
     *   <li>如果消息类型不在 {@link #SUPPORTED_MSG_TYPES} 中，执行兜底逻辑</li>
     * </ul>
     *
     * @param lastUserMsg 用户发送的最后一条消息
     * @param openKfid    客服帐号ID
     */
    private void sendReplyMessage(SyncMsgResponse.MsgItem lastUserMsg, String openKfid) {
        String msgtype = lastUserMsg.getMsgtype();
        String externalUserid = lastUserMsg.getExternal_userid();

        // 检查消息类型是否在支持列表中
        if (!SUPPORTED_MSG_TYPES.contains(msgtype)) {
            System.out.println("---------- [不支持的消息类型: " + msgtype + "] 执行兜底逻辑 ----------");
            handleUnsupportedType(lastUserMsg, openKfid);
            return;
        }

        // 查找对应的消息处理器
        MessageHandler handler = messageHandlerRegistry.get(msgtype);
        if (handler == null) {
            System.out.println("---------- [未注册处理器: " + msgtype + "] 执行兜底逻辑 ----------");
            handleUnsupportedType(lastUserMsg, openKfid);
            return;
        }

        System.out.println("---------- 委托 " + handler.getClass().getSimpleName() + " 处理 ----------");
        Reply reply = handler.buildReplyContent(lastUserMsg, openKfid);

        if (reply == null) {
            System.out.println("处理器返回 null，跳过回复");
            return;
        }

        // 发送回复消息（使用 reply 自身的 msgtype，而非用户消息类型）
        System.out.println("---------- 发送回复（reply msgtype: " + reply.msgtype() + "）----------");
        SendMsgResponse sendResult = wecomApiClient.sendMsg(externalUserid, openKfid, reply.msgtype(), reply.content());
        System.out.println("发送结果：" + sendResult.getErrcode() + " - " + sendResult.getErrmsg());
    }

    /**
     * 处理不支持的消息类型的兜底逻辑
     *
     * <p>当收到 {@link #SUPPORTED_MSG_TYPES} 之外的消息类型时调用。
     * 当前行为：发送一条默认提示消息。</p>
     *
     * @param userMsg  用户消息
     * @param openKfid 客服帐号ID
     */
    private void handleUnsupportedType(SyncMsgResponse.MsgItem userMsg, String openKfid) {
        String msgtype = userMsg.getMsgtype();
        String externalUserid = userMsg.getExternal_userid();

        System.out.println("不支持的消息类型: " + msgtype + "，发送默认提示");

        // 发送一条默认提示文本
        JSONObject textContent = new JSONObject();
        textContent.put("content", "抱歉，我暂时无法处理 " + msgtype + " 类型的消息，请发送文字描述。");

        SendMsgResponse sendResult = wecomApiClient.sendMsg(externalUserid, openKfid, "text", textContent);
        System.out.println("兜底回复结果：" + sendResult.getErrcode() + " - " + sendResult.getErrmsg());
    }
}
