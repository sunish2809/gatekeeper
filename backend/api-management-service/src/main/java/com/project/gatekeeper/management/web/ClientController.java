package com.project.gatekeeper.management.web;

import com.project.gatekeeper.management.aop.Audited;
import com.project.gatekeeper.management.api.CatalogDtos.ClientResponse;
import com.project.gatekeeper.management.api.CatalogDtos.CreateClientRequest;
import com.project.gatekeeper.management.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final CatalogService catalog;

    public ClientController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @PostMapping
    @Audited(action = "client.create")
    public ClientResponse create(@Valid @RequestBody CreateClientRequest request) {
        return catalog.createClient(request);
    }

    @GetMapping
    public List<ClientResponse> list() {
        return catalog.listClients();
    }

    @GetMapping("/{id}")
    public ClientResponse get(@PathVariable String id) {
        return catalog.getClient(id);
    }

    @DeleteMapping("/{id}")
    @Audited(action = "client.delete")
    public void delete(@PathVariable String id) {
        catalog.deleteClient(id);
    }
}
