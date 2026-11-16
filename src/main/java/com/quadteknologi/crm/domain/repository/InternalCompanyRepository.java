package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.InternalCompany;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InternalCompanyRepository extends JpaRepository<InternalCompany, Long> {

    @EntityGraph(attributePaths = {"createdBy", "updatedBy"})
    Optional<InternalCompany> findByPublicId(UUID publicId);

    @EntityGraph(attributePaths = {"createdBy", "updatedBy"})
    List<InternalCompany> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"createdBy", "updatedBy"})
    List<InternalCompany> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
