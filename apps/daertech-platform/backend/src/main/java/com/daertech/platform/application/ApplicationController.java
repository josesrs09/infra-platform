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

    @DeleteMapping("/{id}/environments/{environmentId}") @PreAuthorize("hasAuthority('APPLICATION_WRITE')")
    public Map<String,Object> deleteEnvironment(@PathVariable UUID id,@PathVariable UUID environmentId){return service.deleteEnvironment(id,environmentId);}

    @PostMapping("/{id}/variables") @PreAuthorize("hasAuthority('APPLICATION_WRITE')")
    public Map<String,Object> variable(@PathVariable UUID id,@RequestBody ApplicationService.VariableRequest request){return service.saveVariable(id,request);}

    @DeleteMapping("/{id}/variables/{variableId}") @PreAuthorize("hasAuthority('APPLICATION_WRITE')")
    public Map<String,Object> deleteVariable(@PathVariable UUID id,@PathVariable UUID variableId){return service.deleteVariable(id,variableId);}

    @PostMapping("/{id}/dependencies") @PreAuthorize("hasAuthority('APPLICATION_WRITE')")
    public Map<String,Object> dependency(@PathVariable UUID id,@RequestBody ApplicationService.DependencyRequest request){return service.saveDependency(id,request);}

    @DeleteMapping("/{id}/dependencies/{dependencyId}") @PreAuthorize("hasAuthority('APPLICATION_WRITE')")
    public Map<String,Object> deleteDependency(@PathVariable UUID id,@PathVariable UUID dependencyId){return service.deleteDependency(id,dependencyId);}

    @PostMapping("/{id}/versions") @PreAuthorize("hasAuthority('APPLICATION_WRITE')")
    public Map<String,Object> version(@PathVariable UUID id,@RequestBody ApplicationService.VersionRequest request,Authentication auth){return service.addVersion(id,request,auth.getName());}

    @DeleteMapping("/{id}/versions/{versionId}") @PreAuthorize("hasAuthority('APPLICATION_WRITE')")
    public Map<String,Object> deleteVersion(@PathVariable UUID id,@PathVariable UUID versionId){return service.deleteVersion(id,versionId);}

    @GetMapping("/catalog/technologies") @PreAuthorize("hasAuthority('APPLICATION_READ')")
    public List<String> technologies(){return List.of("ANGULAR","SPRING_BOOT","NODEJS","PHP","GO","PYTHON","DOTNET","ORACLE_FORMS","STATIC","OTHER");}
}
