package org.example.wecomapp.service;

import org.example.wecomapp.dto.CallbackMessage;
import org.example.wecomapp.util.wx.mp.aes.AesException;
import org.example.wecomapp.util.wx.mp.aes.WXBizJsonMsgCrypt;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.stereotype.Service;

/**
 * 企业微信回调消息解密服务
 *
 * <p>负责处理企业微信回调消息的解密和解析，包括：</p>
 * <ul>
 *   <li>将 XML 请求体转为 JSON</li>
 *   <li>调用 WXBizJsonMsgCrypt 解密消息</li>
 *   <li>从解密后的 XML 中提取关键字段</li>
 * </ul>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/90930">企业微信回调配置文档</a>
 */
@Service
public class CallbackDecryptService {

    private final WXBizJsonMsgCrypt wxcpt;

    /**
     * 构造函数
     *
     * @param wxcpt 企业微信消息加解密实例
     */
    public CallbackDecryptService(WXBizJsonMsgCrypt wxcpt) {
        this.wxcpt = wxcpt;
    }

    /**
     * 解密企业微信回调消息
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>将 XML 请求体转为 JSON 并转小写 key</li>
     *   <li>调用 WXBizJsonMsgCrypt 解密消息</li>
     *   <li>从解密后的 XML 中提取 OpenKfId 和 Token</li>
     * </ol>
     *
     * @param msgSignature 消息签名
     * @param timestamp    时间戳
     * @param nonce        随机数
     * @param reqBody      请求体 XML
     * @return 解密后的回调消息对象
     * @throws AesException 解密失败时抛出异常
     */
    public CallbackMessage decrypt(String msgSignature, String timestamp, String nonce, String reqBody) throws AesException {
        // 1. 将 XML body 转为 JSON（key 转小写），供 DecryptMsg 使用
        JSONObject xmlJson = XML.toJSONObject(reqBody).optJSONObject("xml");
        JSONObject lowerCaseJson = new JSONObject();
        var keys = xmlJson.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            lowerCaseJson.put(key.toLowerCase(), xmlJson.get(key));
        }

        // 2. 解密消息
        System.out.println("\n========== [回调解密] 开始解密 ==========");
        String decryptedXml = wxcpt.DecryptMsg(msgSignature, timestamp, nonce, lowerCaseJson.toString());

        System.out.println("解密后明文 XML: " + decryptedXml);

        // 3. 解析解密后的 XML，提取字段
        System.out.println("---------- [回调解密] 提取字段 ----------");
        JSONObject decryptedJson = XML.toJSONObject(decryptedXml).optJSONObject("xml");
        String openKfid = decryptedJson.optString("OpenKfId", "");
        String token = decryptedJson.optString("Token", "");
        System.out.println("  OpenKfId: " + openKfid);
        System.out.println("  Token: " + token);

        // 4. 构造回调消息对象
        CallbackMessage message = new CallbackMessage();
        message.setOpenKfid(openKfid);
        message.setToken(token);
        message.setDecryptedXml(decryptedXml);
        message.setDecryptedJson(decryptedJson);

        return message;
    }
}
