package com.blusimian.blusimian.entityModel;

import java.util.regex.Pattern;

/**
 * The data type of a field's value -- shared vocabulary for built-in (standard) and
 * custom fields alike, so both halves of a record speak one type language.
 *
 * Each constant declares three things, and deliberately only three:
 *
 *   * pool     -- which slot pool a CUSTOM field of this type is allocated from.
 *                 TEXT/URL/PHONE/EMAIL/PICKLIST share the text pool; the DB CHECK constraint
 *                 ck_cfd_type_matches_slot enforces the same mapping in SQL, so a
 *                 NUMBER field parked in a text column is unrepresentable. null means
 *                 the type is built-in only (REFERENCE, COUNTRY): it has no pool, so it cannot
 *                 be the type of a custom field.
 *   * jsonType -- the Java type Jackson parses this field's JSON value into, which is
 *                 what accepts() gates on. Note DATE travels as a String: the ISO text
 *                 is only turned into a LocalDate during conversion. REFERENCE travels
 *                 as the target record's id, a String turned into a UUID the same way.
 *   * format   -- an optional pattern a non-blank String value must match (EMAIL). null
 *                 for every type whose JSON type and conversion already say it all.
 *
 * What is NOT here: the Java type a value converts to, and the column's length cap.
 * Both are facts about the storage column rather than the type, so both live on
 * CustomFieldSlots.SlotAccessor, generated from the column declaration itself and
 * therefore unable to drift from the schema. (A built-in field's cap comes from its own
 * column via FieldDescriptor.withMaxLength, for the same reason.)
 *
 * The enum also drives how the frontend renders a field.
 *
 * Like EntityType, the constant names are the source of truth: every type with a pool
 * must match the data_type CHECK constraint in V1__init.sql (Hibernate maps the enum
 * @STRING by its name()). REFERENCE and COUNTRY are deliberately missing from that CHECK.
 */
public enum FieldDataType {

    TEXT(CustomFieldSlots.POOL_TEXT, String.class, null),
    URL(CustomFieldSlots.POOL_TEXT, String.class, null),
    PHONE(CustomFieldSlots.POOL_TEXT, String.class, null),
    // Deliberately loose (something@something): no stricter than the bean-validation
    // @Email it replaced on contacts, so no address that used to save is now rejected.
    EMAIL(CustomFieldSlots.POOL_TEXT, String.class, Pattern.compile("[^@\\s]+@[^@\\s]+")),
    // One code from the field's picklist (app.picklist_values). Which codes are allowed is
    // tenant data, so RecordMapper checks it against PicklistLookup, not here.
    PICKLIST(CustomFieldSlots.POOL_TEXT, String.class, null),
    LONGTEXT(CustomFieldSlots.POOL_LONGTEXT, String.class, null),
    NUMBER(CustomFieldSlots.POOL_NUM, Number.class, null),
    BOOLEAN(CustomFieldSlots.POOL_BOOL, Boolean.class, null),
    DATE(CustomFieldSlots.POOL_DATE, String.class, null),
    REFERENCE(null, String.class, null),
    // An ISO 3166-1 alpha-2 code ("NL"). Built-in only, like REFERENCE: it exists so a
    // client can offer a country picker instead of a two-letter text box. Only the JSON
    // type is checked here; the two-character cap comes from the field's own column.
    COUNTRY(null, String.class, null);

    private final String pool;
    private final Class<?> jsonType;
    private final Pattern format;

    FieldDataType(String pool, Class<?> jsonType, Pattern format) {
        this.pool = pool;
        this.jsonType = jsonType;
        this.format = format;
    }

    /**
     * The slot pool prefix a custom field of this type allocates from, e.g. "cf_text".
     * null for a built-in-only type, which no custom field can have.
     */
    public String pool() {
        return pool;
    }

    /**
     * Whether a converted value is well-formed for this type. Only non-blank Strings of
     * a type with a format are checked: null and "" both mean "no value", which is the
     * required check's business, not this one's.
     */
    public boolean wellFormed(Object value) {
        return format == null
                || !(value instanceof String text)
                || text.isBlank()
                || format.matcher(text).matches();
    }

    /**
     * Whether a raw JSON value (as Jackson parses it: String, Number, Boolean) has the
     * right JSON type for this field. Checked before conversion, so a number isn't
     * silently stringified into a TEXT field.
     */
    public boolean accepts(Object value) {
        return jsonType.isInstance(value);
    }
}
