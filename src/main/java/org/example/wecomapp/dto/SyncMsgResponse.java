package org.example.wecomapp.dto;

import java.util.List;

/**
 * 同步客服消息接口响应
 *
 * <p>接口地址：POST https://qyapi.weixin.qq.com/cgi-bin/kf/sync_msg</p>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/94672">sync_msg 接口文档</a>
 */
public class SyncMsgResponse extends WecomBaseResponse {

    /**
     * 下一次拉取的游标，当 has_more 为 1 时有效
     */
    private String next_cursor;

    /**
     * 是否还有更多数据
     * <ul>
     *   <li>1: 还有更多数据</li>
     *   <li>0: 没有更多数据</li>
     * </ul>
     */
    private Integer has_more;

    /**
     * 消息列表
     */
    private List<MsgItem> msg_list;

    public String getNext_cursor() {
        return next_cursor;
    }

    public void setNext_cursor(String next_cursor) {
        this.next_cursor = next_cursor;
    }

    public Integer getHas_more() {
        return has_more;
    }

    public void setHas_more(Integer has_more) {
        this.has_more = has_more;
    }

    public List<MsgItem> getMsg_list() {
        return msg_list;
    }

    public void setMsg_list(List<MsgItem> msg_list) {
        this.msg_list = msg_list;
    }

    /**
     * 消息项
     */
    public static class MsgItem {

        /**
         * 消息ID
         */
        private String msgid;

        /**
         * 客服帐号ID
         */
        private String open_kfid;

        /**
         * 外部客户ID
         */
        private String external_userid;

        /**
         * 消息发送时间，Unix时间戳
         */
        private Long send_time;

        /**
         * 消息来源
         * <ul>
         *   <li>3: 微信客户发送的消息</li>
         *   <li>4: 系统推送的事件消息</li>
         *   <li>5: 接待人员在企业微信客户端发送的消息</li>
         * </ul>
         */
        private Integer origin;

        /**
         * 客服人员ID（origin=3 时有效）
         */
        private String servicer_userid;

        /**
         * 消息类型
         * <ul>
         *   <li>text: 文本消息</li>
         *   <li>image: 图片消息</li>
         *   <li>voice: 语音消息</li>
         *   <li>video: 视频消息</li>
         *   <li>file: 文件消息</li>
         *   <li>link: 图文链接消息</li>
         *   <li>miniprogram: 小程序消息</li>
         * </ul>
         */
        private String msgtype;

        // 各消息类型的内容字段
        private TextContent text;
        private ImageContent image;
        private VoiceContent voice;
        private VideoContent video;
        private FileContent file;
        private LinkContent link;
        private MiniprogramContent miniprogram;

        // Getters and Setters
        public String getMsgid() {
            return msgid;
        }

        public void setMsgid(String msgid) {
            this.msgid = msgid;
        }

        public String getOpen_kfid() {
            return open_kfid;
        }

        public void setOpen_kfid(String open_kfid) {
            this.open_kfid = open_kfid;
        }

        public String getExternal_userid() {
            return external_userid;
        }

        public void setExternal_userid(String external_userid) {
            this.external_userid = external_userid;
        }

        public Long getSend_time() {
            return send_time;
        }

        public void setSend_time(Long send_time) {
            this.send_time = send_time;
        }

        public Integer getOrigin() {
            return origin;
        }

        public void setOrigin(Integer origin) {
            this.origin = origin;
        }

        public String getServicer_userid() {
            return servicer_userid;
        }

        public void setServicer_userid(String servicer_userid) {
            this.servicer_userid = servicer_userid;
        }

        public String getMsgtype() {
            return msgtype;
        }

        public void setMsgtype(String msgtype) {
            this.msgtype = msgtype;
        }

        public TextContent getText() {
            return text;
        }

        public void setText(TextContent text) {
            this.text = text;
        }

        public ImageContent getImage() {
            return image;
        }

        public void setImage(ImageContent image) {
            this.image = image;
        }

        public VoiceContent getVoice() {
            return voice;
        }

        public void setVoice(VoiceContent voice) {
            this.voice = voice;
        }

        public VideoContent getVideo() {
            return video;
        }

        public void setVideo(VideoContent video) {
            this.video = video;
        }

        public FileContent getFile() {
            return file;
        }

        public void setFile(FileContent file) {
            this.file = file;
        }

        public LinkContent getLink() {
            return link;
        }

        public void setLink(LinkContent link) {
            this.link = link;
        }

        public MiniprogramContent getMiniprogram() {
            return miniprogram;
        }

        public void setMiniprogram(MiniprogramContent miniprogram) {
            this.miniprogram = miniprogram;
        }
    }

    /**
     * 文本消息内容
     */
    public static class TextContent {
        /**
         * 文本内容
         */
        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * 图片消息内容
     */
    public static class ImageContent {
        /**
         * 图片媒体ID
         */
        private String media_id;

        public String getMedia_id() {
            return media_id;
        }

        public void setMedia_id(String media_id) {
            this.media_id = media_id;
        }
    }

    /**
     * 语音消息内容
     */
    public static class VoiceContent {
        /**
         * 语音媒体ID
         */
        private String media_id;

        public String getMedia_id() {
            return media_id;
        }

        public void setMedia_id(String media_id) {
            this.media_id = media_id;
        }
    }

    /**
     * 视频消息内容
     */
    public static class VideoContent {
        /**
         * 视频媒体ID
         */
        private String media_id;

        public String getMedia_id() {
            return media_id;
        }

        public void setMedia_id(String media_id) {
            this.media_id = media_id;
        }
    }

    /**
     * 文件消息内容
     */
    public static class FileContent {
        /**
         * 文件媒体ID
         */
        private String media_id;

        public String getMedia_id() {
            return media_id;
        }

        public void setMedia_id(String media_id) {
            this.media_id = media_id;
        }
    }

    /**
     * 图文链接消息内容
     */
    public static class LinkContent {
        /**
         * 图文链接标题
         */
        private String title;

        /**
         * 图文链接描述
         */
        private String desc;

        /**
         * 图文链接URL
         */
        private String url;

        /**
         * 图文链接封面图片URL
         */
        private String pic_url;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getPic_url() {
            return pic_url;
        }

        public void setPic_url(String pic_url) {
            this.pic_url = pic_url;
        }
    }

    /**
     * 小程序消息内容
     */
    public static class MiniprogramContent {
        /**
         * 小程序appid
         */
        private String appid;

        /**
         * 小程序消息标题
         */
        private String title;

        /**
         * 小程序消息封面图媒体ID
         */
        private String pic_media_id;

        /**
         * 小程序页面路径
         */
        private String page;

        public String getAppid() {
            return appid;
        }

        public void setAppid(String appid) {
            this.appid = appid;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getPic_media_id() {
            return pic_media_id;
        }

        public void setPic_media_id(String pic_media_id) {
            this.pic_media_id = pic_media_id;
        }

        public String getPage() {
            return page;
        }

        public void setPage(String page) {
            this.page = page;
        }
    }

    @Override
    public String toString() {
        return "SyncMsgResponse{" +
                "errcode=" + getErrcode() +
                ", errmsg='" + getErrmsg() + '\'' +
                ", next_cursor='" + next_cursor + '\'' +
                ", has_more=" + has_more +
                ", msg_list=" + msg_list +
                '}';
    }
}
