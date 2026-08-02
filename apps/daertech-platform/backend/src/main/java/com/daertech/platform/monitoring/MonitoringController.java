package com.daertech.platform.monitoring;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/monitoring")
public class MonitoringController {
    private final MonitoringService service;
    public MonitoringController(MonitoringService service){this.service=service;}

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('MONITORING_READ')")
    public Map<String,Object> dashboard(){return service.dashboard();}

    @GetMapping("/targets")
    @PreAuthorize("hasAuthority('MONITORING_READ')")
    public List<Map<String,Object>> targets(@RequestParam(required=false) String environment){return service.targets(environment);}

    @PostMapping("/targets")
    @PreAuthorize("hasAuthority('MONITORING_WRITE')")
    public Map<String,Object> save(@RequestBody MonitoringService.Request request){return service.save(request);}

    @PostMapping("/targets/{id}/check")
    @PreAuthorize("hasAuthority('MONITORING_EXECUTE')")
    public Map<String,Object> check(@PathVariable UUID id){return service.check(id);}
}