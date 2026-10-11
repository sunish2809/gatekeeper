package com.project.gatekeeper.management.config;

import com.project.gatekeeper.management.service.DemoTenantService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(200)
public class DemoTenantBootstrap implements ApplicationRunner {

    private final DemoTenantService demo;

    public DemoTenantBootstrap(DemoTenantService demo) {
        this.demo = demo;
    }

    @Override
    public void run(ApplicationArguments args) {
        demo.ensurePresent();
    }
}
