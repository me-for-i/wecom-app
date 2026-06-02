package org.example.wecomapp.dto;

/**
 * 变更会话状态接口响应
 *
 * <p>接口地址：POST https://qyapi.weixin.qq.com/cgi-bin/kf/service_state/trans</p>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/94670">企业微信客服消息接口文档</a>
 */
public class TransSessionStateResponse extends WecomBaseResponse {

    /**
     * 消息码
     */
    private String msg_code;

    public String getMsg_code() {
        return msg_code;
    }

    public void setMsg_code(String msg_code) {
        this.msg_code = msg_code;
    }

    @Override
    public String toString() {
        return "TransSessionStateResponse{" +
                "errcode=" + getErrcode() +
                ", errmsg='" + getErrmsg() + '\'' +
                ", msg_code='" + msg_code + '\'' +
                '}';
    }
}
