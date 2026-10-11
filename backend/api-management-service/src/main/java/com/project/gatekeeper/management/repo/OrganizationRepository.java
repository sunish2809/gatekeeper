package com.project.gatekeeper.management.repo;

import com.project.gatekeeper.management.domain.OrganizationEntity;
import com.project.gatekeeper.management.domain.OrganizationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, String> {

    Optional<OrganizationEntity> findBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndStatusNot(String slug, OrganizationStatus status);

    List<OrganizationEntity> findByStatusNotOrderByCreatedAtDesc(OrganizationStatus status);

    List<OrganizationEntity> findAllByOrderByCreatedAtDesc();
}
