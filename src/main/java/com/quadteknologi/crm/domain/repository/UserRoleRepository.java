package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.UserRole;
import com.quadteknologi.crm.domain.entity.UserRoleId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @EntityGraph(attributePaths = "role")
    List<UserRole> findByIdUserId(Long userId);

    List<UserRole> findByIdRoleId(Long roleId);
}
