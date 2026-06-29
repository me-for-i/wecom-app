package org.example.wecomapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "wecom")
public class WecomProperties {

    private String token;
    private String corpId;
    private String corpSecret;
    private String encodingAesKey;
    private String accessToken;
    private String getTokenUrl;
    private String syncMsgUrl;
    private String sendMsgUrl;
    private String getSessionStateUrl;
    private String transSessionStateUrl;
    private String downloadMediaUrl;
    private String difyApiKey;
    private String difyApiServer;
    private String getCustomerUrl;

    public String getDownloadMediaUrl() {
        return downloadMediaUrl;
    }

    public void setDownloadMediaUrl(String downloadMediaUrl) {
        this.downloadMediaUrl = downloadMediaUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCorpId() {
        return corpId;
    }

    public void setCorpId(String corpId) {
        this.corpId = corpId;
    }

    public String getCorpSecret() {
        return corpSecret;
    }

    public void setCorpSecret(String corpSecret) {
        this.corpSecret = corpSecret;
    }

    public String getEncodingAesKey() {
        return encodingAesKey;
    }

    public void setEncodingAesKey(String encodingAesKey) {
        this.encodingAesKey = encodingAesKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getGetTokenUrl() {
        return getTokenUrl;
    }

    public void setGetTokenUrl(String getTokenUrl) {
        this.getTokenUrl = getTokenUrl;
    }

    public String getSyncMsgUrl() {
        return syncMsgUrl;
    }

    public void setSyncMsgUrl(String syncMsgUrl) {
        this.syncMsgUrl = syncMsgUrl;
    }

    public String getSendMsgUrl() {
        return sendMsgUrl;
    }

    public void setSendMsgUrl(String sendMsgUrl) {
        this.sendMsgUrl = sendMsgUrl;
    }

    public String getGetSessionStateUrl() {
        return getSessionStateUrl;
    }

    public void setGetSessionStateUrl(String getSessionStateUrl) {
        this.getSessionStateUrl = getSessionStateUrl;
    }

    public String getTransSessionStateUrl() {
        return transSessionStateUrl;
    }

    public void setTransSessionStateUrl(String transSessionStateUrl) {
        this.transSessionStateUrl = transSessionStateUrl;
    }

    public String getDifyApiKey() {
        return difyApiKey;
    }

    public void setDifyApiKey(String difyApiKey) {
        this.difyApiKey = difyApiKey;
    }

    public String getDifyApiServer() {
        return difyApiServer;
    }

    public void setDifyApiServer(String difyApiServer) {
        this.difyApiServer = difyApiServer;
    }

    public String getGetCustomerUrl() {
        return getCustomerUrl;
    }

    public void setGetCustomerUrl(String getCustomerUrl) {
        this.getCustomerUrl = getCustomerUrl;
    }
}
