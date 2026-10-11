package com.project.gatekeeper.management.web;

import com.project.gatekeeper.management.aop.Audited;
import com.project.gatekeeper.management.service.OrganizationService;
import com.project.gatekeeper.management.service.OrganizationService.OrganizationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/organizations")
public class PlatformOrganizationController {

    private final OrganizationService organizations;

    public PlatformOrganizationController(OrganizationService organizations) {
        this.organizations = organizations;
    }

    @PostMapping
    @Audited(action = "organization.create")
    public OrganizationResponse create(@Valid @RequestBody CreateRequest request) {
        return organizations.create(new OrganizationService.CreateOrganizationRequest(
                request.name(), request.slug(), request.adminName(), request.adminEmail(), request.adminPassword()));
    }

    @GetMapping
    public List<OrganizationResponse> list(@RequestParam(defaultValue = "false") boolean includeDeleted) {
        return organizations.list(includeDeleted);
    }

    @GetMapping("/{id}")
    public OrganizationResponse get(@PathVariable String id) {
        return organizations.get(id);
    }

    @PostMapping("/{id}/activate")
    @Audited(action = "organization.activate")
    public OrganizationResponse activate(@PathVariable String id) {
        return organizations.activate(id);
    }

    @PostMapping("/{id}/suspend")
    @Audited(action = "organization.suspend")
    public OrganizationResponse suspend(@PathVariable String id) {
        return organizations.suspend(id);
    }

    @DeleteMapping("/{id}")
    @Audited(action = "organization.delete")
    public OrganizationResponse delete(@PathVariable String id) {
        return organizations.softDelete(id);
    }

    public record CreateRequest(
            @NotBlank String name,
            @NotBlank String slug,
            @NotBlank String adminName,
            @NotBlank @Email String adminEmail,
            @NotBlank @Size(min = 8, message = "must be at least 8 characters") String adminPassword
    ) {
    }
}
