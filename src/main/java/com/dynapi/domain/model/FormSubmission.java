package com.dynapi.domain.model;

import java.util.Map;

public class FormSubmission {
    private String id;
    private String groupId;
    private Map<String, Object> data;
    private String entity;

    public FormSubmission(String groupId, Map<String, Object> data, String entity) {
        this.groupId = groupId;
        this.data = data;
        this.entity = entity;
    }

    public String getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getEntity() {
        return entity;
    }
}
