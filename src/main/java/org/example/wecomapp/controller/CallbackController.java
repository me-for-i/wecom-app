package org.example.wecomapp.controller;

import org.example.wecomapp.service.MessageService;
import org.example.wecomapp.util.wx.mp.aes.AesException;
import org.springframework.web.bind.annotation.*;

/**
 * 企业微信消息回调控制器
 *
 * <p>处理企业微信服务器推送的消息通知</p>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/90930">企业微信回调配置文档</a>
 */
@RestController
public class CallbackController {

    private final MessageService messageService;

    /**
     * 构造函数
     *
     * @param messageService 消息处理服务
     */
    public CallbackController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 接收企业微信推送的消息
     *
     * <p>当用户发送消息给客服时，企业微信会通过此接口推送消息通知</p>
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>接收加密的消息</li>
     *   <li>调用 MessageService 处理消息</li>
     *   <li>返回 "hello" 字符串</li>
     * </ol>
     *
     * @param msg_signature 企业微信加密签名
     * @param timestamp     时间戳
     * @param nonce         随机数
     * @param req_body      加密的消息体
     * @return 固定返回 "hello" 字符串
     * @throws AesException 解密失败时抛出异常
     */
    @PostMapping("/")
    public String handleCallback(@RequestParam String msg_signature,
                                 @RequestParam String timestamp,
                                 @RequestParam String nonce,
                                 @RequestBody String req_body) throws AesException {

        messageService.handleMessage(msg_signature, timestamp, nonce, req_body);
        return "hello";
    }
}
