package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findAllByOrderByFullNameAsc();

    List<User> findByActiveTrueOrderByFullNameAsc();

    @Query("""
            select distinct user
            from User user
            join UserRole userRole on userRole.user = user
            join userRole.role role
            where user.active = true
              and role.name = 'Sales'
            order by user.fullName asc
            """)
    List<User> findActiveSalesUsersOrderByFullNameAsc();
}
