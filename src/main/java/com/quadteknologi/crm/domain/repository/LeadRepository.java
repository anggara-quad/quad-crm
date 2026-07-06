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

    @EntityGraph(attributePaths = {"company", "person", "assignedTo", "status", "convertedBy"})
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

    @Query("""
            select lead.assignedTo.id, count(lead)
            from Lead lead
            where lead.statusGroupCode = :statusGroupCode
              and lead.assignedTo is not null
            group by lead.assignedTo.id
            """)
    List<Object[]> countByStatusGroupCodeGroupByAssignedTo(
            @Param("statusGroupCode") String statusGroupCode);

    @Query("""
            select lead.assignedTo.id, count(lead)
            from Lead lead
            where lead.statusGroupCode = :statusGroupCode
              and lead.statusCode = :statusCode
              and lead.assignedTo is not null
            group by lead.assignedTo.id
            """)
    List<Object[]> countByStatusGroupCodeAndStatusCodeGroupByAssignedTo(
            @Param("statusGroupCode") String statusGroupCode,
            @Param("statusCode") String statusCode);

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

    @EntityGraph(attributePaths = {"company", "person", "assignedTo", "status", "convertedBy"})
    List<Lead> findByPersonIdOrderByCreatedAtDesc(Long personId);

    @EntityGraph(attributePaths = {"company", "person", "assignedTo", "status", "convertedBy"})
    List<Lead> findByPersonIdAndCreatedByIdOrderByCreatedAtDesc(Long personId, Long createdById);

    @EntityGraph(attributePaths = {"company", "person", "assignedTo", "status", "convertedBy"})
    List<Lead> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    @EntityGraph(attributePaths = {"company", "person", "assignedTo", "status", "convertedBy"})
    List<Lead> findByCompanyIdAndCreatedByIdOrderByCreatedAtDesc(Long companyId, Long createdById);

    @Query("""
            select lead
            from Lead lead
            where lead.statusGroupCode = :statusGroupCode
              and lead.createdAt >= :from
              and lead.createdAt < :to
              and (:assignedToId is null or lead.assignedTo.id = :assignedToId)
            order by lead.createdAt desc
            """)
    @EntityGraph(attributePaths = {"company", "person", "assignedTo", "status", "convertedBy"})
    List<Lead> findDashboardLeads(
            @Param("statusGroupCode") String statusGroupCode,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("assignedToId") Long assignedToId);

    @Query(value = """
            select coalesce(nullif(btrim(lead.source), ''), 'Unknown') as source_name,
                   count(lead.id) as total_leads,
                   sum(case when lead.status_code = :validStatusCode then 1 else 0 end) as valid_leads,
                   coalesce(sum(lead_item_summary.estimated_value), 0) as estimated_lead_value,
                   coalesce(sum(opportunity_summary.converted_opportunities), 0) as converted_opportunities
            from leads lead
            left join (
                select lead_id, sum(estimated_total) as estimated_value
                from lead_items
                group by lead_id
            ) lead_item_summary on lead_item_summary.lead_id = lead.id
            left join (
                select lead_id, count(*) as converted_opportunities
                from opportunities
                where lead_id is not null
                  and (:opportunityStatusCode is null or status_code = :opportunityStatusCode)
                group by lead_id
            ) opportunity_summary on opportunity_summary.lead_id = lead.id
            where lead.status_group_code = :statusGroupCode
              and lead.created_at >= :from
              and lead.created_at < :to
              and (:assignedToId is null or lead.assigned_to = :assignedToId)
              and (:leadStatusCode is null or lead.status_code = :leadStatusCode)
              and (:leadSource is null or coalesce(nullif(btrim(lead.source), ''), 'Unknown') = :leadSource)
              and (
                  :opportunityStatusCode is null
                  or exists (
                      select 1
                      from opportunities opportunity_filter
                      where opportunity_filter.lead_id = lead.id
                        and opportunity_filter.status_code = :opportunityStatusCode
                  )
              )
            group by source_name
            order by total_leads desc, source_name asc
            """, nativeQuery = true)
    List<Object[]> summarizeLeadSources(
            @Param("statusGroupCode") String statusGroupCode,
            @Param("validStatusCode") String validStatusCode,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("assignedToId") Long assignedToId,
            @Param("leadStatusCode") String leadStatusCode,
            @Param("opportunityStatusCode") String opportunityStatusCode,
            @Param("leadSource") String leadSource);
}
