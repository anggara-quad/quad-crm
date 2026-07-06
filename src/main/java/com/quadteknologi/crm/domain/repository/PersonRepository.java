package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.Person;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByPublicId(UUID publicId);

    @Override
    @EntityGraph(attributePaths = {"company", "createdBy", "updatedBy"})
    Optional<Person> findById(Long id);

    @EntityGraph(attributePaths = {"company", "createdBy"})
    List<Person> findAllByOrderByFullNameAsc();

    @EntityGraph(attributePaths = {"company", "createdBy"})
    List<Person> findByCreatedByIdOrderByFullNameAsc(Long createdById);

    long countByCreatedById(Long createdById);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndCreatedById(
            LocalDateTime from, LocalDateTime to, Long createdById);

    @EntityGraph(attributePaths = {"company", "createdBy"})
    List<Person> findByCompanyIdOrderByFullNameAsc(Long companyId);

    @EntityGraph(attributePaths = {"company", "createdBy"})
    List<Person> findByCompanyIdAndCreatedByIdOrderByFullNameAsc(Long companyId, Long createdById);

    List<Person> findByFullNameContainingIgnoreCaseOrderByFullNameAsc(String fullName);

    @Query(value = """
            select p.id,
                   p.public_id,
                   p.full_name,
                   p.job_title,
                   c.name as company_name,
                   p.email,
                   p.phone,
                   p.whatsapp,
                   coalesce(lead_summary.leads, 0) as leads,
                   coalesce(lead_summary.valid_leads, 0) as valid_leads,
                   coalesce(opportunity_summary.opportunities, 0) as opportunities,
                   coalesce(opportunity_summary.open_opportunities, 0) as open_opportunities,
                   coalesce(opportunity_summary.open_pipeline, 0) as open_pipeline,
                   coalesce(opportunity_summary.won_revenue, 0) as won_revenue,
                   greatest(
                       coalesce(lead_summary.last_lead_at, timestamp '1970-01-01'),
                       coalesce(opportunity_summary.last_opportunity_at, timestamp '1970-01-01'),
                       p.updated_at
                   ) as last_activity_at
            from persons p
            left join companies c on c.id = p.company_id
            left join (
                select person_id,
                       count(*) as leads,
                       sum(case when status_code = 'VALID' then 1 else 0 end) as valid_leads,
                       max(updated_at) as last_lead_at
                from leads
                where person_id is not null
                  and (:createdById is null or created_by = :createdById)
                group by person_id
            ) lead_summary on lead_summary.person_id = p.id
            left join (
                select o.person_id,
                       count(*) as opportunities,
                       sum(case when (status.is_won = false or status.is_won is null)
                                  and (status.is_lost = false or status.is_lost is null)
                                then 1 else 0 end) as open_opportunities,
                       coalesce(sum(case when (status.is_won = false or status.is_won is null)
                                           and (status.is_lost = false or status.is_lost is null)
                                         then o.estimated_amount else 0 end), 0) as open_pipeline,
                       coalesce(sum(case when status.is_won = true then o.estimated_amount else 0 end), 0) as won_revenue,
                       max(o.updated_at) as last_opportunity_at
                from opportunities o
                left join option_values status
                    on status.group_code = o.status_group_code
                   and status.code = o.status_code
                where o.person_id is not null
                  and (:createdById is null or o.created_by = :createdById)
                group by o.person_id
            ) opportunity_summary on opportunity_summary.person_id = p.id
            where (:createdById is null or p.created_by = :createdById)
            order by p.full_name asc
            """, nativeQuery = true)
    List<Object[]> summarizePersons(@Param("createdById") Long createdById);
}
