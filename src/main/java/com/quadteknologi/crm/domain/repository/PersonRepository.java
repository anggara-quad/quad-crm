package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.Person;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByPublicId(UUID publicId);

    @Override
    @EntityGraph(attributePaths = {"company", "createdBy", "updatedBy"})
    Optional<Person> findById(Long id);

    @EntityGraph(attributePaths = "company")
    List<Person> findAllByOrderByFullNameAsc();

    @EntityGraph(attributePaths = "company")
    List<Person> findByCreatedByIdOrderByFullNameAsc(Long createdById);

    long countByCreatedById(Long createdById);

    List<Person> findByCompanyIdOrderByFullNameAsc(Long companyId);

    List<Person> findByFullNameContainingIgnoreCaseOrderByFullNameAsc(String fullName);
}
