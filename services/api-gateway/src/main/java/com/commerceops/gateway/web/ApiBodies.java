package com.commerceops.gateway.web;

import com.fasterxml.jackson.databind.JsonNode;

final class ApiBodies {

    private ApiBodies() {
    }

    /** Unwrap standardized {@code ApiResponse.data} when present; otherwise return the node as-is. */
    static JsonNode data(JsonNode body) {
        if (body != null && body.isObject() && body.path("success").isBoolean() && body.has("data")) {
            return body.get("data");
        }
        return body;
    }
}
