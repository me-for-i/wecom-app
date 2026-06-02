package org.example.wecomapp.controller;

import org.example.wecomapp.util.wx.mp.aes.AesException;
import org.example.wecomapp.util.wx.mp.aes.WXBizJsonMsgCrypt;
import org.springframework.web.bind.annotation.*;

/**
 * 企业微信服务器验证控制器
 *
 * <p>处理企业微信配置回调URL时的服务器地址有效性验证</p>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/90930">企业微信回调配置文档</a>
 */
@RestController
public class ValidateController {

    private final WXBizJsonMsgCrypt wxcpt;

    /**
     * 构造函数
     *
     * @param wxcpt 企业微信消息加解密实例
     */
    public ValidateController(WXBizJsonMsgCrypt wxcpt) {
        this.wxcpt = wxcpt;
    }

    /**
     * 验证服务器地址有效性
     *
     * <p>企业微信配置回调URL时，会发送GET请求验证服务器地址的有效性</p>
     *
     * @param msg_signature 企业微信加密签名
     * @param timestamp     时间戳
     * @param nonce         随机数
     * @param echostr       加密的字符串
     * @return 解密后的明文消息内容
     * @throws AesException 解密失败时抛出异常
     */
    @GetMapping("/")
    public String validate(@RequestParam String msg_signature,
                           @RequestParam String timestamp,
                           @RequestParam String nonce,
                           @RequestParam String echostr) throws AesException {
        return wxcpt.VerifyURL(msg_signature, timestamp, nonce, echostr);
    }
}
