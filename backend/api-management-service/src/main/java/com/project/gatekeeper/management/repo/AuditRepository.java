package com.project.gatekeeper.management.repo;

import com.project.gatekeeper.management.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRepository extends JpaRepository<AuditEntry, String> {

    List<AuditEntry> findTop50ByOrderByCreatedAtDesc();
}
