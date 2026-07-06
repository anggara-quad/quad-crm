package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.Company;
import com.quadteknologi.crm.domain.entity.Country;
import com.quadteknologi.crm.domain.entity.Lead;
import com.quadteknologi.crm.domain.entity.Opportunity;
import com.quadteknologi.crm.domain.entity.Person;
import com.quadteknologi.crm.domain.entity.Region;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.domain.repository.CompanyRepository;
import com.quadteknologi.crm.domain.repository.CountryRepository;
import com.quadteknologi.crm.domain.repository.LeadRepository;
import com.quadteknologi.crm.domain.repository.OpportunityRepository;
import com.quadteknologi.crm.domain.repository.PersonRepository;
import com.quadteknologi.crm.domain.repository.RegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ContactService {

    private final CompanyRepository companyRepository;
    private final PersonRepository personRepository;
    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;
    private final CountryRepository countryRepository;
    private final RegionRepository regionRepository;
    private final DataAccessService dataAccessService;

    public ContactService(
            CompanyRepository companyRepository,
            PersonRepository personRepository,
            LeadRepository leadRepository,
            OpportunityRepository opportunityRepository,
            CountryRepository countryRepository,
            RegionRepository regionRepository,
            DataAccessService dataAccessService) {
        this.companyRepository = companyRepository;
        this.personRepository = personRepository;
        this.leadRepository = leadRepository;
        this.opportunityRepository = opportunityRepository;
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
    public List<Company> findOrganizationFilterCompanies() {
        return findAllCompanies();
    }

    @Transactional(readOnly = true)
    public List<PersonContactSummaryDto> findPersonSummaries() {
        return personRepository.summarizePersons(visibleCreatedById()).stream()
                .map(this::mapPersonSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CompanyContactSummaryDto> findCompanySummaries() {
        return companyRepository.summarizeCompanies(visibleCreatedById()).stream()
                .map(this::mapCompanySummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countPersons(LocalDateTime from, LocalDateTime to, Long createdById) {
        if (createdById == null) {
            return personRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);
        }
        return personRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndCreatedById(from, to, createdById);
    }

    @Transactional(readOnly = true)
    public long countCompanies(LocalDateTime from, LocalDateTime to, Long createdById) {
        if (createdById == null) {
            return companyRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);
        }
        return companyRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndCreatedById(from, to, createdById);
    }

    @Transactional(readOnly = true)
    public List<Lead> findLeadsForPerson(Person person) {
        if (person == null || person.getId() == null) {
            return List.of();
        }
        Long createdById = visibleCreatedById();
        return createdById == null
                ? leadRepository.findByPersonIdOrderByCreatedAtDesc(person.getId())
                : leadRepository.findByPersonIdAndCreatedByIdOrderByCreatedAtDesc(person.getId(), createdById);
    }

    @Transactional(readOnly = true)
    public List<Lead> findLeadsForCompany(Company company) {
        if (company == null || company.getId() == null) {
            return List.of();
        }
        Long createdById = visibleCreatedById();
        return createdById == null
                ? leadRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId())
                : leadRepository.findByCompanyIdAndCreatedByIdOrderByCreatedAtDesc(company.getId(), createdById);
    }

    @Transactional(readOnly = true)
    public List<Opportunity> findOpportunitiesForPerson(Person person) {
        if (person == null || person.getId() == null) {
            return List.of();
        }
        Long createdById = visibleCreatedById();
        return createdById == null
                ? opportunityRepository.findByPersonIdOrderByCreatedAtDesc(person.getId())
                : opportunityRepository.findByPersonIdAndCreatedByIdOrderByCreatedAtDesc(person.getId(), createdById);
    }

    @Transactional(readOnly = true)
    public List<Opportunity> findOpportunitiesForCompany(Company company) {
        if (company == null || company.getId() == null) {
            return List.of();
        }
        Long createdById = visibleCreatedById();
        return createdById == null
                ? opportunityRepository.findByCompanyIdOrderByCreatedAtDesc(company.getId())
                : opportunityRepository.findByCompanyIdAndCreatedByIdOrderByCreatedAtDesc(company.getId(), createdById);
    }

    @Transactional(readOnly = true)
    public List<Person> findPersonsForCompany(Company company) {
        if (company == null || company.getId() == null) {
            return List.of();
        }
        Long createdById = visibleCreatedById();
        return createdById == null
                ? personRepository.findByCompanyIdOrderByFullNameAsc(company.getId())
                : personRepository.findByCompanyIdAndCreatedByIdOrderByFullNameAsc(company.getId(), createdById);
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

    private Long visibleCreatedById() {
        return dataAccessService.isSalesScope() ? dataAccessService.requireCurrentUserId() : null;
    }

    private PersonContactSummaryDto mapPersonSummary(Object[] row) {
        return new PersonContactSummaryDto(
                longValue(row[0]),
                uuidValue(row[1]),
                stringValue(row[2]),
                stringValue(row[3]),
                stringValue(row[4]),
                stringValue(row[5]),
                stringValue(row[6]),
                stringValue(row[7]),
                primitiveLong(row[8]),
                primitiveLong(row[9]),
                primitiveLong(row[10]),
                primitiveLong(row[11]),
                decimalValue(row[12]),
                decimalValue(row[13]),
                dateTimeValue(row[14]));
    }

    private CompanyContactSummaryDto mapCompanySummary(Object[] row) {
        return new CompanyContactSummaryDto(
                longValue(row[0]),
                uuidValue(row[1]),
                stringValue(row[2]),
                stringValue(row[3]),
                stringValue(row[4]),
                stringValue(row[5]),
                stringValue(row[6]),
                stringValue(row[7]),
                stringValue(row[8]),
                stringValue(row[9]),
                primitiveLong(row[10]),
                primitiveLong(row[11]),
                primitiveLong(row[12]),
                primitiveLong(row[13]),
                primitiveLong(row[14]),
                decimalValue(row[15]),
                decimalValue(row[16]),
                dateTimeValue(row[17]));
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private long primitiveLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private UUID uuidValue(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return value == null ? null : UUID.fromString(value.toString());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private LocalDateTime dateTimeValue(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }
}
