package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.Lead;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    @Override
    @EntityGraph(attributePaths = {"company", "person", "assignedTo", "status", "convertedBy"})
    Optional<Lead> findById(Long id);

    Optional<Lead> findByPublicId(UUID publicId);

    long countByCreatedAtGreaterThanEqual(LocalDateTime createdAt);

    long countByCreatedAtGreaterThanEqualAndCreatedById(LocalDateTime createdAt, Long createdById);

    long countByStatusGroupCodeAndStatusCode(String statusGroupCode, String statusCode);

    long countByStatusGroupCodeAndStatusCodeAndCreatedById(String statusGroupCode, String statusCode, Long createdById);

    @Query("""
            select lead.statusCode, count(lead)
            from Lead lead
            where lead.statusGroupCode = :statusGroupCode
            group by lead.statusCode
            """)
    List<Object[]> countByStatusGroupCode(@Param("statusGroupCode") String statusGroupCode);

    @Query("""
            select lead.statusCode, count(lead)
            from Lead lead
            where lead.statusGroupCode = :statusGroupCode
              and lead.createdBy.id = :createdById
            group by lead.statusCode
            """)
    List<Object[]> countByStatusGroupCodeAndCreatedById(
            @Param("statusGroupCode") String statusGroupCode,
            @Param("createdById") Long createdById);

    @EntityGraph(attributePaths = {"company", "person", "assignedTo", "status", "convertedBy"})
    List<Lead> findByStatusGroupCodeAndStatusCodeOrderByCreatedAtDesc(String statusGroupCode, String statusCode);

    @EntityGraph(attributePaths = {"company", "person", "assignedTo", "status", "convertedBy"})
    List<Lead> findByStatusGroupCodeAndStatusCodeAndCreatedByIdOrderByCreatedAtDesc(
            String statusGroupCode, String statusCode, Long createdById);

    @Query("""
            select lead
            from Lead lead
            where lead.statusGroupCode = :statusGroupCode
              and lead.statusCode = :statusCode
              and not exists (
                  select 1
                  from Opportunity opportunity
                  where opportunity.lead = lead
              )
            order by lead.createdAt desc
            """)
    @EntityGraph(attributePaths = {"company", "person", "assignedTo", "status", "convertedBy"})
    List<Lead> findUnconvertedByStatusGroupCodeAndStatusCodeOrderByCreatedAtDesc(
            @Param("statusGroupCode") String statusGroupCode,
            @Param("statusCode") String statusCode);

    @Query("""
            select lead
            from Lead lead
            where lead.statusGroupCode = :statusGroupCode
              and lead.statusCode = :statusCode
              and lead.createdBy.id = :createdById
              and not exists (
                  select 1
                  from Opportunity opportunity
                  where opportunity.lead = lead
              )
            order by lead.createdAt desc
            """)
    @EntityGraph(attributePaths = {"company", "person", "assignedTo", "status", "convertedBy"})
    List<Lead> findUnconvertedByStatusGroupCodeAndStatusCodeAndCreatedByIdOrderByCreatedAtDesc(
            @Param("statusGroupCode") String statusGroupCode,
            @Param("statusCode") String statusCode,
            @Param("createdById") Long createdById);

    @EntityGraph(attributePaths = {"company", "person", "assignedTo", "status", "convertedBy"})
    List<Lead> findByStatusGroupCodeOrderByCreatedAtDesc(String statusGroupCode);

    @EntityGraph(attributePaths = {"company", "person", "assignedTo", "status", "convertedBy"})
    List<Lead> findByStatusGroupCodeAndCreatedByIdOrderByCreatedAtDesc(String statusGroupCode, Long createdById);
}
