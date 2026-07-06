package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.Activity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    @Query("""
            select distinct activity
            from Activity activity
            left join activity.lead lead
            left join activity.opportunity opportunity
            where activity.activityDate >= :from
              and activity.activityDate < :to
              and (
                    :salesId is null
                    or opportunity.assignedTo.id = :salesId
                    or (opportunity is null and lead.assignedTo.id = :salesId)
                    or (opportunity is null and lead is null and activity.createdBy.id = :salesId)
                  )
            order by activity.activityDate desc
            """)
    @EntityGraph(attributePaths = {
            "type", "lead", "lead.assignedTo", "opportunity", "opportunity.assignedTo", "company", "person", "createdBy"
    })
    List<Activity> findDashboardActivities(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("salesId") Long salesId);
}
