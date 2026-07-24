package com.commerceops.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({ApiResponseBodyAdvice.class, ApiExceptionHandler.class})
public class CommonWebAutoConfiguration {
}
