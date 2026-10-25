package com.blusimian.blusimian.deals;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.entityModel.FieldDescriptor;
import com.blusimian.blusimian.entityModel.StandardFieldProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Declares the deal's standard (built-in) fields for the record mapper and the schema
 * bundle -- the deals -> entityModel edge, mirroring CompanyStandardFieldProvider.
 *
 * dealName is required because its column is NOT NULL, and capped at the column's
 * varchar(255). customerId is a REFERENCE to a company: the mapper checks it is a
 * well-formed id, the schema tells the client which entity it points at, and the
 * customer_id foreign key (translated in DealService) checks the company exists.
 */
@Component
public class DealStandardFieldProvider implements StandardFieldProvider<Deal> {

    private static final List<FieldDescriptor<Deal>> FIELDS = List.of(
            FieldDescriptor.of("dealName", "Deal Name", FieldDataType.TEXT, String.class,
                    Deal::getDealName, Deal::setDealName).asRequired().withMaxLength(255),
            FieldDescriptor.of("amount", "Amount", FieldDataType.NUMBER, BigDecimal.class,
                    Deal::getAmount, Deal::setAmount),
            FieldDescriptor.reference("customerId", "Customer", EntityType.COMPANY,
                    Deal::getCustomerId, Deal::setCustomerId)
    );

    @Override
    public EntityType entityType() {
        return EntityType.DEAL;
    }

    @Override
    public List<FieldDescriptor<Deal>> fields() {
        return FIELDS;
    }
}
