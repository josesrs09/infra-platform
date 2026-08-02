package com.daertech.platform.security;

import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        return http.csrf(csrf->csrf.disable()).cors(cors->{}).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth->auth.requestMatchers("/auth/**","/actuator/health/**","/actuator/info","/internal/alerts/alertmanager").permitAll().anyRequest().authenticated())
            .oauth2ResourceServer(oauth->oauth.jwt(jwt->jwt.jwtAuthenticationConverter(converter))).build();
    }
    @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception{return configuration.getAuthenticationManager();}
    @Bean JwtEncoder jwtEncoder(@Value("${app.jwt.secret}") String secret){return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<>(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")));}
    @Bean JwtDecoder jwtDecoder(@Value("${app.jwt.secret}") String secret){return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256")).build();}
    @Bean JwtAuthenticationConverter jwtAuthenticationConverter(){var granted=new JwtGrantedAuthoritiesConverter();granted.setAuthoritiesClaimName("scope");granted.setAuthorityPrefix("");var converter=new JwtAuthenticationConverter();converter.setJwtGrantedAuthoritiesConverter(granted);return converter;}
}
