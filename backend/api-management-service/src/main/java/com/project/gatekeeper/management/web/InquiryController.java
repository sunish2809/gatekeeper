package com.project.gatekeeper.management.web;

import com.project.gatekeeper.management.domain.InquiryEntity;
import com.project.gatekeeper.management.repo.InquiryRepository;
import com.project.gatekeeper.management.security.CurrentAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class InquiryController {

    private final InquiryRepository inquiries;
    private final CurrentAdmin currentAdmin;

    public InquiryController(InquiryRepository inquiries, CurrentAdmin currentAdmin) {
        this.inquiries = inquiries;
        this.currentAdmin = currentAdmin;
    }

    @PostMapping("/api/inquiries")
    public InquiryResponse create(@Valid @RequestBody CreateInquiryRequest request) {
        InquiryEntity inquiry = new InquiryEntity();
        inquiry.setId(UUID.randomUUID().toString());
        inquiry.setName(request.name().trim());
        inquiry.setCompany(request.company().trim());
        inquiry.setEmail(request.email().trim().toLowerCase());
        inquiry.setWebsite(blankToNull(request.website()));
        inquiry.setMessage(request.message().trim());
        inquiries.save(inquiry);
        return toResponse(inquiry);
    }

    @GetMapping("/api/platform/inquiries")
    public List<InquiryResponse> list() {
        currentAdmin.requirePlatform();
        return inquiries.findTop50ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    private InquiryResponse toResponse(InquiryEntity inquiry) {
        return new InquiryResponse(
                inquiry.getId(),
                inquiry.getName(),
                inquiry.getCompany(),
                inquiry.getEmail(),
                inquiry.getWebsite(),
                inquiry.getMessage(),
                inquiry.getCreatedAt());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateInquiryRequest(
            @NotBlank String name,
            @NotBlank String company,
            @NotBlank @Email String email,
            String website,
            @NotBlank @Size(max = 4000) String message
    ) {
    }

    public record InquiryResponse(
            String id,
            String name,
            String company,
            String email,
            String website,
            String message,
            Instant createdAt
    ) {
    }
}
