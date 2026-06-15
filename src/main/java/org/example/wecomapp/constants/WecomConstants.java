package org.example.wecomapp.constants;

import java.util.Set;

/**
 * 企业微信常量定义
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/94670">企业微信客服消息接口文档</a>
 */
public final class WecomConstants {

    private WecomConstants() {
        // 私有构造函数，防止实例化
    }

    /**
     * 消息类型常量
     */
    public static final class MsgType {
        /** 文本消息 */
        public static final String TEXT = "text";
        /** 图片消息 */
        public static final String IMAGE = "image";
        /** 语音消息 */
        public static final String VOICE = "voice";
        /** 视频消息 */
        public static final String VIDEO = "video";
        /** 文件消息 */
        public static final String FILE = "file";
        /** 图文链接消息 */
        public static final String LINK = "link";
        /** 小程序消息 */
        public static final String MINIPROGRAM = "miniprogram";

        private MsgType() {
        }
    }

    /**
     * 消息来源常量
     */
    public static final class MsgOrigin {
        /** 微信客户发送的消息 */
        public static final int WECHAT_USER = 3;
        /** 系统推送的事件消息 */
        public static final int SYSTEM = 4;
        /** 接待人员在企业微信客户端发送的消息 */
        public static final int SERVICER = 5;

        private MsgOrigin() {
        }
    }

    /**
     * 响应状态码常量
     */
    public static final class ErrCode {
        /** 成功 */
        public static final int SUCCESS = 0;

        private ErrCode() {
        }
    }

    /**
     * 会话状态常量
     */
    public static final class ServiceState {
        /** 未处理 - 新会话接入（客户发消息咨询） */
        public static final int UNTREATED = 0;
        /** 由智能助手接待 - 可使用API回复消息 */
        public static final int BOT_SERVICE = 1;
        /** 待接入池排队中 - 在待接入池中排队等待接待人员接入 */
        public static final int WAITING = 2;
        /** 由人工接待 - 人工接待中 */
        public static final int MANUAL_SERVICE = 3;
        /** 已结束/未开始 - 会话已经结束或未开始 */
        public static final int FINISHED = 4;

        private ServiceState() {
        }

        /**
         * 获取会话状态描述
         *
         * @param state 会话状态值
         * @return 会话状态描述
         */
        public static String getDesc(int state) {
            switch (state) {
                case UNTREATED:
                    return "未处理";
                case BOT_SERVICE:
                    return "由智能助手接待";
                case WAITING:
                    return "待接入池排队中";
                case MANUAL_SERVICE:
                    return "由人工接待";
                case FINISHED:
                    return "已结束/未开始";
                default:
                    return "未知状态(" + state + ")";
            }
        }
    }

    /**
     * 当前服务支持的（已实现 Handler 的）消息类型集合
     *
     * <p>用于在 {@code MessageService} 中判断消息类型是否已有对应的处理器，
     * 对不在集合中的类型可执行其他逻辑（如返回默认提示、转人工等）。</p>
     */
    public static final Set<String> SUPPORTED_MSG_TYPES = Set.of(
            MsgType.TEXT,
            MsgType.IMAGE,
            MsgType.VOICE
    );

    /**
     * Dify 支持作为文件附件上传的消息类型集合
     *
     * <p>这些类型的消息需要先将媒体文件从企业微信下载，再上传到 Dify，
     * 然后在 chat-messages 请求中以 {@code files} 参数引用。</p>
     */
    public static final Set<String> DIFY_UPLOADABLE_TYPES = Set.of(
            MsgType.IMAGE,
            MsgType.VOICE
    );
}
