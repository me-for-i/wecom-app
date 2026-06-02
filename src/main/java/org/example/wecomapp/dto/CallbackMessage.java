package org.example.wecomapp.dto;

import org.json.JSONObject;

/**
 * 企业微信回调消息对象
 *
 * <p>封装解密后的回调消息信息</p>
 *
 * @author dixonyen
 */
public class CallbackMessage {

    /**
     * 客服帐号ID
     */
    private String openKfid;

    /**
     * 回调事件返回的 Token，用于调用 sync_msg 接口
     */
    private String token;

    /**
     * 解密后的 XML 字符串
     */
    private String decryptedXml;

    /**
     * 解密后的 JSON 对象
     */
    private JSONObject decryptedJson;

    public String getOpenKfid() {
        return openKfid;
    }

    public void setOpenKfid(String openKfid) {
        this.openKfid = openKfid;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getDecryptedXml() {
        return decryptedXml;
    }

    public void setDecryptedXml(String decryptedXml) {
        this.decryptedXml = decryptedXml;
    }

    public JSONObject getDecryptedJson() {
        return decryptedJson;
    }

    public void setDecryptedJson(JSONObject decryptedJson) {
        this.decryptedJson = decryptedJson;
    }

    @Override
    public String toString() {
        return "CallbackMessage{" +
                "openKfid='" + openKfid + '\'' +
                ", token='" + token + '\'' +
                '}';
    }
}
