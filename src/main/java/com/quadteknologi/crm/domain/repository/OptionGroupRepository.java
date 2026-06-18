package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.OptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OptionGroupRepository extends JpaRepository<OptionGroup, String> {

    List<OptionGroup> findByActiveTrueOrderByNameAsc();
}
