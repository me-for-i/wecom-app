package org.example.wecomapp.dto;

import java.util.Map;

/**
 * Dify 聊天请求
 *
 * @author dixonyen
 */
public class DifyChatRequest {

    private String query;
    private String user;
    private String responseMode;
    private Map<String, Object> inputs;

    public DifyChatRequest() {
        this.responseMode = "blocking";
        this.inputs = Map.of();
    }

    public DifyChatRequest(String query, String user) {
        this();
        this.query = query;
        this.user = user;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getResponseMode() {
        return responseMode;
    }

    public void setResponseMode(String responseMode) {
        this.responseMode = responseMode;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public void setInputs(Map<String, Object> inputs) {
        this.inputs = inputs;
    }
}
