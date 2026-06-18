package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.Company;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByPublicId(UUID publicId);

    @Override
    @EntityGraph(attributePaths = {"createdBy", "updatedBy"})
    Optional<Company> findById(Long id);

    List<Company> findAllByOrderByNameAsc();

    List<Company> findByCreatedByIdOrderByNameAsc(Long createdById);

    long countByCreatedById(Long createdById);

    List<Company> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    long countByIndustryIsNotNull();
}
