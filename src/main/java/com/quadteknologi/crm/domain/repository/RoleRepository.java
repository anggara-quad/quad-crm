package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByPublicId(UUID publicId);

    Optional<Role> findByCode(String code);

    List<Role> findByActiveTrueOrderByNameAsc();
}
