package org.example.wecomapp.dto;

/**
 * 企业微信接口基础响应
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/94670">企业微信客服消息接口文档</a>
 */
public class WecomBaseResponse {

    /**
     * 错误码，0 表示成功
     */
    private Integer errcode;

    /**
     * 错误信息
     */
    private String errmsg;

    public Integer getErrcode() {
        return errcode;
    }

    public void setErrcode(Integer errcode) {
        this.errcode = errcode;
    }

    public String getErrmsg() {
        return errmsg;
    }

    public void setErrmsg(String errmsg) {
        this.errmsg = errmsg;
    }

    /**
     * 判断请求是否成功
     *
     * @return true 表示成功，false 表示失败
     */
    public boolean isSuccess() {
        return errcode != null && errcode == 0;
    }

    @Override
    public String toString() {
        return "WecomBaseResponse{" +
                "errcode=" + errcode +
                ", errmsg='" + errmsg + '\'' +
                '}';
    }
}
