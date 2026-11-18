package com.blusimian.blusimian.contacts;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.entityModel.FieldDescriptor;
import com.blusimian.blusimian.entityModel.StandardFieldProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Declares the contact's standard (built-in) fields for the record mapper and the schema
 * bundle -- the contacts -> entityModel edge, mirroring CompanyStandardFieldProvider.
 *
 * Keys are the contact's JSON property names, which for the address differ from the DB
 * columns (address_street -> street, address_country -> countryCode). The address stays
 * five flat fields: there is no composite type, so a layout places each part by its own
 * key. countryCode is typed COUNTRY so a client renders it as a country picker, and
 * salutation is a PICKLIST (a code from the tenant's list in app.picklist_values).
 *
 * firstName and lastName are required because their columns are NOT NULL. withMaxLength
 * mirrors each column's cap from V1__init.sql, so an over-long value comes back as a 400
 * with a field error instead of a Postgres constraint violation. email carries no cap --
 * its column is unbounded text -- but its EMAIL type is format-checked on write.
 */
@Component
public class ContactStandardFieldProvider implements StandardFieldProvider<Contact> {

    private static final List<FieldDescriptor<Contact>> FIELDS = List.of(
            FieldDescriptor.of("salutation", "Salutation", FieldDataType.PICKLIST, String.class,
                    Contact::getSalutation, Contact::setSalutation).withMaxLength(50),
            FieldDescriptor.of("firstName", "First Name", FieldDataType.TEXT, String.class,
                    Contact::getFirstName, Contact::setFirstName).asRequired().withMaxLength(100),
            FieldDescriptor.of("lastName", "Last Name", FieldDataType.TEXT, String.class,
                    Contact::getLastName, Contact::setLastName).asRequired().withMaxLength(100),
            FieldDescriptor.of("email", "Email", FieldDataType.EMAIL, String.class,
                    Contact::getEmail, Contact::setEmail),
            FieldDescriptor.of("phone", "Phone", FieldDataType.PHONE, String.class,
                    Contact::getPhone, Contact::setPhone).withMaxLength(50),
            FieldDescriptor.of("street", "Street", FieldDataType.TEXT, String.class,
                    Contact::getStreet, Contact::setStreet).withMaxLength(255),
            FieldDescriptor.of("city", "City", FieldDataType.TEXT, String.class,
                    Contact::getCity, Contact::setCity).withMaxLength(100),
            FieldDescriptor.of("state", "State", FieldDataType.TEXT, String.class,
                    Contact::getState, Contact::setState).withMaxLength(100),
            FieldDescriptor.of("postalCode", "Postal Code", FieldDataType.TEXT, String.class,
                    Contact::getPostalCode, Contact::setPostalCode).withMaxLength(20),
            FieldDescriptor.of("countryCode", "Country", FieldDataType.COUNTRY, String.class,
                    Contact::getCountryCode, Contact::setCountryCode).withMaxLength(2)
    );

    @Override
    public EntityType entityType() {
        return EntityType.CONTACT;
    }

    @Override
    public List<FieldDescriptor<Contact>> fields() {
        return FIELDS;
    }
}
