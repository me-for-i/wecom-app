package org.example.wecomapp.config;

import org.example.wecomapp.util.wx.mp.aes.AesException;
import org.example.wecomapp.util.wx.mp.aes.WXBizJsonMsgCrypt;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import java.net.ProxySelector;

@Configuration
public class WecomConfig {

    /**
     * 连接超时时间（毫秒）
     */
    private static final int CONNECT_TIMEOUT = 10_000;

    /**
     * 读取超时时间（毫秒）
     */
    private static final int READ_TIMEOUT = 30_000;

    @Bean
    public WXBizJsonMsgCrypt wxbizJsonMsgCrypt(WecomProperties properties) throws AesException {
        return new WXBizJsonMsgCrypt(
                properties.getToken(),
                properties.getEncodingAesKey(),
                properties.getCorpId()
        );
    }

    @Bean
    public RestClient restClient() throws Exception {
        // 信任所有 SSL 证书（企业内网 / 自签名证书场景）
        SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                .build();

        HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(20)           // 最大总连接数
                .setMaxConnPerRoute(10)        // 每个目标主机最大连接数
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(30))
                        .build())
                .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                        .setSslContext(sslContext)
                        .build())
                .build();

        // 使用系统默认代理选择器，仅在 JVM 系统属性明确配置代理时才走代理
        // 与原 SimpleClientHttpRequestFactory 行为一致：无代理属性 → 直连
        SystemDefaultRoutePlanner routePlanner = new SystemDefaultRoutePlanner(ProxySelector.getDefault());

        org.apache.hc.client5.http.impl.classic.HttpClientBuilder httpClientBuilder =
                HttpClients.custom()
                        .setConnectionManager(connectionManager)
                        .setRoutePlanner(routePlanner);

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
