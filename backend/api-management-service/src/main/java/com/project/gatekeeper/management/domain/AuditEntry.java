package com.project.gatekeeper.management.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "admin_audit")
@Getter
@Setter
@NoArgsConstructor
public class AuditEntry {

    @Id
    private String id;

    @Column(nullable = false)
    private String action;

    private String path;

    @Column(length = 2000)
    private String detail;

    private Instant createdAt;
}
