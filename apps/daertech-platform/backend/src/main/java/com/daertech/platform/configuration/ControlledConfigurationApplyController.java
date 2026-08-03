package com.daertech.platform.configuration;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/configuration-apply")
public class ControlledConfigurationApplyController {
    private final ControlledConfigurationApplyService service;

    public ControlledConfigurationApplyController(ControlledConfigurationApplyService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CONFIG_WRITE')")
    public Map<String, Object> apply(@RequestBody ControlledConfigurationApplyService.ApplyRequest request, Authentication authentication) {
        return service.apply(request, authentication.getName());
    }

    @PostMapping("/{operationId}/rollback")
    @PreAuthorize("hasAuthority('CONFIG_WRITE')")
    public Map<String, Object> rollback(@PathVariable UUID operationId, @RequestBody Map<String, String> body, Authentication authentication) {
        return service.rollback(operationId, authentication.getName(), body.getOrDefault("reason", "Rollback manual"));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('CONFIG_READ')")
    public List<Map<String, Object>> history() {
        return service.history();
    }
}
