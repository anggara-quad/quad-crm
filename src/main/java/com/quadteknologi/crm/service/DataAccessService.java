package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.domain.repository.UserRepository;
import com.quadteknologi.crm.security.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DataAccessService {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    public DataAccessService(CurrentUserService currentUserService, UserRepository userRepository) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
    }

    public boolean isSalesScope() {
        return currentUserService.hasRole("Sales")
                && !currentUserService.hasRole("Manager")
                && !currentUserService.hasRole("Administrator");
    }

    public Optional<User> getCurrentUserReference() {
        return currentUserService.getUser()
                .filter(user -> user.getId() != null)
                .map(user -> userRepository.getReferenceById(user.getId()));
    }

    public User requireCurrentUserReference() {
        return getCurrentUserReference()
                .orElseThrow(() -> new AccessDeniedException("Signed in user was not found."));
    }

    public Optional<Long> getCurrentUserId() {
        return currentUserService.getUser().map(User::getId);
    }

    public Long requireCurrentUserId() {
        return getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Signed in user was not found."));
    }

    public void assertCanAccessCreatedBy(String resourceName, User createdBy) {
        if (!isSalesScope()) {
            return;
        }

        Long currentUserId = requireCurrentUserId();
        if (createdBy == null || createdBy.getId() == null || !createdBy.getId().equals(currentUserId)) {
            throw new AccessDeniedException("You can only access your own " + resourceName + ".");
        }
    }
}
