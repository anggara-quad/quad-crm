package com.quadteknologi.crm.service;

import com.quadteknologi.crm.domain.entity.Company;
import com.quadteknologi.crm.domain.entity.Person;
import com.quadteknologi.crm.domain.entity.User;
import com.quadteknologi.crm.domain.repository.CompanyRepository;
import com.quadteknologi.crm.domain.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContactService {

    private final CompanyRepository companyRepository;
    private final PersonRepository personRepository;
    private final DataAccessService dataAccessService;

    public ContactService(
            CompanyRepository companyRepository,
            PersonRepository personRepository,
            DataAccessService dataAccessService) {
        this.companyRepository = companyRepository;
        this.personRepository = personRepository;
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
}
