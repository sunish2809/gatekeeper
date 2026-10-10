package com.project.gatekeeper.management.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RequestTimingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestTimingInterceptor.class);
    private static final String STARTED = RequestTimingInterceptor.class.getName() + ".started";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(STARTED, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object started = request.getAttribute(STARTED);
        if (!(started instanceof Long startedAt)) {
            return;
        }
        long tookMs = (System.nanoTime() - startedAt) / 1_000_000;
        String target = handler instanceof HandlerMethod method
                ? method.getBeanType().getSimpleName() + "." + method.getMethod().getName()
                : "-";
        log.info("{} {} -> {} in {} ms ({})", request.getMethod(), request.getRequestURI(), response.getStatus(), tookMs, target);
    }
}
