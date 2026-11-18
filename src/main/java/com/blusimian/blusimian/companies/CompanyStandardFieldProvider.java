package com.blusimian.blusimian.companies;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.entityModel.FieldDescriptor;
import com.blusimian.blusimian.entityModel.StandardFieldProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Declares the company's standard (built-in) fields for the record mapper and the
 * schema bundle. This is the companies -> entityModel edge: companies owns the
 * knowledge of its own fields and contributes it by implementing;
 * entityModel collects it generically, without ever importing companies.
 *
 * Keys are the company's JSON property names (a STANDARD placement references a field
 * by the key under which its value appears in the record JSON, §4) -- so the frontend
 * reads record[key] with no snake/camel translation. They happen to differ from the DB
 * column only where the column is multi-word (legal_name -> legalName). Labels and
 * dataTypes are code-defined for now. Only the fields meant to be placeable on a layout
 * are listed -- not surrogate ids or timestamps.
 *
 * The value type passed to of() must match the entity getter/setter's type; name is
 * required because its column is NOT NULL. withMaxLength mirrors each column's varchar
 * cap from V1__init.sql, so an over-long value comes back as a 400 with a field error
 * instead of a Postgres constraint violation. description and website carry no cap --
 * their columns are unbounded `text`.
 *
 * type, industry and employeeRange are PICKLIST fields: each holds a code from the
 * tenant's list in app.picklist_values, seeded from public.standard_picklist_values.
 */
@Component
public class CompanyStandardFieldProvider implements StandardFieldProvider<Company> {

    private static final List<FieldDescriptor<Company>> FIELDS = List.of(
            FieldDescriptor.of("name", "Name", FieldDataType.TEXT, String.class,
                    Company::getName, Company::setName).asRequired().withMaxLength(255),
            FieldDescriptor.of("legalName", "Legal Name", FieldDataType.TEXT, String.class,
                    Company::getLegalName, Company::setLegalName).withMaxLength(255),
            FieldDescriptor.of("type", "Type", FieldDataType.PICKLIST, String.class,
                    Company::getType, Company::setType).withMaxLength(100),
            FieldDescriptor.of("industry", "Industry", FieldDataType.PICKLIST, String.class,
                    Company::getIndustry, Company::setIndustry).withMaxLength(100),
            FieldDescriptor.of("description", "Description", FieldDataType.TEXT, String.class,
                    Company::getDescription, Company::setDescription),
            FieldDescriptor.of("employeeRange", "Employee Range", FieldDataType.PICKLIST, String.class,
                    Company::getEmployeeRange, Company::setEmployeeRange).withMaxLength(50),
            FieldDescriptor.of("website", "Website", FieldDataType.URL, String.class,
                    Company::getWebsite, Company::setWebsite),
            FieldDescriptor.of("phone", "Phone", FieldDataType.PHONE, String.class,
                    Company::getPhone, Company::setPhone).withMaxLength(50),
            FieldDescriptor.of("phoneAlt", "Alternate Phone", FieldDataType.PHONE, String.class,
                    Company::getPhoneAlt, Company::setPhoneAlt).withMaxLength(50),
            FieldDescriptor.of("vatNumber", "VAT Number", FieldDataType.TEXT, String.class,
                    Company::getVatNumber, Company::setVatNumber).withMaxLength(50)
    );

    @Override
    public EntityType entityType() {
        return EntityType.COMPANY;
    }

    @Override
    public List<FieldDescriptor<Company>> fields() {
        return FIELDS;
    }
}
