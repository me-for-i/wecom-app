package org.example.wecomapp.dto;

import org.json.JSONObject;

/**
 * 消息回复封装
 *
 * <p>包含发送给企业微信的 msgtype 和 content，
 * 解耦「用户消息类型」与「回复消息类型」。</p>
 *
 * @param msgtype 回复消息类型，如 "text"、"image"、"voice"
 * @param content 回复消息的内层内容 JSON
 */
public record Reply(String msgtype, JSONObject content) {

    /**
     * 创建文本类型回复
     *
     * @param text 文本内容
     * @return Reply 实例，msgtype 为 "text"
     */
    public static Reply text(String text) {
        JSONObject content = new JSONObject();
        content.put("content", text);
        return new Reply("text", content);
    }
}
