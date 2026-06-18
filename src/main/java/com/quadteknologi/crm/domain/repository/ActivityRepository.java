package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.Activity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    Optional<Activity> findByPublicId(UUID publicId);

    @EntityGraph(attributePaths = {"type", "lead", "opportunity", "company", "person"})
    List<Activity> findTop8ByOrderByActivityDateDesc();

    @EntityGraph(attributePaths = {"type", "lead", "opportunity", "company", "person"})
    List<Activity> findTop8ByCreatedByIdOrderByActivityDateDesc(Long createdById);

    @EntityGraph(attributePaths = {"type", "createdBy"})
    List<Activity> findByLeadIdOrderByActivityDateDesc(Long leadId);

    @EntityGraph(attributePaths = {"type", "createdBy"})
    List<Activity> findByOpportunityIdOrderByActivityDateDesc(Long opportunityId);

    List<Activity> findByCompanyIdOrderByActivityDateDesc(Long companyId);

    List<Activity> findByPersonIdOrderByActivityDateDesc(Long personId);
}
