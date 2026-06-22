package com.quadteknologi.crm.domain.repository;

import com.quadteknologi.crm.domain.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CountryRepository extends JpaRepository<Country, Long> {

    List<Country> findByActiveTrueOrderByNameAsc();
}
