package org.example.wecomapp.service;

import org.example.wecomapp.dto.CallbackMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息处理服务（入口）
 *
 * <p>负责解密回调消息、Token 去重，然后委托给 {@link MessageAggregatorService} 进行消息聚合与防抖处理。</p>
 *
 * @author dixonyen
 * @see MessageAggregatorService
 */
@Service
public class MessageService {

    private final CallbackDecryptService callbackDecryptService;
    private final MessageAggregatorService messageAggregatorService;

    /**
     * 已处理消息的 Token 缓存，用于消息去重
     * 企业微信在服务器未在5秒内响应时会重试发送消息
     */
    private final Set<String> processedTokens = ConcurrentHashMap.newKeySet();

    public MessageService(CallbackDecryptService callbackDecryptService,
                          MessageAggregatorService messageAggregatorService) {
        this.callbackDecryptService = callbackDecryptService;
        this.messageAggregatorService = messageAggregatorService;
    }

    /**
     * 处理企业微信回调消息
     *
     * <p>流程：</p>
     * <ol>
     *   <li>解密回调消息</li>
     *   <li>Token 去重（防微信重试）</li>
     *   <li>委托 MessageAggregatorService 进行消息聚合与防抖处理</li>
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

            // ==================== 步骤 3: 委托聚合器 ====================
            System.out.println("========== 步骤 3: 委托消息聚合器 ==========");
            messageAggregatorService.onCallback(token, openKfid);

        } catch (Exception e) {
            System.err.println("========== [handleMessage 异常] ==========");
            System.err.println("消息处理失败: " + e.getMessage());
            e.printStackTrace(System.err);
            System.err.println("==========================================");
        }
    }
}
