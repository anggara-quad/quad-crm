package com.quadteknologi.crm.security;

import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.domain.repository.UserRepository;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CurrentUserService {

    private final AuthenticationContext authenticationContext;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public CurrentUserService(
            AuthenticationContext authenticationContext,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.authenticationContext = authenticationContext;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Optional<User> getUser() {
        return getEmail().flatMap(userRepository::findByEmailIgnoreCase);
    }

    public Optional<String> getEmail() {
        return authenticationContext.getPrincipalName();
    }

    @Transactional(readOnly = true)
    public Optional<String> getDisplayName() {
        return getUser().map(User::getFullName);
    }

    public boolean hasRole(String role) {
        return authenticationContext.hasRole(role);
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        User user = getUser().orElseThrow();
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is not correct.");
        }
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public void logout() {
        authenticationContext.logout();
    }
}
