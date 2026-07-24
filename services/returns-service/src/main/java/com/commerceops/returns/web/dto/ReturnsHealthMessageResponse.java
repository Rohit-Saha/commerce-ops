package com.commerceops.returns.web.dto;

public record ReturnsHealthMessageResponse(String status, int phase, String message) {

    private static final String STUB_MESSAGE =
            "Returns service scaffolded for Phase 2 (RMA -> restock -> refund saga)";

    public static ReturnsHealthMessageResponse stub() {
        return new ReturnsHealthMessageResponse("STUB", 2, STUB_MESSAGE);
    }
}
