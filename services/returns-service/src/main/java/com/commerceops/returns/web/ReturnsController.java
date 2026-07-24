package com.commerceops.returns.web;

import com.commerceops.returns.web.dto.ReturnsHealthMessageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 2 stub only. Returns no data yet -- see the module README for the
 * planned RMA -> restock -> refund saga that will replace this controller.
 */
@RestController
@RequestMapping("/api/returns")
public class ReturnsController {

    @GetMapping
    public List<Object> list() {
        return List.of();
    }

    @GetMapping("/health-message")
    public ReturnsHealthMessageResponse healthMessage() {
        return ReturnsHealthMessageResponse.stub();
    }
}
