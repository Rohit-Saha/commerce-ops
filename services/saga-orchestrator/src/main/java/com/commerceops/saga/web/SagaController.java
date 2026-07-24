package com.commerceops.saga.web;

import com.commerceops.common.web.BusinessException;
import com.commerceops.saga.repository.SagaInstanceRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sagas")
public class SagaController {

    private final SagaInstanceRepository sagaInstanceRepository;

    public SagaController(SagaInstanceRepository sagaInstanceRepository) {
        this.sagaInstanceRepository = sagaInstanceRepository;
    }

    @GetMapping
    public List<SagaResponse> list() {
        return sagaInstanceRepository.findAll().stream()
                .map(SagaResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public SagaResponse getById(@PathVariable Long id) {
        return sagaInstanceRepository.findById(id)
                .map(SagaResponse::from)
                .orElseThrow(() -> BusinessException.notFound("We couldn’t find that saga."));
    }

    @GetMapping("/by-order/{orderId}")
    public SagaResponse getByOrderId(@PathVariable String orderId) {
        return sagaInstanceRepository.findByOrderId(orderId)
                .map(SagaResponse::from)
                .orElseThrow(() -> BusinessException.notFound("We couldn’t find a saga for that order."));
    }
}
