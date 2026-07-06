package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.Opportunity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OpportunityRepository extends JpaRepository<Opportunity, Long> {

    @Override
    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    Optional<Opportunity> findById(Long id);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
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

    @Query("""
            select opportunity.assignedTo.id,
                   count(opportunity),
                   coalesce(sum(opportunity.estimatedAmount), 0),
                   coalesce(sum(case when status.won = true then opportunity.estimatedAmount else 0 end), 0),
                   sum(case when status.won = true then 1 else 0 end),
                   sum(case when status.lost = true then 1 else 0 end),
                   sum(case when (status.won = false or status.won is null)
                              and (status.lost = false or status.lost is null)
                            then 1 else 0 end),
                   coalesce(sum(case when (status.won = false or status.won is null)
                                      and (status.lost = false or status.lost is null)
                                    then opportunity.estimatedAmount else 0 end), 0),
                   sum(case when (status.won = false or status.won is null)
                              and (status.lost = false or status.lost is null)
                              and opportunity.expectedCloseDate is not null
                              and opportunity.expectedCloseDate <= :closingUntil
                            then 1 else 0 end)
            from Opportunity opportunity
            left join opportunity.status status
            where opportunity.statusGroupCode = :statusGroupCode
              and opportunity.assignedTo is not null
            group by opportunity.assignedTo.id
            """)
    List<Object[]> summarizeSalesPerformanceByAssignedTo(
            @Param("statusGroupCode") String statusGroupCode,
            @Param("closingUntil") LocalDate closingUntil);

    @Query("""
            select opportunity.statusCode, count(opportunity), coalesce(sum(opportunity.estimatedAmount), 0)
            from Opportunity opportunity
            where opportunity.statusGroupCode = :statusGroupCode
              and opportunity.assignedTo is not null
            group by opportunity.statusCode
            """)
    List<Object[]> summarizeByStatusGroupCodeAndAssignedToIsNotNull(
            @Param("statusGroupCode") String statusGroupCode);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findByStatusGroupCodeAndStatusCodeOrderByCreatedAtDesc(String statusGroupCode, String statusCode);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findByStatusGroupCodeAndStatusCodeAndCreatedByIdOrderByCreatedAtDesc(
            String statusGroupCode, String statusCode, Long createdById);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findByStatusGroupCodeOrderByCreatedAtDesc(String statusGroupCode);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findByStatusGroupCodeAndCreatedByIdOrderByCreatedAtDesc(String statusGroupCode, Long createdById);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findByPersonIdOrderByCreatedAtDesc(Long personId);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findByPersonIdAndCreatedByIdOrderByCreatedAtDesc(Long personId, Long createdById);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findByCompanyIdAndCreatedByIdOrderByCreatedAtDesc(Long companyId, Long createdById);

    @Query("""
            select opportunity
            from Opportunity opportunity
            where opportunity.statusGroupCode = :statusGroupCode
              and opportunity.createdAt >= :from
              and opportunity.createdAt < :to
              and (:assignedToId is null or opportunity.assignedTo.id = :assignedToId)
            order by opportunity.createdAt desc
            """)
    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findDashboardOpportunities(
            @Param("statusGroupCode") String statusGroupCode,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("assignedToId") Long assignedToId);

    @Query("""
            select opportunity
            from Opportunity opportunity
            left join opportunity.status status
            where opportunity.statusGroupCode = :statusGroupCode
              and opportunity.expectedCloseDate between :from and :to
              and (status.won = false or status.won is null)
              and (status.lost = false or status.lost is null)
              and (:assignedToId is null or opportunity.assignedTo.id = :assignedToId)
            order by opportunity.expectedCloseDate asc, opportunity.createdAt desc
            """)
    @EntityGraph(attributePaths = {"lead", "company", "person", "assignedTo", "status"})
    List<Opportunity> findDashboardClosingSoon(
            @Param("statusGroupCode") String statusGroupCode,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("assignedToId") Long assignedToId);
}
