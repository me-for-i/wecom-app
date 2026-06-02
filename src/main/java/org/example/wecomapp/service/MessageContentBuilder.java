package org.example.wecomapp.service;

import org.example.wecomapp.constants.WecomConstants;
import org.example.wecomapp.dto.SyncMsgResponse;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 消息内容构建器
 *
 * <p>负责根据消息类型构建发送消息的内容</p>
 *
 * <p>支持的消息类型：</p>
 * <ul>
 *   <li>text: 文本消息 - 提取 text.content</li>
 *   <li>image: 图片消息 - 提取 image.media_id</li>
 *   <li>voice: 语音消息 - 提取 voice.media_id</li>
 *   <li>video: 视频消息 - 提取 video.media_id</li>
 *   <li>file: 文件消息 - 提取 file.media_id</li>
 *   <li>link: 图文链接 - 提取 title, desc, url, pic_url</li>
 *   <li>miniprogram: 小程序 - 提取 appid, title, pic_media_id, page</li>
 * </ul>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/94677">send_msg 接口文档</a>
 */
@Component
public class MessageContentBuilder {

    /**
     * 根据消息类型构建发送消息的内容
     *
     * @param msg     消息对象
     * @param msgtype 消息类型
     * @return 构造好的发送内容 JSON 对象，如果消息类型不支持则返回 null
     */
    public JSONObject build(SyncMsgResponse.MsgItem msg, String msgtype) {
        JSONObject content = new JSONObject();

        switch (msgtype) {
            case WecomConstants.MsgType.TEXT:
                return buildTextContent(msg);
            case WecomConstants.MsgType.IMAGE:
                return buildImageContent(msg);
            case WecomConstants.MsgType.VOICE:
                return buildVoiceContent(msg);
            case WecomConstants.MsgType.VIDEO:
                return buildVideoContent(msg);
            case WecomConstants.MsgType.FILE:
                return buildFileContent(msg);
            case WecomConstants.MsgType.LINK:
                return buildLinkContent(msg);
            case WecomConstants.MsgType.MINIPROGRAM:
                return buildMiniprogramContent(msg);
            default:
                return buildDefaultContent(msg);
        }
    }

    /**
     * 构建文本消息内容
     *
     * @param msg 消息对象
     * @return 文本消息内容
     */
    private JSONObject buildTextContent(SyncMsgResponse.MsgItem msg) {
        JSONObject content = new JSONObject();
        SyncMsgResponse.TextContent text = msg.getText();
        if (text != null) {
            content.put("content", text.getContent());
        }
        return content;
    }

    /**
     * 构建图片消息内容
     *
     * @param msg 消息对象
     * @return 图片消息内容
     */
    private JSONObject buildImageContent(SyncMsgResponse.MsgItem msg) {
        JSONObject content = new JSONObject();
        SyncMsgResponse.ImageContent image = msg.getImage();
        if (image != null) {
            content.put("media_id", image.getMedia_id());
        }
        return content;
    }

    /**
     * 构建语音消息内容
     *
     * @param msg 消息对象
     * @return 语音消息内容
     */
    private JSONObject buildVoiceContent(SyncMsgResponse.MsgItem msg) {
        JSONObject content = new JSONObject();
        SyncMsgResponse.VoiceContent voice = msg.getVoice();
        if (voice != null) {
            content.put("media_id", voice.getMedia_id());
        }
        return content;
    }

    /**
     * 构建视频消息内容
     *
     * @param msg 消息对象
     * @return 视频消息内容
     */
    private JSONObject buildVideoContent(SyncMsgResponse.MsgItem msg) {
        JSONObject content = new JSONObject();
        SyncMsgResponse.VideoContent video = msg.getVideo();
        if (video != null) {
            content.put("media_id", video.getMedia_id());
        }
        return content;
    }

    /**
     * 构建文件消息内容
     *
     * @param msg 消息对象
     * @return 文件消息内容
     */
    private JSONObject buildFileContent(SyncMsgResponse.MsgItem msg) {
        JSONObject content = new JSONObject();
        SyncMsgResponse.FileContent file = msg.getFile();
        if (file != null) {
            content.put("media_id", file.getMedia_id());
        }
        return content;
    }

    /**
     * 构建图文链接消息内容
     *
     * @param msg 消息对象
     * @return 图文链接消息内容
     */
    private JSONObject buildLinkContent(SyncMsgResponse.MsgItem msg) {
        JSONObject content = new JSONObject();
        SyncMsgResponse.LinkContent link = msg.getLink();
        if (link != null) {
            content.put("title", link.getTitle());
            content.put("desc", link.getDesc());
            content.put("url", link.getUrl());
            content.put("pic_url", link.getPic_url());
        }
        return content;
    }

    /**
     * 构建小程序消息内容
     *
     * @param msg 消息对象
     * @return 小程序消息内容
     */
    private JSONObject buildMiniprogramContent(SyncMsgResponse.MsgItem msg) {
        JSONObject content = new JSONObject();
        SyncMsgResponse.MiniprogramContent miniprogram = msg.getMiniprogram();
        if (miniprogram != null) {
            content.put("appid", miniprogram.getAppid());
            content.put("title", miniprogram.getTitle());
            content.put("pic_media_id", miniprogram.getPic_media_id());
            content.put("page", miniprogram.getPage());
        }
        return content;
    }

    /**
     * 构建默认消息内容（不支持的消息类型）
     *
     * <p>尝试提取文本内容作为 fallback</p>
     *
     * @param msg 消息对象
     * @return 默认消息内容，如果没有文本内容则返回 null
     */
    private JSONObject buildDefaultContent(SyncMsgResponse.MsgItem msg) {
        System.out.println("不支持的消息类型，尝试提取文本内容");
        SyncMsgResponse.TextContent text = msg.getText();
        if (text != null) {
            JSONObject content = new JSONObject();
            content.put("content", text.getContent());
            return content;
        }
        return null;
    }
}
