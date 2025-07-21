package com.example.dynapi.interfaces.rest.dto;

import java.util.Map;

public class FormSubmissionRequest {
    private String groupId;
    private Map<String, Object> data;

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
