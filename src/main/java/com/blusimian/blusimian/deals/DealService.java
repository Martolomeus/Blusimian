package com.blusimian.blusimian.deals;

import com.blusimian.blusimian.deals.exception.DealNotFoundException;
import com.blusimian.blusimian.deals.exception.InvalidDealCustomerException;
import com.blusimian.blusimian.entityModel.RecordMapper;
import com.blusimian.blusimian.entityModel.StandardFieldProvider;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The API speaks one flat field map (built-in + custom, no standard/custom split). Like
 * CompanyService, this service owns only the deal-specific parts -- fetch/persist/delete
 * -- and delegates the field mapping to RecordMapper: toRecord()/toRecords() for the read
 * shape, apply() to validate and merge an incoming map onto the entity.
 *
 * The one deal-specific rule is the customer reference: RecordMapper only checks that
 * customerId is a well-formed id, so whether that company exists is left to the
 * customer_id foreign key and translated here (see save).
 *
 * @Transactional makes each call run in one transaction (the mapper reads custom field
 * definitions inside it).
 */
@Service
public class DealService {

    private final DealRepository repository;
    private final RecordMapper recordMapper;
    private final StandardFieldProvider<Deal> fieldProvider;

    public DealService(DealRepository repository, RecordMapper recordMapper,
                       DealStandardFieldProvider fieldProvider) {
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
        Deal deal = repository.findById(id).orElseThrow(DealNotFoundException::new);
        return recordMapper.toRecord(deal, fieldProvider);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> values) {
        Deal deal = new Deal();
        recordMapper.apply(deal, fieldProvider, values);
        return recordMapper.toRecord(save(deal), fieldProvider);
    }

    @Transactional
    public Map<String, Object> update(UUID id, Map<String, Object> values) {
        Deal deal = repository.findById(id).orElseThrow(DealNotFoundException::new);
        recordMapper.apply(deal, fieldProvider, values);
        return recordMapper.toRecord(save(deal), fieldProvider);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new DealNotFoundException();
        }
        repository.deleteById(id);
    }

    private Deal save(Deal deal) {
        try {
            // saveAndFlush (not save): force the write now, inside this try, so a bad
            // customer_id hits the FK here as a DataIntegrityViolationException and
            // becomes a clean 400. Plain save() would defer to commit and escape as 500.
            // It also populates the @Creation/@UpdateTimestamp values toRecord reads back.
            return repository.saveAndFlush(deal);
        } catch (DataIntegrityViolationException e) {
            // Translate only the customer_id FK. Anything else (a number too large for
            // its column, say) keeps the platform's generic handling rather than being
            // misreported as an unknown customer.
            if (e.getCause() instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().contains("customer_id")) {
                throw new InvalidDealCustomerException(deal.getCustomerId());
            }
            throw e;
        }
    }
}
