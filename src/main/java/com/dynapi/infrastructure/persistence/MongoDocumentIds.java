package com.dynapi.infrastructure.persistence;

import java.util.Map;

public final class MongoDocumentIds {
    private MongoDocumentIds() {
    }

    public static String stringify(Map<String, Object> document) {
        Object id = document == null ? null : document.get("_id");
        return id == null ? null : id.toString();
    }
}
