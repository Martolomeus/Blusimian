package com.blusimian.blusimian.contacts;

import com.blusimian.blusimian.contacts.exception.ContactNotFoundException;
import com.blusimian.blusimian.entityModel.RecordMapper;
import com.blusimian.blusimian.entityModel.StandardFieldProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The API speaks one flat field map (built-in + custom, no standard/custom split). Like
 * CompanyService, this service owns only the contact-specific parts -- fetch/persist/
 * delete -- and delegates the field mapping to RecordMapper: toRecord()/toRecords() for
 * the read shape, apply() to validate and merge an incoming map onto the entity.
 *
 * @Transactional makes each call run in one transaction (the mapper reads custom field
 * definitions inside it); writes saveAndFlush so the @Creation/@UpdateTimestamp values
 * are populated before toRecord reads them back.
 */
@Service
public class ContactService {

    private final ContactRepository repository;
    private final RecordMapper recordMapper;
    private final StandardFieldProvider<Contact> fieldProvider;

    public ContactService(ContactRepository repository, RecordMapper recordMapper,
                          ContactStandardFieldProvider fieldProvider) {
        this.repository = repository;
        this.recordMapper = recordMapper;
        this.fieldProvider = fieldProvider;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return recordMapper.toRecords(repository.findAll(), fieldProvider);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        Contact contact = repository.findById(id).orElseThrow(ContactNotFoundException::new);
        return recordMapper.toRecord(contact, fieldProvider);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> values) {
        Contact contact = new Contact();
        recordMapper.apply(contact, fieldProvider, values);
        return recordMapper.toRecord(repository.saveAndFlush(contact), fieldProvider);
    }

    @Transactional
    public Map<String, Object> update(UUID id, Map<String, Object> values) {
        Contact contact = repository.findById(id).orElseThrow(ContactNotFoundException::new);
        recordMapper.apply(contact, fieldProvider, values);
        return recordMapper.toRecord(repository.saveAndFlush(contact), fieldProvider);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ContactNotFoundException();
        }
        repository.deleteById(id);
    }
}
