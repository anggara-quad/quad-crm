package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.OptionValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OptionValueRepository extends JpaRepository<OptionValue, Long> {

    Optional<OptionValue> findByPublicId(UUID publicId);

    Optional<OptionValue> findByGroupCodeAndCode(String groupCode, String code);

    List<OptionValue> findByGroupCodeOrderBySortOrderAsc(String groupCode);

    List<OptionValue> findByGroupCodeAndActiveTrueOrderBySortOrderAsc(String groupCode);
}
