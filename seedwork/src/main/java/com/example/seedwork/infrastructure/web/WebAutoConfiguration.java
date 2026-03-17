package com.example.seedwork.infrastructure.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Registers the seedwork {@link GlobalExceptionHandler} for any servlet-based
 * Spring MVC application that includes seedwork on its classpath.
 *
 * <p>The handler runs at {@code Ordered.LOWEST_PRECEDENCE}, so service-specific
 * {@code @RestControllerAdvice} beans always take priority for their own exception types.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
public class WebAutoConfiguration {

    @Bean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
