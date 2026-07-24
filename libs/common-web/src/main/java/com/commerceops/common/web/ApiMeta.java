package com.commerceops.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiMeta(Instant timestamp, String path) {

    public static ApiMeta now(String path) {
        return new ApiMeta(Instant.now(), path);
    }
}
