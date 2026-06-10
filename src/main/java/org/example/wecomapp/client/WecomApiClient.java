package org.example.wecomapp.client;

import org.example.wecomapp.config.WecomProperties;
import org.example.wecomapp.dto.SyncMsgResponse;
import org.example.wecomapp.dto.SendMsgResponse;
import org.example.wecomapp.dto.GetSessionStateResponse;
import org.example.wecomapp.dto.TransSessionStateResponse;
import org.example.wecomapp.dto.SessionStateRequest;
import org.example.wecomapp.service.AccessTokenService;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 企业微信 API 客户端
 *
 * <p>封装企业微信客服消息相关接口的调用</p>
 *
 * @author dixonyen
 * @see <a href="https://developer.work.weixin.qq.com/document/path/94670">企业微信客服消息接口文档</a>
 */
@Component
public class WecomApiClient {

    private final RestClient restClient;
    private final WecomProperties properties;
    private final AccessTokenService accessTokenService;

    /**
     * 构造函数
     *
     * @param restClient         Spring RestClient 实例
     * @param properties         企业微信配置属性
     * @param accessTokenService Access Token 服务
     */
    public WecomApiClient(RestClient restClient, WecomProperties properties, AccessTokenService accessTokenService) {
        this.restClient = restClient;
        this.properties = properties;
        this.accessTokenService = accessTokenService;
    }

    /**
     * 获取客服消息列表
     *
     * <p>调用企业微信 sync_msg 接口，获取客服账号的聊天消息记录</p>
     *
     * <p>接口地址：POST https://qyapi.weixin.qq.com/cgi-bin/kf/sync_msg</p>
     *
     * @param token    回调事件返回的 Token，用于拉取该 Token 对应的消息
     * @param openKfid 客服帐号ID
     * @param cursor   上一次调用时返回的 next_cursor，第一次拉取可以不填
     * @param limit    期望拉取的消息条数，取值范围 1~1000
     * @return 消息列表响应，包含 msg_list、next_cursor、has_more 等字段
     * @see SyncMsgResponse
     */
    public SyncMsgResponse syncMsg(String token, String openKfid, String cursor, int limit) {
        System.out.println("\n========== [企业微信 API] sync_msg ==========");
        System.out.println("  cursor: " + (cursor != null && !cursor.isEmpty() ? cursor : "首次拉取"));
        System.out.println("  limit: " + limit);

        JSONObject body = new JSONObject();
        body.put("token", token);
        body.put("open_kfid", openKfid);
        body.put("cursor", cursor != null ? cursor : "");
        body.put("limit", limit);
        body.put("voice_format", 0);

        String accessToken = accessTokenService.getAccessToken();
        String response = restClient.post()
                .uri(properties.getSyncMsgUrl() + "?access_token=" + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);

        JSONObject json = new JSONObject(response);
        SyncMsgResponse result = new SyncMsgResponse();
        result.setErrcode(json.optInt("errcode", -1));
        result.setErrmsg(json.optString("errmsg", ""));
        result.setNext_cursor(json.optString("next_cursor", ""));
        result.setHas_more(json.optInt("has_more", 0));

        // 解析 msg_list
        if (json.has("msg_list") && !json.isNull("msg_list")) {
            var msgArray = json.getJSONArray("msg_list");
            java.util.List<SyncMsgResponse.MsgItem> msgList = new java.util.ArrayList<>();
            for (int i = 0; i < msgArray.length(); i++) {
                JSONObject msgJson = msgArray.getJSONObject(i);
                SyncMsgResponse.MsgItem item = new SyncMsgResponse.MsgItem();
                item.setMsgid(msgJson.optString("msgid", ""));
                item.setOpen_kfid(msgJson.optString("open_kfid", ""));
                item.setExternal_userid(msgJson.optString("external_userid", ""));
                item.setSend_time(msgJson.optLong("send_time", 0));
                item.setOrigin(msgJson.optInt("origin", 0));
                item.setServicer_userid(msgJson.optString("servicer_userid", ""));
                item.setMsgtype(msgJson.optString("msgtype", ""));

                // 解析各消息类型内容
                parseMsgContent(msgJson, item);

                msgList.add(item);
            }
            result.setMsg_list(msgList);
        }

        return result;
    }

