package com.daertech.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService){ this.authService = authService; }

    @PostMapping("/login")
    public Map<String,Object> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http){
        return authService.login(request.username(), request.password(), clientIp(http));
    }

    @PostMapping("/refresh")
    public Map<String,Object> refresh(@Valid @RequestBody RefreshRequest request){ return authService.refresh(request.refreshToken()); }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request){ authService.logout(request.refreshToken()); return ResponseEntity.noContent().build(); }

    private String clientIp(HttpServletRequest request){
        String forwarded=request.getHeader("X-Forwarded-For");
        return forwarded==null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
    public record LoginRequest(@NotBlank String username, @NotBlank String password){}
    public record RefreshRequest(@NotBlank String refreshToken){}
}
