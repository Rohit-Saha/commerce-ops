package com.commerceops.gateway.web;

import com.commerceops.common.web.RawResponse;

import com.commerceops.gateway.service.SseBroadcastService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RawResponse
@RestController
@RequestMapping("/api/stream")
public class OrderStreamController {

    private final SseBroadcastService broadcastService;

    public OrderStreamController(SseBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @GetMapping(path = "/orders", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrders() {
        return broadcastService.subscribe();
    }
}
