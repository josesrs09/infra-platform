package com.daertech.platform.deployment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/deployment-runtime")
public class DeploymentRuntimeController {
    private final DeploymentRuntimeService service;

    public DeploymentRuntimeController(DeploymentRuntimeService service){this.service=service;}

    @GetMapping("/{deploymentId}/events")
    @PreAuthorize("hasAuthority('DEPLOYMENT_READ')")
    public List<Map<String,Object>> events(@PathVariable UUID deploymentId){return service.events(deploymentId);}

    @PostMapping("/{deploymentId}/push")
    @PreAuthorize("hasAuthority('DEPLOYMENT_REGISTRY_PUSH')")
    public Map<String,Object> push(@PathVariable UUID deploymentId, Authentication authentication){
        return service.pushImage(deploymentId,authentication.getName());
    }

    @PostMapping("/{deploymentId}/switch-traffic")
    @PreAuthorize("hasAuthority('DEPLOYMENT_TRAFFIC_SWITCH')")
    public Map<String,Object> switchTraffic(@PathVariable UUID deploymentId,
                                            @RequestBody SwitchRequest request,
                                            Authentication authentication){
        return service.switchTraffic(deploymentId,request.targetSlot(),authentication.getName());
    }

    public record SwitchRequest(String targetSlot){}
}