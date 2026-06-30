package org.example.wecomapp.config;

import org.example.wecomapp.util.wx.mp.aes.AesException;
import org.example.wecomapp.util.wx.mp.aes.WXBizJsonMsgCrypt;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

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
    private static final int READ_TIMEOUT = 120_000;

    /**
     * Dify 流式响应读取超时（毫秒）—— LLM 生成耗时可能较长，需给予充足时间
     */
    private static final int DIFY_READ_TIMEOUT = 600_000;

    @Bean
    public WXBizJsonMsgCrypt wxbizJsonMsgCrypt(WecomProperties properties) throws AesException {
        return new WXBizJsonMsgCrypt(
                properties.getToken(),
                properties.getEncodingAesKey(),
                properties.getCorpId()
        );
    }

    @Bean
    public RestClient restClient() {
        HttpClientConnectionManager connectionManager = buildConnectionManager(READ_TIMEOUT);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setRoutePlanner(buildRoutePlanner())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT))
                        .setResponseTimeout(Timeout.ofMilliseconds(READ_TIMEOUT))
                        .build())
                .build();

        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    /**
     * 为 Dify 流式 SSE 响应提供独立的 RestClient，超时时间远大于普通请求，
     * 避免 LLM 长时间生成时触发 "Premature end of chunk coded message body"。
     */
    @Bean
    public RestClient difyRestClient() {
        HttpClientConnectionManager connectionManager = buildConnectionManager(DIFY_READ_TIMEOUT);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setRoutePlanner(buildRoutePlanner())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECT_TIMEOUT))
                        .setResponseTimeout(Timeout.ofMilliseconds(DIFY_READ_TIMEOUT))
                        .build())
                .build();

        return RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    private HttpClientConnectionManager buildConnectionManager(int soTimeoutMs) {
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(10)
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(Timeout.ofMilliseconds(soTimeoutMs))
                        .build())
                .build();
    }

    private SystemDefaultRoutePlanner buildRoutePlanner() {
        return new SystemDefaultRoutePlanner(ProxySelector.getDefault());
    }
}
