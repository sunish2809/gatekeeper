package com.project.gatekeeper.management.repo;

import com.project.gatekeeper.management.domain.AdminEntity;
import com.project.gatekeeper.management.domain.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<AdminEntity, String> {

    Optional<AdminEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(AdminRole role);

    List<AdminEntity> findByOrganizationId(String organizationId);
}
