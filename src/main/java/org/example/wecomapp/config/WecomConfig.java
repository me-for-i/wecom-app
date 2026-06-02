package org.example.wecomapp.config;

import org.example.wecomapp.util.wx.mp.aes.AesException;
import org.example.wecomapp.util.wx.mp.aes.WXBizJsonMsgCrypt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WecomConfig {

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
        return RestClient.create();
    }
}
