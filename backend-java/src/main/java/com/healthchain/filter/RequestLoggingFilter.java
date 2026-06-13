package com.healthchain.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Request logging filter - replaces Python's @app.before_request log_request_info()
 * Logs: DEBUG: METHOD PATH - Headers: {...}
 */
@Component
@Order(1)
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        // Build headers map
        String headers = Collections.list(httpRequest.getHeaderNames()).stream()
                .collect(Collectors.toMap(
                        name -> name,
                        httpRequest::getHeader
                )).toString();

        System.out.println("DEBUG: " + httpRequest.getMethod() + " " + httpRequest.getRequestURI()
                + " - Headers: " + headers);

        chain.doFilter(request, response);
    }
}
