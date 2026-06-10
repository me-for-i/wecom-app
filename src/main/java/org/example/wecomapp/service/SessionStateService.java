package org.example.wecomapp.service;

import org.example.wecomapp.client.WecomApiClient;
import org.example.wecomapp.constants.WecomConstants;
import org.example.wecomapp.dto.GetSessionStateResponse;
import org.example.wecomapp.dto.SessionStateRequest;
import org.example.wecomapp.dto.TransSessionStateResponse;
import org.springframework.stereotype.Service;

/**
 * 会话状态服务
 *
 * <p>负责管理企业微信客服会话状态，包括：</p>
 * <ul>
 *   <li>获取会话状态</li>
 *   <li>变更会话状态</li>
 *   <li>会话状态流转控制</li>
 * </ul>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/94670">企业微信客服消息接口文档</a>
 */
@Service
public class SessionStateService {

    private final WecomApiClient wecomApiClient;

    /**
     * 构造函数
     *
     * @param wecomApiClient 企业微信 API 客户端
     */
    public SessionStateService(WecomApiClient wecomApiClient) {
        this.wecomApiClient = wecomApiClient;
    }

    /**
     * 获取会话状态
     *
     * @param openKfid       客服帐号ID
     * @param externalUserid 外部客户ID
     * @return 会话状态响应
     */
    public GetSessionStateResponse getSessionState(String openKfid, String externalUserid) {
        System.out.println("\n---------- 获取会话状态 ----------");
        System.out.println("  openKfid: " + openKfid);
        System.out.println("  externalUserid: " + externalUserid);

        GetSessionStateResponse response = wecomApiClient.getSessionState(openKfid, externalUserid);

        System.out.println("  结果:");
        System.out.println("    errcode: " + response.getErrcode());
        System.out.println("    errmsg: " + response.getErrmsg());
        System.out.println("    service_state: " + response.getService_state() + " (" + response.getServiceStateDesc() + ")");
        System.out.println("    servicer_userid: " + response.getServicer_userid());

        return response;
    }

    /**
     * 变更会话状态
     *
     * @param openKfid       客服帐号ID
     * @param externalUserid 外部客户ID
     * @param serviceState   目标会话状态
     * @param servicerUserid 接待人员的userid
     * @return 变更结果响应
     */
    public TransSessionStateResponse transSessionState(String openKfid, String externalUserid,
                                                        Integer serviceState, String servicerUserid) {
        System.out.println("\n---------- 变更会话状态 ----------");
        System.out.println("  openKfid: " + openKfid);
        System.out.println("  externalUserid: " + externalUserid);
        System.out.println("  目标状态: " + serviceState + " (" + WecomConstants.ServiceState.getDesc(serviceState) + ")");
        System.out.println("  接待人员: " + (servicerUserid != null ? servicerUserid : "（不指定）"));

        SessionStateRequest request = new SessionStateRequest(openKfid, externalUserid, serviceState, servicerUserid);
        TransSessionStateResponse response = wecomApiClient.transSessionState(request);

        System.out.println("  变更结果:");
        System.out.println("    errcode: " + response.getErrcode());
        System.out.println("    errmsg: " + response.getErrmsg());
        if (response.getMsg_code() != null && !response.getMsg_code().isEmpty()) {
            System.out.println("    msg_code: " + response.getMsg_code());
        }

        return response;
    }

    /**
     * 转接给人工接待
     *
     * <p>将会话状态从当前状态转为由人工接待</p>
     *
     * @param openKfid       客服帐号ID
     * @param externalUserid 外部客户ID
     * @param servicerUserid 接待人员的userid
     * @return 变更结果响应
     */
    public TransSessionStateResponse transferToManualService(String openKfid, String externalUserid,
                                                              String servicerUserid) {
        return transSessionState(openKfid, externalUserid, WecomConstants.ServiceState.MANUAL_SERVICE, servicerUserid);
    }

    /**
     * 转入待接入池
     *
     * <p>将会话状态从当前状态转为待接入池排队中</p>
     *
     * @param openKfid       客服帐号ID
     * @param externalUserid 外部客户ID
     * @return 变更结果响应
     */
    public TransSessionStateResponse transferToWaitingPool(String openKfid, String externalUserid) {
        return transSessionState(openKfid, externalUserid, WecomConstants.ServiceState.WAITING, null);
    }

    /**
     * 结束会话
     *
     * <p>将会话状态从当前状态转为已结束</p>
     *
     * @param openKfid       客服帐号ID
     * @param externalUserid 外部客户ID
     * @return 变更结果响应
     */
    public TransSessionStateResponse finishSession(String openKfid, String externalUserid) {
        return transSessionState(openKfid, externalUserid, WecomConstants.ServiceState.FINISHED, null);
    }

    /**
     * 转为智能助手接待
     *
     * <p>将会话状态从当前状态转为由智能助手接待</p>
     *
     * @param openKfid       客服帐号ID
     * @param externalUserid 外部客户ID
     */
    public void transferToBotService(String openKfid, String externalUserid) {
        transSessionState(openKfid, externalUserid, WecomConstants.ServiceState.BOT_SERVICE, null);
    }
}
