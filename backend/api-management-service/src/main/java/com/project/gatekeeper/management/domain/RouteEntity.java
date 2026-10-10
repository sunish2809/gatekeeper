package com.project.gatekeeper.management.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "gateway_route", indexes = @Index(name = "idx_route_client", columnList = "clientId"))
@Getter
@Setter
@NoArgsConstructor
public class RouteEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String publicPath;

    @Column(nullable = false, length = 1000)
    private String targetUrl;

    @Column(nullable = false)
    private String methods;

    @Column(nullable = false)
    private int timeoutMs;

    @Column(nullable = false)
    private boolean authRequired;

    private String allowedRoles;

    @Column(nullable = false)
    private int rateLimit;

    @Column(nullable = false)
    private int rateWindowSeconds;

    @Column(nullable = false)
    private boolean idempotentPost;

    @Column(nullable = false)
    private boolean stripPrefix;

    @Column(nullable = false, columnDefinition = "bit(1) not null default 0")
    private boolean requireUserJwt;

    @Column(length = 4000)
    private String requestHeaders;

    @Column(length = 2000)
    private String allowedOrigins;

    @Column(length = 500)
    private String oidcIssuer;

    @Column(length = 500)
    private String oidcAudience;

    @Column(length = 1000)
    private String requiredScopes;

    @Column(nullable = false)
    private boolean enabled;

    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
