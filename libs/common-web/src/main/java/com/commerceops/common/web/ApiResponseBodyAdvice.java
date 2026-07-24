package com.commerceops.common.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;

@RestControllerAdvice
public class ApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Method method = returnType.getMethod();
        if (method == null) {
            return false;
        }
        Class<?> containing = returnType.getContainingClass();
        if (containing.isAnnotationPresent(RawResponse.class) || method.isAnnotationPresent(RawResponse.class)) {
            return false;
        }
        // Actuator endpoints live under org.springframework.boot.actuate
        String packageName = containing.getPackageName();
        if (packageName.startsWith("org.springframework.boot.actuate")) {
            return false;
        }
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body == null) {
            return null;
        }
        if (shouldSkip(body, selectedContentType)) {
            return body;
        }

        String path = requestPath(request);
        if (body instanceof ApiResponse<?> apiResponse) {
            if (apiResponse.meta() == null || apiResponse.meta().path() == null) {
                return apiResponse.withPath(path);
            }
            return apiResponse;
        }

        // Proxied JsonNode that is already { success, ... } — leave unchanged (no double-wrap).
        if (body instanceof JsonNode node && isStandardEnvelope(node)) {
            return body;
        }

        String message = resolveMessage(returnType, response);
        return ApiResponse.ok(body, message, path);
    }

    private static boolean shouldSkip(Object body, MediaType contentType) {
        if (body instanceof ApiError
                || body instanceof byte[]
                || body instanceof Resource
                || body instanceof SseEmitter) {
            return true;
        }
        if (contentType != null) {
            if (MediaType.TEXT_EVENT_STREAM.includes(contentType)
                    || MediaType.APPLICATION_OCTET_STREAM.includes(contentType)
                    || MediaType.APPLICATION_PDF.includes(contentType)) {
                return true;
            }
        }
        return false;
    }

    /** Success or error envelope from common-web / downstream. */
    static boolean isStandardEnvelope(JsonNode node) {
        return node != null && node.isObject() && node.has("success") && node.get("success").isBoolean();
    }

    private static String resolveMessage(MethodParameter returnType, ServerHttpResponse response) {
        Method method = returnType.getMethod();
        if (method != null) {
            ApiMessage annotation = method.getAnnotation(ApiMessage.class);
            if (annotation != null && !annotation.value().isBlank()) {
                return annotation.value();
            }
        }
        int status = response instanceof ServletServerHttpResponse servlet
                ? servlet.getServletResponse().getStatus()
                : 200;
        if (status == 201) {
            return "Created";
        }
        return "OK";
    }

    private static String requestPath(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getRequestURI();
        }
        return request.getURI().getPath();
    }
}
