package com.daertech.platform.application;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/admin/applications")
public class ApplicationController {
    private final ApplicationService service;
    public ApplicationController(ApplicationService service){this.service=service;}

    @GetMapping @PreAuthorize("hasAuthority('APPLICATION_READ')")
    public List<Map<String,Object>> list(){return service.list();}

    @GetMapping("/{id}") @PreAuthorize("hasAuthority('APPLICATION_READ')")
    public Map<String,Object> find(@PathVariable UUID id){return service.find(id);}

    @PostMapping @PreAuthorize("hasAuthority('APPLICATION_WRITE')")
    public Map<String,Object> save(@RequestBody ApplicationService.ApplicationRequest request, Authentication auth){return service.save(request,auth.getName());}

    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('APPLICATION_WRITE')")
    public void delete(@PathVariable UUID id){service.delete(id);}

    @PostMapping("/{id}/environments") @PreAuthorize("hasAuthority('APPLICATION_WRITE')")
    public Map<String,Object> environment(@PathVariable UUID id,@RequestBody ApplicationService.EnvironmentRequest request){return service.saveEnvironment(id,request);}

    @PostMapping("/{id}/variables") @PreAuthorize("hasAuthority('APPLICATION_WRITE')")
    public Map<String,Object> variable(@PathVariable UUID id,@RequestBody ApplicationService.VariableRequest request){return service.saveVariable(id,request);}

    @PostMapping("/{id}/versions") @PreAuthorize("hasAuthority('APPLICATION_WRITE')")
    public Map<String,Object> version(@PathVariable UUID id,@RequestBody ApplicationService.VersionRequest request,Authentication auth){return service.addVersion(id,request,auth.getName());}

    @GetMapping("/catalog/technologies") @PreAuthorize("hasAuthority('APPLICATION_READ')")
    public List<String> technologies(){return List.of("ANGULAR","SPRING_BOOT","NODEJS","PHP","GO","PYTHON","DOTNET","ORACLE_FORMS","STATIC","OTHER");}
}