    /**
     * 解析消息内容
     */
    private void parseMsgContent(JSONObject msgJson, SyncMsgResponse.MsgItem item) {
        String msgtype = item.getMsgtype();

        switch (msgtype) {
            case "text":
                JSONObject textObj = msgJson.optJSONObject("text");
                if (textObj != null) {
                    SyncMsgResponse.TextContent text = new SyncMsgResponse.TextContent();
                    text.setContent(textObj.optString("content", ""));
                    item.setText(text);
                }
                break;

            case "image":
                JSONObject imageObj = msgJson.optJSONObject("image");
                if (imageObj != null) {
                    SyncMsgResponse.ImageContent image = new SyncMsgResponse.ImageContent();
                    image.setMedia_id(imageObj.optString("media_id", ""));
                    item.setImage(image);
                }
                break;

            case "voice":
                JSONObject voiceObj = msgJson.optJSONObject("voice");
                if (voiceObj != null) {
                    SyncMsgResponse.VoiceContent voice = new SyncMsgResponse.VoiceContent();
                    voice.setMedia_id(voiceObj.optString("media_id", ""));
                    item.setVoice(voice);
                }
                break;

            case "video":
                JSONObject videoObj = msgJson.optJSONObject("video");
                if (videoObj != null) {
                    SyncMsgResponse.VideoContent video = new SyncMsgResponse.VideoContent();
                    video.setMedia_id(videoObj.optString("media_id", ""));
                    item.setVideo(video);
                }
                break;

            case "file":
                JSONObject fileObj = msgJson.optJSONObject("file");
                if (fileObj != null) {
                    SyncMsgResponse.FileContent file = new SyncMsgResponse.FileContent();
                    file.setMedia_id(fileObj.optString("media_id", ""));
                    item.setFile(file);
                }
                break;

            case "link":
                JSONObject linkObj = msgJson.optJSONObject("link");
                if (linkObj != null) {
                    SyncMsgResponse.LinkContent link = new SyncMsgResponse.LinkContent();
                    link.setTitle(linkObj.optString("title", ""));
                    link.setDesc(linkObj.optString("desc", ""));
                    link.setUrl(linkObj.optString("url", ""));
                    link.setPic_url(linkObj.optString("pic_url", ""));
                    item.setLink(link);
                }
                break;

            case "miniprogram":
                JSONObject miniObj = msgJson.optJSONObject("miniprogram");
                if (miniObj != null) {
                    SyncMsgResponse.MiniprogramContent mini = new SyncMsgResponse.MiniprogramContent();
                    mini.setAppid(miniObj.optString("appid", ""));
                    mini.setTitle(miniObj.optString("title", ""));
                    mini.setPic_media_id(miniObj.optString("pic_media_id", ""));
                    mini.setPage(miniObj.optString("page", ""));
                    item.setMiniprogram(mini);
                }
                break;
        }
    }

    /**
     * 发送客服消息给用户
     *
     * <p>调用企业微信 send_msg 接口，发送客服消息给指定用户</p>
     *
     * <p>接口地址：POST https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg</p>
     *
     * <p>支持的消息类型：</p>
     * <ul>
     *   <li>text: 文本消息</li>
     *   <li>image: 图片消息</li>
     *   <li>voice: 语音消息</li>
     *   <li>video: 视频消息</li>
     *   <li>file: 文件消息</li>
     *   <li>link: 图文链接消息</li>
     *   <li>miniprogram: 小程序消息</li>
     * </ul>
     *
     * @param touser   外部用户ID (external_userid)
     * @param openKfid 客服账号ID
     * @param msgtype  消息类型，如 text, image, voice 等
     * @param content  消息内容，根据 msgtype 构造对应的 JSON 对象
     * @return 发送结果响应，包含 errcode、errmsg、msgid 等字段
     * @see SendMsgResponse
     */
    public SendMsgResponse sendMsg(String touser, String openKfid, String msgtype, JSONObject content) {
        System.out.println("\n========== [企业微信 API] send_msg ==========");
        System.out.println("  touser: " + touser);
        System.out.println("  msgtype: " + msgtype);

        JSONObject body = new JSONObject();
        body.put("touser", touser);
        body.put("open_kfid", openKfid);
        body.put("msgtype", msgtype);

        // 根据消息类型设置对应的内容字段
        body.put(msgtype, content);

        String accessToken = accessTokenService.getAccessToken();
        String response = restClient.post()
                .uri(properties.getSendMsgUrl() + "?access_token=" + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);

        JSONObject json = new JSONObject(response);
        SendMsgResponse result = new SendMsgResponse();
        result.setErrcode(json.optInt("errcode", -1));
        result.setErrmsg(json.optString("errmsg", ""));
        result.setMsgid(json.optString("msgid", ""));

        System.out.println("  结果: errcode=" + result.getErrcode() + ", errmsg=" + result.getErrmsg());
        if (result.getMsgid() != null && !result.getMsgid().isEmpty()) {
            System.out.println("        msgid=" + result.getMsgid());
        }

        return result;
    }

