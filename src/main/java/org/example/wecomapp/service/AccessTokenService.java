package org.example.wecomapp.service;

import org.example.wecomapp.config.WecomProperties;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;

/**
 * Access Token 管理服务
 *
 * <p>负责获取和缓存企业微信的 access_token，避免频繁调用接口</p>
 *
 * <p>access_token 有效期为 7200 秒（2小时），本服务会在过期前 5 分钟自动刷新</p>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/91039">gettoken 接口文档</a>
 */
@Service
public class AccessTokenService {

    private final RestClient restClient;
    private final WecomProperties properties;

    /**
     * 缓存的 access_token
     */
    private String cachedAccessToken;

    /**
     * access_token 过期时间
     */
    private Instant expireTime;

    /**
     * 构造函数
     *
     * @param restClient Spring RestClient 实例
     * @param properties 企业微信配置属性
     */
    public AccessTokenService(RestClient restClient, WecomProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * 获取 access_token
     *
     * <p>优先使用缓存，如果缓存不存在或已过期，则重新调用接口获取</p>
     *
     * @return access_token 字符串
     * @throws RuntimeException 获取失败时抛出异常
     */
    public String getAccessToken() {
        // 如果缓存存在且未过期，直接返回
        if (cachedAccessToken != null && Instant.now().isBefore(expireTime)) {
            return cachedAccessToken;
        }

        // 重新获取 access_token
        return fetchAccessToken();
    }

    /**
     * 调用微信接口获取新的 access_token
     *
     * <p>接口地址：GET https://qyapi.weixin.qq.com/cgi-bin/gettoken</p>
     *
     * <p>请求参数：</p>
     * <ul>
     *   <li>corpid: 企业ID</li>
     *   <li>corpsecret: 应用的凭证密钥</li>
     * </ul>
     *
     * <p>响应参数：</p>
     * <ul>
     *   <li>errcode: 错误码，0 表示成功</li>
     *   <li>errmsg: 错误信息</li>
     *   <li>access_token: 获取到的凭证</li>
     *   <li>expires_in: 凭证有效时间（秒）</li>
     * </ul>
     *
     * @return access_token 字符串
     * @throws RuntimeException 获取失败时抛出异常
     */
    private String fetchAccessToken() {
        String url = properties.getGetTokenUrl()
                + "?corpid=" + properties.getCorpId()
                + "&corpsecret=" + properties.getCorpSecret();

        String response = restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

        JSONObject jsonResponse = new JSONObject(response);

        int errcode = jsonResponse.optInt("errcode", 0);
        if (errcode != 0) {
            String errmsg = jsonResponse.optString("errmsg", "Unknown error");
            throw new RuntimeException("Failed to get access_token: " + errmsg + " (errcode: " + errcode + ")");
        }

        cachedAccessToken = jsonResponse.getString("access_token");
        int expiresIn = jsonResponse.getInt("expires_in");

        // 提前 5 分钟过期，避免边界情况
        expireTime = Instant.now().plusSeconds(expiresIn - 300);

        System.out.println("Access token refreshed, expires in " + expiresIn + " seconds");

        return cachedAccessToken;
    }
}
