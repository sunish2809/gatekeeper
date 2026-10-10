package com.project.gatekeeper.management.repo;

import com.project.gatekeeper.management.domain.ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<ClientEntity, String> {

    List<ClientEntity> findByAdminId(String adminId);

    Optional<ClientEntity> findByApiKey(String apiKey);

    boolean existsByApiKey(String apiKey);
}
