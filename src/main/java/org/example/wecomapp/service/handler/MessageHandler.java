package org.example.wecomapp.service.handler;

import org.example.wecomapp.dto.Reply;
import org.example.wecomapp.dto.SyncMsgResponse;

/**
 * 消息处理器接口 —— 策略模式
 *
 * <p>每种消息类型（文本、图片、音频等）对应一个实现类，
 * 每个处理器负责将用户消息转化为可发送给企微的回复内容。</p>
 *
 * <p>实现类需：</p>
 * <ol>
 *   <li>通过 {@link #getMsgType()} 声明自己处理的消息类型</li>
 *   <li>通过 {@link #buildReplyContent(SyncMsgResponse.MsgItem, String)} 构建回复内容</li>
 * </ol>
 *
 * @author dixonyen
 * @see org.example.wecomapp.service.MessageService
 */
public interface MessageHandler {

    /**
     * 返回此处理器支持的消息类型
     *
     * @return 消息类型字符串，例如 "text"、"image"、"voice"
     */
    String getMsgType();

    /**
     * 处理用户消息并构建回复内容
     *
     * <p>处理器在此方法中完成特定业务逻辑：</p>
     * <ul>
     *   <li>文本：查询 Dify AI 获取回答</li>
     *   <li>图片：下载媒体 → 上传 Dify → 附带图片上下文请求 AI</li>
     *   <li>音频：下载媒体 → 上传 Dify → 附带音频上下文请求 AI</li>
     * </ul>
     *
     * <p>注意：回复的 msgtype 可能与用户消息类型不同。
     * 例如用户发送图片/音频，Dify 通常返回文本回复。</p>
     *
     * @param userMsg 用户发送的最后一条消息
     * @param openKfid 客服帐号 ID
     * @return 回复封装（包含 msgtype 和 content），返回 {@code null} 表示跳过回复
     */
    Reply buildReplyContent(SyncMsgResponse.MsgItem userMsg, String openKfid);
}
