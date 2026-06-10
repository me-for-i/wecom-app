package org.example.wecomapp.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Dify 会话缓存服务
 *
 * <p>为每个微信用户（external_userid）缓存 Dify 返回的 conversation_id，
 * 使得后续请求可以基于之前的聊天记录继续对话。</p>
 *
 * @author dixonyen
 */
@Service
public class DifyConversationService {

    /**
     * 会话缓存
     * key = external_userid（微信用户ID）
     * value = conversation_id（Dify 会话ID）
     */
    private final ConcurrentHashMap<String, String> conversationCache = new ConcurrentHashMap<>();

    /**
     * 获取指定用户的 Dify 会话ID
     *
     * @param externalUserid 微信用户ID
     * @return 缓存的会话ID，如果没有缓存则返回 null
     */
    public String getConversationId(String externalUserid) {
        String conversationId = conversationCache.get(externalUserid);
        return conversationId;
    }

    /**
     * 保存指定用户的 Dify 会话ID
     *
     * @param externalUserid 微信用户ID
     * @param conversationId Dify 返回的会话ID
     */
    public void saveConversationId(String externalUserid, String conversationId) {
        if (conversationId != null && !conversationId.isEmpty()) {
            conversationCache.put(externalUserid, conversationId);
            System.out.println("  [Dify会话] 缓存 conversation_id=" + conversationId + " for user=" + externalUserid);
        }
    }
}
