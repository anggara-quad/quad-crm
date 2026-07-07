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
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.quadteknologi.crm.util.TextUtils.trimToNull;
import static com.quadteknologi.crm.util.TextUtils.valueOrFallback;

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

    @Transactional(readOnly = true)
    public ContactImportPreview previewContactImport(InputStream inputStream) {
        List<ContactImportRow> rows = readContactImportRows(inputStream);
        if (rows.isEmpty()) {
            return new ContactImportPreview(List.of(), 0, 0, 0, 1);
        }

        ImportLookup lookup = buildImportLookup();
        Map<String, ContactImportRow> firstOrganizationByKey = new LinkedHashMap<>();
        Set<String> importPersonKeys = new java.util.LinkedHashSet<>();
        List<ContactImportRow> validatedRows = new ArrayList<>();

        for (ContactImportRow row : rows) {
            List<String> errors = new ArrayList<>();
            validateRequired(row, errors);

            String organizationKey = normalizeKey(row.organizationName());
            Company existingCompany = lookup.companiesByName().get(organizationKey);
            ContactImportRow firstOrganization = firstOrganizationByKey.putIfAbsent(organizationKey, row);
            if (firstOrganization != null && !sameOrganization(firstOrganization, row)) {
                errors.add("Duplicate organization name has different organization data");
            }

            Country country = lookup.countriesByName().get(normalizeKey(row.country()));
            Region province = null;
            Region city = null;
            if (country == null) {
                errors.add("Country not found or inactive: " + valueOrFallback(row.country(), "-"));
            } else {
                province = lookup.provincesByCountryIdAndName().get(regionKey(country.getId(), row.province()));
                if (province == null) {
                    errors.add("Province not found under " + country.getName() + ": "
                            + valueOrFallback(row.province(), "-"));
                } else {
                    city = lookup.citiesByProvinceIdAndName().get(regionKey(province.getId(), row.city()));
                    if (city == null) {
                        errors.add("City not found under " + province.getName() + ": "
                                + valueOrFallback(row.city(), "-"));
                    }
                }
            }

            String personKey = personImportKey(row);
            if (personKey == null) {
                errors.add("Person name or email is required");
            } else {
                if (lookup.personKeys().contains(personKey)) {
                    errors.add("Person already exists");
                }
                if (!importPersonKeys.add(personKey)) {
                    errors.add("Duplicate person in import file");
                }
            }

            validatedRows.add(row.withValidation(
                    errors,
                    existingCompany == null ? null : existingCompany.getId(),
                    country == null ? null : country.getId(),
                    province == null ? null : province.getId(),
                    city == null ? null : city.getId()));
        }

        long newOrganizations = validatedRows.stream()
                .filter(row -> row.valid() && row.existingCompanyId() == null)
                .map(row -> normalizeKey(row.organizationName()))
                .distinct()
                .count();
        long existingOrganizations = validatedRows.stream()
                .filter(row -> row.valid() && row.existingCompanyId() != null)
                .map(ContactImportRow::existingCompanyId)
                .distinct()
                .count();
        long newPersons = validatedRows.stream().filter(ContactImportRow::valid).count();
        long errors = validatedRows.stream().filter(row -> !row.valid()).count();

        return new ContactImportPreview(validatedRows, newOrganizations, existingOrganizations, newPersons, errors);
    }

    @Transactional
    public ContactImportResult saveContactImport(ContactImportPreview preview) {
        if (preview == null || !preview.valid()) {
            throw new IllegalArgumentException("Import file still has validation errors");
        }

        Map<String, Company> importedCompanies = new LinkedHashMap<>();
        long createdCompanies = 0;
        long createdPersons = 0;

        for (ContactImportRow row : preview.rows()) {
            Company company;
            if (row.existingCompanyId() != null) {
                company = companyRepository.findById(row.existingCompanyId()).orElseThrow();
                dataAccessService.assertCanAccessCreatedBy("organization", company.getCreatedBy());
            } else {
                String organizationKey = normalizeKey(row.organizationName());
                company = importedCompanies.get(organizationKey);
                if (company == null) {
                    company = new Company();
                    company.setName(row.organizationName());
                    company.setIndustry(row.industry());
                    company.setEmail(row.organizationEmail());
                    company.setPhone(row.organizationPhone());
                    company.setCountry(countryRepository.findById(row.countryId()).orElseThrow());
                    company.setProvince(regionRepository.findById(row.provinceId()).orElseThrow());
                    company.setCity(regionRepository.findById(row.cityId()).orElseThrow());
                    company.setAddress(row.address());
                    company = saveCompany(company);
                    importedCompanies.put(organizationKey, company);
                    createdCompanies++;
                }
            }

            Person person = new Person();
            person.setCompany(company);
            person.setFullName(row.personName());
            person.setJobTitle(row.personTitle());
            person.setEmail(row.personEmail());
            person.setPhone(row.personPhone());
            person.setWhatsapp(row.personPhone());
            savePerson(person);
            createdPersons++;
        }

        return new ContactImportResult(createdCompanies, createdPersons);
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

    private List<ContactImportRow> readContactImportRows(InputStream inputStream) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<ContactImportRow> rows = new ArrayList<>();
            for (int index = 2; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null || isImportRowEmpty(row, formatter)) {
                    continue;
                }
                rows.add(new ContactImportRow(
                        index + 1,
                        cell(row, 0, formatter),
                        cell(row, 1, formatter),
                        cell(row, 2, formatter),
                        cell(row, 3, formatter),
                        cell(row, 4, formatter),
                        cell(row, 5, formatter),
                        cell(row, 6, formatter),
                        cell(row, 7, formatter),
                        cell(row, 8, formatter),
                        cell(row, 9, formatter),
                        cell(row, 10, formatter),
                        cell(row, 11, formatter),
                        List.of(),
                        null,
                        null,
                        null,
                        null));
            }
            return rows;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read Excel file");
        }
    }

    private boolean isImportRowEmpty(Row row, DataFormatter formatter) {
        for (int index = 0; index < 12; index++) {
            if (trimToNull(cell(row, index, formatter)) != null) {
                return false;
            }
        }
        return true;
    }

    private String cell(Row row, int index, DataFormatter formatter) {
        if (row.getCell(index) == null) {
            return null;
        }
        return trimToNull(formatter.formatCellValue(row.getCell(index)));
    }

    private void validateRequired(ContactImportRow row, List<String> errors) {
        require(row.organizationName(), "Organization name", errors);
        require(row.industry(), "Industry", errors);
        require(row.organizationEmail(), "Organization email", errors);
        require(row.organizationPhone(), "Organization phone", errors);
        require(row.country(), "Country", errors);
        require(row.province(), "Province", errors);
        require(row.city(), "City", errors);
        require(row.address(), "Address", errors);
        require(row.personName(), "Person name", errors);
        require(row.personTitle(), "Person title", errors);
        require(row.personEmail(), "Person email", errors);
        require(row.personPhone(), "Person phone", errors);
    }

    private void require(String value, String label, List<String> errors) {
        if (trimToNull(value) == null) {
            errors.add(label + " is required");
        }
    }

    private ImportLookup buildImportLookup() {
        List<Country> countries = findActiveCountries();
        Map<String, Country> countriesByName = countries.stream()
                .collect(Collectors.toMap(country -> normalizeKey(country.getName()), Function.identity(),
                        (current, replacement) -> current, LinkedHashMap::new));

        Map<String, Region> provincesByCountryIdAndName = new LinkedHashMap<>();
        Map<String, Region> citiesByProvinceIdAndName = new LinkedHashMap<>();
        for (Country country : countries) {
            for (Region province : findActiveProvinces(country)) {
                provincesByCountryIdAndName.put(regionKey(country.getId(), province.getName()), province);
                for (Region city : findActiveCities(province)) {
                    citiesByProvinceIdAndName.put(regionKey(province.getId(), city.getName()), city);
                }
            }
        }

        Map<String, Company> companiesByName = findAllCompanies().stream()
                .collect(Collectors.toMap(company -> normalizeKey(company.getName()), Function.identity(),
                        (current, replacement) -> current, LinkedHashMap::new));

        Set<String> personKeys = findAllPersons().stream()
                .map(this::personExistingKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        return new ImportLookup(countriesByName, provincesByCountryIdAndName, citiesByProvinceIdAndName,
                companiesByName, personKeys);
    }

    private boolean sameOrganization(ContactImportRow left, ContactImportRow right) {
        return Objects.equals(normalizeKey(left.industry()), normalizeKey(right.industry()))
                && Objects.equals(normalizeKey(left.organizationEmail()), normalizeKey(right.organizationEmail()))
                && Objects.equals(normalizeKey(left.organizationPhone()), normalizeKey(right.organizationPhone()))
                && Objects.equals(normalizeKey(left.country()), normalizeKey(right.country()))
                && Objects.equals(normalizeKey(left.province()), normalizeKey(right.province()))
                && Objects.equals(normalizeKey(left.city()), normalizeKey(right.city()))
                && Objects.equals(normalizeKey(left.address()), normalizeKey(right.address()));
    }

    private String personExistingKey(Person person) {
        if (trimToNull(person.getEmail()) != null) {
            return "email:" + normalizeKey(person.getEmail());
        }
        return "name:" + normalizeKey(person.getFullName()) + "|company:" + normalizeKey(personCompanyName(person));
    }

    private String personImportKey(ContactImportRow row) {
        if (trimToNull(row.personEmail()) != null) {
            return "email:" + normalizeKey(row.personEmail());
        }
        if (trimToNull(row.personName()) == null || trimToNull(row.organizationName()) == null) {
            return null;
        }
        return "name:" + normalizeKey(row.personName()) + "|company:" + normalizeKey(row.organizationName());
    }

    private String personCompanyName(Person person) {
        return person.getCompany() == null ? null : person.getCompany().getName();
    }

    private String regionKey(Long parentId, String name) {
        return parentId + ":" + normalizeKey(name);
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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

    private record ImportLookup(
            Map<String, Country> countriesByName,
            Map<String, Region> provincesByCountryIdAndName,
            Map<String, Region> citiesByProvinceIdAndName,
            Map<String, Company> companiesByName,
            Set<String> personKeys) {
    }

    public record ContactImportPreview(
            List<ContactImportRow> rows,
            long newOrganizations,
            long existingOrganizations,
            long newPersons,
            long errors) {

        public boolean valid() {
            return !rows.isEmpty() && errors == 0;
        }
    }

    public record ContactImportRow(
            int rowNumber,
            String organizationName,
            String industry,
            String organizationEmail,
            String organizationPhone,
            String country,
            String province,
            String city,
            String address,
            String personName,
            String personTitle,
            String personEmail,
            String personPhone,
            List<String> errors,
            Long existingCompanyId,
            Long countryId,
            Long provinceId,
            Long cityId) {

        public boolean valid() {
            return errors == null || errors.isEmpty();
        }

        public String status() {
            if (!valid()) {
                return "Invalid";
            }
            return existingCompanyId == null ? "Ready - new organization" : "Ready - existing organization";
        }

        public String errorText() {
            return valid() ? "-" : String.join("; ", errors);
        }

        ContactImportRow withValidation(
                List<String> errors,
                Long existingCompanyId,
                Long countryId,
                Long provinceId,
                Long cityId) {
            return new ContactImportRow(
                    rowNumber,
                    organizationName,
                    industry,
                    organizationEmail,
                    organizationPhone,
                    country,
                    province,
                    city,
                    address,
                    personName,
                    personTitle,
                    personEmail,
                    personPhone,
                    List.copyOf(errors),
                    existingCompanyId,
                    countryId,
                    provinceId,
                    cityId);
        }
    }

    public record ContactImportResult(long createdOrganizations, long createdPersons) {
    }
}
