package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.Region;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegionRepository extends JpaRepository<Region, Long> {

    @EntityGraph(attributePaths = {"country"})
    List<Region> findByCountryIdAndRegionLevelAndActiveTrueOrderByNameAsc(Long countryId, Short regionLevel);

    @EntityGraph(attributePaths = {"country", "parent"})
    List<Region> findByParentIdAndRegionLevelAndActiveTrueOrderByNameAsc(Long parentId, Short regionLevel);
}
