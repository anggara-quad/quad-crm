package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.Role;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.domain.entity.UserRole;
import com.quadteknologi.crm.domain.repository.RoleRepository;
import com.quadteknologi.crm.domain.repository.UserRepository;
import com.quadteknologi.crm.domain.repository.UserRoleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserSettingsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserSettingsService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserAccount> findUserAccounts() {
        return userRepository.findAllByOrderByFullNameAsc()
                .stream()
                .map(user -> new UserAccount(user, findRolesForUser(user.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Role> findActiveRoles() {
        return roleRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Role> findRolesForUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userRoleRepository.findByIdUserId(userId)
                .stream()
                .map(UserRole::getRole)
                .toList();
    }

    @Transactional
    public User saveUser(UserForm form) {
        User user = form.getUserId() == null
                ? new User()
                : userRepository.findById(form.getUserId()).orElseThrow();

        user.setFullName(trimToNull(form.getFullName()));
        user.setEmail(trimToNull(form.getEmail()));
        user.setActive(Boolean.TRUE.equals(form.getActive()));

        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        } else if (user.getId() == null) {
            throw new IllegalArgumentException("Password is required for new user");
        }

        User savedUser = userRepository.save(user);
        assignRoles(savedUser, form.getRoles());
        return savedUser;
    }

    @Transactional
    public void deleteUser(User user) {
        List<UserRole> existingRoles = userRoleRepository.findByIdUserId(user.getId());
        userRoleRepository.deleteAll(existingRoles);
        try {
            userRepository.delete(user);
        } catch (DataIntegrityViolationException exception) {
            throw exception;
        }
    }

    private void assignRoles(User user, Set<Role> roles) {
        List<UserRole> existingRoles = userRoleRepository.findByIdUserId(user.getId());
        userRoleRepository.deleteAll(existingRoles);
        userRoleRepository.flush();

        if (roles == null || roles.isEmpty()) {
            return;
        }

        List<UserRole> nextRoles = roles.stream()
                .filter(role -> role.getId() != null)
                .map(role -> roleRepository.getReferenceById(role.getId()))
                .map(role -> new UserRole(user, role))
                .toList();
        userRoleRepository.saveAll(nextRoles);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record UserAccount(User user, List<Role> roles) {

        public String roleNames() {
            if (roles == null || roles.isEmpty()) {
                return "-";
            }
            return roles.stream()
                    .map(Role::getName)
                    .collect(Collectors.joining(", "));
        }
    }

    public static class UserForm {

        private Long userId;
        private String fullName;
        private String email;
        private String password;
        private Boolean active = true;
        private Set<Role> roles = Set.of();

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }

        public Set<Role> getRoles() {
            return roles;
        }

        public void setRoles(Set<Role> roles) {
            this.roles = roles;
        }
    }
}
