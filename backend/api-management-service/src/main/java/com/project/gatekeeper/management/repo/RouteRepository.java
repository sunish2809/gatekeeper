package com.project.gatekeeper.management.repo;

import com.project.gatekeeper.management.domain.RouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<RouteEntity, String> {

    List<RouteEntity> findByClientId(String clientId);

    Optional<RouteEntity> findByPublicPath(String publicPath);

    void deleteByClientId(String clientId);
}