    /**
     * 获取会话状态
     *
     * <p>调用企业微信 service_state/get 接口，获取会话的当前状态</p>
     *
     * <p>接口地址：POST https://qyapi.weixin.qq.com/cgi-bin/kf/service_state/get</p>
     *
     * @param openKfid       客服帐号ID
     * @param externalUserid 外部客户ID
     * @return 会话状态响应，包含 service_state、servicer_userid 等字段
     * @see GetSessionStateResponse
     */
    public GetSessionStateResponse getSessionState(String openKfid, String externalUserid) {
        System.out.println("\n========== [企业微信 API] service_state/get ==========");
        System.out.println("  openKfid: " + openKfid);
        System.out.println("  externalUserid: " + externalUserid);

        JSONObject body = new JSONObject();
        body.put("open_kfid", openKfid);
        body.put("external_userid", externalUserid);

        String accessToken = accessTokenService.getAccessToken();
        String response = restClient.post()
                .uri(properties.getGetSessionStateUrl() + "?access_token=" + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);

        JSONObject json = new JSONObject(response);
        GetSessionStateResponse result = new GetSessionStateResponse();
        result.setErrcode(json.optInt("errcode", -1));
        result.setErrmsg(json.optString("errmsg", ""));
        result.setService_state(json.optInt("service_state", -1));
        result.setServicer_userid(json.optString("servicer_userid", ""));

        return result;
    }

    /**
     * 变更会话状态
     *
     * <p>调用企业微信 service_state/trans 接口，变更会话的当前状态</p>
     *
     * <p>接口地址：POST https://qyapi.weixin.qq.com/cgi-bin/kf/service_state/trans</p>
     *
     * @param request 会话状态变更请求
     * @return 变更结果响应，包含 errcode、errmsg、msg_code 等字段
     * @see TransSessionStateResponse
     */
    public TransSessionStateResponse transSessionState(SessionStateRequest request) {
        System.out.println("\n========== [企业微信 API] service_state/trans ==========");
        System.out.println("  openKfid: " + request.getOpen_kfid());
        System.out.println("  externalUserid: " + request.getExternal_userid());
        System.out.println("  目标状态: " + request.getService_state());

        JSONObject body = new JSONObject();
        body.put("open_kfid", request.getOpen_kfid());
        body.put("external_userid", request.getExternal_userid());
        body.put("service_state", request.getService_state());
        body.put("servicer_userid", request.getServicer_userid());

        String accessToken = accessTokenService.getAccessToken();
        String response = restClient.post()
                .uri(properties.getTransSessionStateUrl() + "?access_token=" + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve()
                .body(String.class);

        JSONObject json = new JSONObject(response);
        TransSessionStateResponse result = new TransSessionStateResponse();
        result.setErrcode(json.optInt("errcode", -1));
        result.setErrmsg(json.optString("errmsg", ""));
        result.setMsg_code(json.optString("msg_code", ""));

        System.out.println("  结果: errcode=" + result.getErrcode() + ", errmsg=" + result.getErrmsg());

        return result;
    }
}
