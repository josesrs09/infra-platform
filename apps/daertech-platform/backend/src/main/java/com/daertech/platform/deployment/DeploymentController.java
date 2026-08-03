package com.daertech.platform.deployment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/deployments")
public class DeploymentController {
    private final DeploymentService service;
    public DeploymentController(DeploymentService service){this.service=service;}

    @GetMapping
    @PreAuthorize("hasAuthority('DEPLOYMENT_READ')")
    public List<Map<String,Object>> list(@RequestParam(required=false) UUID applicationId,
                                         @RequestParam(required=false) String environment){
        return service.list(applicationId, environment);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('DEPLOYMENT_READ')")
    public Map<String,Object> detail(@PathVariable UUID id){return service.detail(id);}

    @PostMapping
    @PreAuthorize("hasAuthority('DEPLOYMENT_EXECUTE')")
    public Map<String,Object> create(@RequestBody DeploymentService.Request request, Authentication authentication){
        return service.create(request, authentication.getName());
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('DEPLOYMENT_EXECUTE')")
    public Map<String,Object> execute(@PathVariable UUID id){return service.execute(id);}

    @PostMapping("/{id}/rollback")
    @PreAuthorize("hasAuthority('DEPLOYMENT_ROLLBACK')")
    public Map<String,Object> rollback(@PathVariable UUID id,
                                       @RequestBody(required=false) Map<String,String> body,
                                       Authentication authentication){
        return service.rollback(id, authentication.getName(), body==null?"Rollback manual":body.getOrDefault("reason","Rollback manual"));
    }
}
