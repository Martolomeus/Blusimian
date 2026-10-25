package com.blusimian.blusimian.companies;

import com.blusimian.blusimian.companies.exception.CompanyNotFoundException;
import com.blusimian.blusimian.entityModel.RecordMapper;
import com.blusimian.blusimian.entityModel.StandardFieldProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The API speaks one flat field map (built-in + custom, no standard/custom split). This
 * service owns only the company-specific parts -- fetch/persist/delete -- and delegates
 * the field mapping to RecordMapper: toRecord()/toRecords() for the read shape, apply()
 * to validate and merge an incoming map onto the entity.
 * fieldProvider is the company's own StandardFieldProvider, injected so both directions
 * stay type-safe.
 *
 * @Transactional makes each call run in one transaction (the mapper reads custom field
 * definitions inside it); writes saveAndFlush so the @Creation/@UpdateTimestamp values
 * are populated before toRecord reads them back.
 */
@Service
public class CompanyService {

    private final CompanyRepository repository;
    private final RecordMapper recordMapper;
    private final StandardFieldProvider<Company> fieldProvider;

    public CompanyService(CompanyRepository repository, RecordMapper recordMapper,
                          CompanyStandardFieldProvider fieldProvider) {
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
        Company company = repository.findById(id).orElseThrow(CompanyNotFoundException::new);
        return recordMapper.toRecord(company, fieldProvider);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> values) {
        Company company = new Company();
        recordMapper.apply(company, fieldProvider, values);
        return recordMapper.toRecord(repository.saveAndFlush(company), fieldProvider);
    }

    @Transactional
    public Map<String, Object> update(UUID id, Map<String, Object> values) {
        Company company = repository.findById(id).orElseThrow(CompanyNotFoundException::new);
        recordMapper.apply(company, fieldProvider, values);
        return recordMapper.toRecord(repository.saveAndFlush(company), fieldProvider);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new CompanyNotFoundException();
        }
        repository.deleteById(id);
    }
}
