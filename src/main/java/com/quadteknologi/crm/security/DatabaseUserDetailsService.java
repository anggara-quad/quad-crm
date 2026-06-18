package com.quadteknologi.crm.security;

import com.quadteknologi.crm.domain.entity.Role;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.domain.entity.UserRole;
import com.quadteknologi.crm.domain.repository.UserRepository;
import com.quadteknologi.crm.domain.repository.UserRoleRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public DatabaseUserDetailsService(UserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = username == null ? "" : username.trim();
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(loadAuthorities(user))
                .disabled(!Boolean.TRUE.equals(user.getActive()))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .build();
    }

    private Set<GrantedAuthority> loadAuthorities(User user) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        userRoleRepository.findByIdUserId(user.getId())
                .stream()
                .map(UserRole::getRole)
                .filter(role -> Boolean.TRUE.equals(role.getActive()))
                .forEach(role -> addRoleAuthorities(authorities, role));
        return authorities;
    }

    private void addRoleAuthorities(Set<GrantedAuthority> authorities, Role role) {
        addRoleAuthority(authorities, role.getName());
        addRoleAuthority(authorities, role.getCode());
    }

    private void addRoleAuthority(Set<GrantedAuthority> authorities, String value) {
        if (value != null && !value.isBlank()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + value.trim()));
        }
    }
}
