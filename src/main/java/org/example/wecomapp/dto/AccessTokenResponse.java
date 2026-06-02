package org.example.wecomapp.dto;

/**
 * 获取 Access Token 接口响应
 *
 * <p>接口地址：GET https://qyapi.weixin.qq.com/cgi-bin/gettoken</p>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/91039">gettoken 接口文档</a>
 */
public class AccessTokenResponse extends WecomBaseResponse {

    /**
     * 获取到的凭证，最长为512字节
     */
    private String access_token;

    /**
     * 凭证的有效时间（秒）
     */
    private Integer expires_in;

    public String getAccess_token() {
        return access_token;
    }

    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    public Integer getExpires_in() {
        return expires_in;
    }

    public void setExpires_in(Integer expires_in) {
        this.expires_in = expires_in;
    }

    @Override
    public String toString() {
        return "AccessTokenResponse{" +
                "errcode=" + getErrcode() +
                ", errmsg='" + getErrmsg() + '\'' +
                ", access_token='" + access_token + '\'' +
                ", expires_in=" + expires_in +
                '}';
    }
}
