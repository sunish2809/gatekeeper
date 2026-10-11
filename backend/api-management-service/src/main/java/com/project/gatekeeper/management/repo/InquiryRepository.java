package com.project.gatekeeper.management.repo;

import com.project.gatekeeper.management.domain.InquiryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryRepository extends JpaRepository<InquiryEntity, String> {

    List<InquiryEntity> findTop50ByOrderByCreatedAtDesc();
}
