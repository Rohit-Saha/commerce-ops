package com.commerceops.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data, ApiMeta meta) {

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, "OK");
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data, ApiMeta.now(null));
    }

    public static <T> ApiResponse<T> ok(T data, String message, String path) {
        return new ApiResponse<>(true, message, data, ApiMeta.now(path));
    }

    public static ApiResponse<Void> okMessage(String message) {
        return new ApiResponse<>(true, message, null, ApiMeta.now(null));
    }

    public ApiResponse<T> withPath(String path) {
        ApiMeta current = meta != null ? meta : ApiMeta.now(null);
        return new ApiResponse<>(success, message, data, new ApiMeta(current.timestamp(), path));
    }
}
