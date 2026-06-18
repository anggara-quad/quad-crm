package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findAllByOrderByFullNameAsc();

    List<User> findByActiveTrueOrderByFullNameAsc();
}
