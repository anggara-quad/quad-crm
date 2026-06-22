package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.Company;
import com.quadteknologi.crm.domain.entity.Country;
import com.quadteknologi.crm.domain.entity.Person;
import com.quadteknologi.crm.domain.entity.Region;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.domain.repository.CompanyRepository;
import com.quadteknologi.crm.domain.repository.CountryRepository;
import com.quadteknologi.crm.domain.repository.PersonRepository;
import com.quadteknologi.crm.domain.repository.RegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class ContactService {

    private final CompanyRepository companyRepository;
    private final PersonRepository personRepository;
    private final CountryRepository countryRepository;
    private final RegionRepository regionRepository;
    private final DataAccessService dataAccessService;

    public ContactService(
            CompanyRepository companyRepository,
            PersonRepository personRepository,
            CountryRepository countryRepository,
            RegionRepository regionRepository,
            DataAccessService dataAccessService) {
        this.companyRepository = companyRepository;
        this.personRepository = personRepository;
        this.countryRepository = countryRepository;
        this.regionRepository = regionRepository;
        this.dataAccessService = dataAccessService;
    }

    @Transactional(readOnly = true)
    public List<Person> findAllPersons() {
        if (dataAccessService.isSalesScope()) {
            return personRepository.findByCreatedByIdOrderByFullNameAsc(dataAccessService.requireCurrentUserId());
        }
        return personRepository.findAllByOrderByFullNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Company> findAllCompanies() {
        if (dataAccessService.isSalesScope()) {
            return companyRepository.findByCreatedByIdOrderByNameAsc(dataAccessService.requireCurrentUserId());
        }
        return companyRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Country> findActiveCountries() {
        return countryRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Region> findActiveProvinces(Country country) {
        if (country == null || country.getId() == null) {
            return List.of();
        }
        return regionRepository.findByCountryIdAndRegionLevelAndActiveTrueOrderByNameAsc(country.getId(), (short) 1);
    }

    @Transactional(readOnly = true)
    public List<Region> findActiveCities(Region province) {
        if (province == null || province.getId() == null) {
            return List.of();
        }
        return regionRepository.findByParentIdAndRegionLevelAndActiveTrueOrderByNameAsc(province.getId(), (short) 2);
    }

    @Transactional
    public Person savePerson(Person person) {
        User currentUser = dataAccessService.requireCurrentUserReference();
        if (person.getId() != null) {
            Person existingPerson = personRepository.findById(person.getId()).orElseThrow();
            dataAccessService.assertCanAccessCreatedBy("person", existingPerson.getCreatedBy());
            person.setCreatedBy(existingPerson.getCreatedBy());
        } else {
            person.setCreatedBy(currentUser);
        }
        assertCanUseCompany(person.getCompany());
        person.setUpdatedBy(currentUser);
        return personRepository.save(person);
    }

    @Transactional
    public void deletePerson(Person person) {
        Person existingPerson = personRepository.findById(person.getId()).orElseThrow();
        dataAccessService.assertCanAccessCreatedBy("person", existingPerson.getCreatedBy());
        personRepository.delete(existingPerson);
    }

    @Transactional
    public Company saveCompany(Company company) {
        User currentUser = dataAccessService.requireCurrentUserReference();
        if (company.getId() != null) {
            Company existingCompany = companyRepository.findById(company.getId()).orElseThrow();
            dataAccessService.assertCanAccessCreatedBy("organization", existingCompany.getCreatedBy());
            company.setCreatedBy(existingCompany.getCreatedBy());
        } else {
            company.setCreatedBy(currentUser);
        }
        validateCompanyRegions(company);
        company.setUpdatedBy(currentUser);
        return companyRepository.save(company);
    }

    @Transactional
    public void deleteCompany(Company company) {
        Company existingCompany = companyRepository.findById(company.getId()).orElseThrow();
        dataAccessService.assertCanAccessCreatedBy("organization", existingCompany.getCreatedBy());
        companyRepository.delete(existingCompany);
    }

    private void assertCanUseCompany(Company company) {
        if (company == null || company.getId() == null) {
            return;
        }
        Company existingCompany = companyRepository.findById(company.getId()).orElseThrow();
        dataAccessService.assertCanAccessCreatedBy("organization", existingCompany.getCreatedBy());
    }

    private void validateCompanyRegions(Company company) {
        if (company.getCountry() == null || company.getCountry().getId() == null) {
            throw new IllegalArgumentException("Country is required");
        }
        if (company.getProvince() == null || company.getProvince().getId() == null) {
            throw new IllegalArgumentException("Province is required");
        }
        if (company.getCity() == null || company.getCity().getId() == null) {
            throw new IllegalArgumentException("City / Regency is required");
        }

        Country country = countryRepository.findById(company.getCountry().getId()).orElseThrow();
        Region province = regionRepository.findById(company.getProvince().getId()).orElseThrow();
        Region city = regionRepository.findById(company.getCity().getId()).orElseThrow();

        if (!Boolean.TRUE.equals(country.getActive())) {
            throw new IllegalArgumentException("Country is inactive");
        }
        if (!Boolean.TRUE.equals(province.getActive())
                || !Short.valueOf((short) 1).equals(province.getRegionLevel())
                || province.getCountry() == null
                || !Objects.equals(province.getCountry().getId(), country.getId())) {
            throw new IllegalArgumentException("Province must belong to the selected country");
        }
        if (!Boolean.TRUE.equals(city.getActive())
                || !Short.valueOf((short) 2).equals(city.getRegionLevel())
                || city.getParent() == null
                || !Objects.equals(city.getParent().getId(), province.getId())) {
            throw new IllegalArgumentException("City / Regency must belong to the selected province");
        }

        company.setCountry(country);
        company.setProvince(province);
        company.setCity(city);
    }
}
