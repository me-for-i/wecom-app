package org.example.wecomapp.dto;

/**
 * 会话状态请求对象
 *
 * <p>用于获取会话状态和变更会话状态的请求</p>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/94670">企业微信客服消息接口文档</a>
 */
public class SessionStateRequest {

    /**
     * 客服帐号ID
     */
    private String open_kfid;

    /**
     * 外部客户ID
     */
    private String external_userid;

    /**
     * 会话状态（变更会话状态时必填）
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
     * 接待人员的userid（变更会话状态时必填）
     */
    private String servicer_userid;

    public SessionStateRequest() {
    }

    /**
     * 构造函数（获取会话状态）
     *
     * @param openKfid       客服帐号ID
     * @param externalUserid 外部客户ID
     */
    public SessionStateRequest(String openKfid, String externalUserid) {
        this.open_kfid = openKfid;
        this.external_userid = externalUserid;
    }

    /**
     * 构造函数（变更会话状态）
     *
     * @param openKfid       客服帐号ID
     * @param externalUserid 外部客户ID
     * @param serviceState   会话状态
     * @param servicerUserid 接待人员的userid
     */
    public SessionStateRequest(String openKfid, String externalUserid, Integer serviceState, String servicerUserid) {
        this.open_kfid = openKfid;
        this.external_userid = externalUserid;
        this.service_state = serviceState;
        this.servicer_userid = servicerUserid;
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

    @Override
    public String toString() {
        return "SessionStateRequest{" +
                "open_kfid='" + open_kfid + '\'' +
                ", external_userid='" + external_userid + '\'' +
                ", service_state=" + service_state +
                ", servicer_userid='" + servicer_userid + '\'' +
                '}';
    }
}
