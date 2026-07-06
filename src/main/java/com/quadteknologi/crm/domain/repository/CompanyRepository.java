package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.Company;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByPublicId(UUID publicId);

    @Override
    @EntityGraph(attributePaths = {"country", "province", "city", "createdBy", "updatedBy"})
    Optional<Company> findById(Long id);

    @EntityGraph(attributePaths = {"country", "province", "city", "createdBy"})
    List<Company> findAllByOrderByNameAsc();

    @EntityGraph(attributePaths = {"country", "province", "city", "createdBy"})
    List<Company> findByCreatedByIdOrderByNameAsc(Long createdById);

    long countByCreatedById(Long createdById);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndCreatedById(
            LocalDateTime from, LocalDateTime to, Long createdById);

    @EntityGraph(attributePaths = {"country", "province", "city"})
    List<Company> findByNameContainingIgnoreCaseOrderByNameAsc(String name);

    long countByIndustryIsNotNull();

    @Query(value = """
            select c.id,
                   c.public_id,
                   c.name,
                   c.industry,
                   c.email,
                   c.phone,
                   c.website,
                   coalesce(city.name, nullif(c.city, '')) as city_name,
                   coalesce(province.name, nullif(c.province, '')) as province_name,
                   coalesce(country.name, nullif(c.country, '')) as country_name,
                   coalesce(person_summary.contacts, 0) as contacts,
                   coalesce(lead_summary.leads, 0) as leads,
                   coalesce(lead_summary.valid_leads, 0) as valid_leads,
                   coalesce(opportunity_summary.opportunities, 0) as opportunities,
                   coalesce(opportunity_summary.open_opportunities, 0) as open_opportunities,
                   coalesce(opportunity_summary.open_pipeline, 0) as open_pipeline,
                   coalesce(opportunity_summary.won_revenue, 0) as won_revenue,
                   greatest(
                       coalesce(person_summary.last_contact_at, timestamp '1970-01-01'),
                       coalesce(lead_summary.last_lead_at, timestamp '1970-01-01'),
                       coalesce(opportunity_summary.last_opportunity_at, timestamp '1970-01-01'),
                       c.updated_at
                   ) as last_activity_at
            from companies c
            left join countries country on country.id = c.country_id
            left join regions province on province.id = c.province_id
            left join regions city on city.id = c.city_id
            left join (
                select company_id,
                       count(*) as contacts,
                       max(updated_at) as last_contact_at
                from persons
                where company_id is not null
                  and (:createdById is null or created_by = :createdById)
                group by company_id
            ) person_summary on person_summary.company_id = c.id
            left join (
                select company_id,
                       count(*) as leads,
                       sum(case when status_code = 'VALID' then 1 else 0 end) as valid_leads,
                       max(updated_at) as last_lead_at
                from leads
                where company_id is not null
                  and (:createdById is null or created_by = :createdById)
                group by company_id
            ) lead_summary on lead_summary.company_id = c.id
            left join (
                select o.company_id,
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
                where o.company_id is not null
                  and (:createdById is null or o.created_by = :createdById)
                group by o.company_id
            ) opportunity_summary on opportunity_summary.company_id = c.id
            where (:createdById is null or c.created_by = :createdById)
            order by c.name asc
            """, nativeQuery = true)
    List<Object[]> summarizeCompanies(@Param("createdById") Long createdById);
}
