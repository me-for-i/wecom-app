package org.example.wecomapp.dto;

/**
 * 发送客服消息接口响应
 *
 * <p>接口地址：POST https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg</p>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/94677">send_msg 接口文档</a>
 */
public class SendMsgResponse extends WecomBaseResponse {

    /**
     * 消息ID，发送成功时返回
     */
    private String msgid;

    public String getMsgid() {
        return msgid;
    }

    public void setMsgid(String msgid) {
        this.msgid = msgid;
    }

    @Override
    public String toString() {
        return "SendMsgResponse{" +
                "errcode=" + getErrcode() +
                ", errmsg='" + getErrmsg() + '\'' +
                ", msgid='" + msgid + '\'' +
                '}';
    }
}
