package com.blusimian.blusimian.companyContacts;

import com.blusimian.blusimian.companies.Company;
import com.blusimian.blusimian.companies.CompanyRepository;
import com.blusimian.blusimian.companies.exception.CompanyNotFoundException;
import com.blusimian.blusimian.companyContacts.exception.CompanyContactNotFoundException;
import com.blusimian.blusimian.contacts.Contact;
import com.blusimian.blusimian.contacts.ContactRepository;
import com.blusimian.blusimian.contacts.exception.ContactNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for company<->contact links. Reads go through the derived finders;
 * the EAGER @ManyToOne associations mean company and contact are loaded with each
 * link, so mapping to CompanyContactResponse just reads their names.
 *
 * On write it turns the request's ids into real Company/Contact entities, which
 * both satisfies the @ManyToOne and validates they exist (clean 404s instead of a
 * raw foreign-key error).
 */
@Service
public class CompanyContactService {

    private final CompanyContactRepository repository;
    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;

    public CompanyContactService(CompanyContactRepository repository,
                                 CompanyRepository companyRepository,
                                 ContactRepository contactRepository) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
    }

    /**
     * List links, optionally filtered to one side. Pass companyId to get an
     * account's contacts, or contactId to get a contact's accounts; pass neither
     * for all links.
     */
    @Transactional(readOnly = true)
    public List<CompanyContactResponse> list(UUID companyId, UUID contactId) {
        List<CompanyContact> links;
        if (companyId != null) {
            links = repository.findByCompanyId(companyId);
        } else if (contactId != null) {
            links = repository.findByContactId(contactId);
        } else {
            links = repository.findAll();
        }
        return links.stream().map(CompanyContactResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CompanyContactResponse get(UUID id) {
        return CompanyContactResponse.from(
                repository.findById(id).orElseThrow(CompanyContactNotFoundException::new));
    }

    @Transactional
    public CompanyContactResponse create(CompanyContactRequest request) {
        CompanyContact link = new CompanyContact();
        apply(link, request);
        return CompanyContactResponse.from(repository.save(link));
    }

    @Transactional
    public CompanyContactResponse update(UUID id, CompanyContactRequest request) {
        CompanyContact link = repository.findById(id).orElseThrow(CompanyContactNotFoundException::new);
        apply(link, request);
        return CompanyContactResponse.from(repository.save(link));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new CompanyContactNotFoundException();
        }
        repository.deleteById(id);
    }

    private void apply(CompanyContact link, CompanyContactRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(CompanyNotFoundException::new);
        Contact contact = contactRepository.findById(request.contactId())
                .orElseThrow(ContactNotFoundException::new);
        link.setCompany(company);
        link.setContact(contact);
    }
}
