package com.daertech.platform.security;

import com.daertech.platform.user.UserAccountRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PlatformUserDetailsService implements UserDetailsService {
    private final UserAccountRepository users;
    public PlatformUserDetailsService(UserAccountRepository users){ this.users = users; }
    @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var account = users.findByUsernameIgnoreCase(username).orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        var authorities = account.getRoles().stream()
            .filter(r -> r.isActive())
            .flatMap(r -> java.util.stream.Stream.concat(
                java.util.stream.Stream.of(new SimpleGrantedAuthority("ROLE_" + r.getCode())),
                r.getPermissions().stream().map(p -> new SimpleGrantedAuthority(p.getCode()))))
            .distinct().toList();
        return User.withUsername(account.getUsername()).password(account.getPasswordHash())
            .disabled(!account.isEnabled()).accountLocked(account.isLocked()).authorities(authorities).build();
    }
}
