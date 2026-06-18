package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.Opportunity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {

    @Override
    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    Optional<Opportunity> findById(Long id);

    Optional<Opportunity> findByPublicId(UUID publicId);

    long countByCreatedAtGreaterThanEqual(LocalDateTime createdAt);

    long countByCreatedAtGreaterThanEqualAndCreatedById(LocalDateTime createdAt, Long createdById);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    Optional<Opportunity> findByLeadId(Long leadId);

    long countByStatusGroupCodeAndStatusCode(String statusGroupCode, String statusCode);

    long countByStatusGroupCodeAndStatusCodeAndCreatedById(String statusGroupCode, String statusCode, Long createdById);

    @Query("""
            select coalesce(sum(opportunity.estimatedAmount), 0)
            from Opportunity opportunity
            left join opportunity.status status
            where opportunity.statusGroupCode = :statusGroupCode
              and (status.won = false or status.won is null)
              and (status.lost = false or status.lost is null)
            """)
    BigDecimal sumOpenEstimatedAmount(@Param("statusGroupCode") String statusGroupCode);

    @Query("""
            select coalesce(sum(opportunity.estimatedAmount), 0)
            from Opportunity opportunity
            left join opportunity.status status
            where opportunity.statusGroupCode = :statusGroupCode
              and opportunity.createdBy.id = :createdById
              and (status.won = false or status.won is null)
              and (status.lost = false or status.lost is null)
            """)
    BigDecimal sumOpenEstimatedAmountByCreatedById(
            @Param("statusGroupCode") String statusGroupCode,
            @Param("createdById") Long createdById);

    @Query("""
            select opportunity.statusCode, count(opportunity), coalesce(sum(opportunity.estimatedAmount), 0)
            from Opportunity opportunity
            where opportunity.statusGroupCode = :statusGroupCode
            group by opportunity.statusCode
            """)
    List<Object[]> summarizeByStatusGroupCode(@Param("statusGroupCode") String statusGroupCode);

    @Query("""
            select opportunity.statusCode, count(opportunity), coalesce(sum(opportunity.estimatedAmount), 0)
            from Opportunity opportunity
            where opportunity.statusGroupCode = :statusGroupCode
              and opportunity.createdBy.id = :createdById
            group by opportunity.statusCode
            """)
    List<Object[]> summarizeByStatusGroupCodeAndCreatedById(
            @Param("statusGroupCode") String statusGroupCode,
            @Param("createdById") Long createdById);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findByStatusGroupCodeAndStatusCodeOrderByCreatedAtDesc(String statusGroupCode, String statusCode);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findByStatusGroupCodeAndStatusCodeAndCreatedByIdOrderByCreatedAtDesc(
            String statusGroupCode, String statusCode, Long createdById);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findByStatusGroupCodeOrderByCreatedAtDesc(String statusGroupCode);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findByStatusGroupCodeAndCreatedByIdOrderByCreatedAtDesc(String statusGroupCode, Long createdById);
}
