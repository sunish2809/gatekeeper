package com.project.gatekeeper.management.aop;

import com.project.gatekeeper.management.domain.AuditEntry;
import com.project.gatekeeper.management.repo.AuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.UUID;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditRepository audits;

    public AuditAspect(AuditRepository audits) {
        this.audits = audits;
    }

    @AfterReturning("@annotation(audited)")
    public void record(JoinPoint joinPoint, Audited audited) {
        try {
            AuditEntry entry = new AuditEntry();
            entry.setId(UUID.randomUUID().toString());
            entry.setAction(audited.action());
            entry.setPath(currentRequest());
            entry.setDetail(joinPoint.getSignature().getName());
            entry.setCreatedAt(Instant.now());
            audits.save(entry);
        } catch (RuntimeException ex) {
            log.warn("Admin audit was not saved: {}", ex.getMessage());
        }
    }

    private String currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getMethod() + " " + request.getRequestURI();
    }
}
