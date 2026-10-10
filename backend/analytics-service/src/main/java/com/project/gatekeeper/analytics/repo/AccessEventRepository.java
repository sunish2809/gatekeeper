package com.project.gatekeeper.analytics.repo;

import com.project.gatekeeper.analytics.domain.AccessEventDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AccessEventRepository extends MongoRepository<AccessEventDocument, String> {

    List<AccessEventDocument> findByClientId(String clientId, Pageable pageable);
}
