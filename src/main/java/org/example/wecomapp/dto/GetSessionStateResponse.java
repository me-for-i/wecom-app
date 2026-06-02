package org.example.wecomapp.dto;

/**
 * 获取会话状态接口响应
 *
 * <p>接口地址：POST https://qyapi.weixin.qq.com/cgi-bin/kf/service_state/get</p>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/94670">企业微信客服消息接口文档</a>
 */
public class GetSessionStateResponse extends WecomBaseResponse {

    /**
     * 会话状态
     * <ul>
     *   <li>0: 未处理</li>
     *   <li>1: 由智能助手接待</li>
     *   <li>2: 待接入池排队中</li>
     *   <li>3: 由人工接待</li>
     *   <li>4: 已结束/未开始</li>
     * </ul>
     */
    private Integer service_state;

    /**
     * 接待人员的userid
     */
    private String servicer_userid;

    public Integer getService_state() {
        return service_state;
    }

    public void setService_state(Integer service_state) {
        this.service_state = service_state;
    }

    public String getServicer_userid() {
        return servicer_userid;
    }

    public void setServicer_userid(String servicer_userid) {
        this.servicer_userid = servicer_userid;
    }

    /**
     * 获取会话状态描述
     *
     * @return 会话状态描述文本
     */
    public String getServiceStateDesc() {
        if (service_state == null) {
            return "未知状态";
        }
        switch (service_state) {
            case 0:
                return "未处理";
            case 1:
                return "由智能助手接待";
            case 2:
                return "待接入池排队中";
            case 3:
                return "由人工接待";
            case 4:
                return "已结束/未开始";
            default:
                return "未知状态(" + service_state + ")";
        }
    }

    @Override
    public String toString() {
        return "GetSessionStateResponse{" +
                "errcode=" + getErrcode() +
                ", errmsg='" + getErrmsg() + '\'' +
                ", service_state=" + service_state +
                ", servicer_userid='" + servicer_userid + '\'' +
                '}';
    }
}
