POOLS = [
    ("text",     40, "String",     "cf_text",     'varchar(255)', True),
    ("num",      25, "BigDecimal", "cf_num",      'numeric(19,4)', False),
    ("bool",     25, "Boolean",    "cf_bool",     'boolean', False),
    ("date",     10, "LocalDate",  "cf_date",     'date', False),
    ("longtext",  5, "String",     "cf_longtext", 'text', False),
]

def camel(prefix, i):
    part = prefix.split("_")[1]
    return "cf" + part[0].upper() + part[1:] + f"{i:02d}"

fields, puts = [], []
for name, count, jtype, colprefix, sqltype, capped in POOLS:
    fields.append(f"    // ---- {name} pool: {count} slots ({sqltype}) ----")
    for i in range(1, count + 1):
        col = f"{colprefix}_{i:02d}"
        f = camel(colprefix, i)
        ann = f'    @Column(name = "{col}"' + (", length = 255)" if capped else ")")
        if name == "longtext":
            ann = "    @Basic(fetch = FetchType.LAZY)\n" + ann
        fields.append(f"{ann}\n    private {jtype} {f};\n")
        cap = "255" if capped else "null"
        puts.append(f'        put(map, "{col}", {jtype}.class, {cap}, '
                    f'CustomFieldSlots::get{f[0].upper()+f[1:]}, CustomFieldSlots::set{f[0].upper()+f[1:]});')
    fields.append("")

accessors = []
for name, count, jtype, colprefix, sqltype, capped in POOLS:
    for i in range(1, count + 1):
        f = camel(colprefix, i)
        U = f[0].upper() + f[1:]
        accessors.append(f"    public {jtype} get{U}() {{\n        return {f};\n    }}\n\n"
                         f"    public void set{U}({jtype} {f}) {{\n        this.{f} = {f};\n    }}\n")

pool_consts = "\n".join(
    f'    public static final String POOL_{n.upper()} = "{p}";' for n, c, j, p, s, cp in POOLS)

header = '''package com.blusimian.blusimian.entityModel;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * GENERATED -- do not hand-edit. Regenerate from scripts/gen_slots.py when a pool
 * is widened.
 *
 * The custom field slot pool, shared by every record entity: 40 text + 25 numeric
 * + 25 boolean + 10 date, plus 5 long-text. Embedded into each entity so all
 * record tables carry an identical pool, which is what keeps adding the next
 * entity cheap.
 *
 * A slot is meaningless on its own: app.custom_field_definitions.column_name says
 * which tenant field owns it, and the DB CHECK ck_cfd_type_matches_slot guarantees
 * the definition's data type matches the pool the slot came from. So a value read
 * out of a slot is always of the type its definition promises.
 *
 * Every field is a boxed type: null is a real value here, meaning "unset", which
 * the merge-patch write path depends on.
 *
 * ACCESSORS is the read/write half -- FieldDescriptor.forCustomField looks a slot
 * up by column name and closes over the pair, exactly the way a standard field
 * closes over Company::getName / Company::setName. Nothing above this class knows
 * a slot column exists.
 */
@Embeddable
public class CustomFieldSlots {

''' + pool_consts + '''

'''

body = "\n".join(fields) + "\n" + "\n".join(accessors)

registry = '''
    // ------------------------------------------------------------------
    // Slot accessor registry
    // ------------------------------------------------------------------

    /**
     * One slot's wiring: what type it holds, how long a value it accepts, and how to
     * read and write it. valueType and maxLength are generated from the column
     * declaration a few lines above, so neither can drift from the schema -- which is
     * why they live here rather than on FieldDataType.
     */
    public record SlotAccessor(
            String columnName,
            Class<?> valueType,
            Integer maxLength,
            Function<CustomFieldSlots, Object> getter,
            BiConsumer<CustomFieldSlots, Object> setter
    ) {
    }

    private static final Map<String, SlotAccessor> ACCESSORS = buildAccessors();

    /**
     * The slot for a column name, e.g. "cf_text_03". Throws rather than returning
     * null: a definition row naming a slot that doesn't exist means the catalog and
     * this class have drifted apart, which is a bug, not a request problem.
     */
    public static SlotAccessor accessor(String columnName) {
        SlotAccessor accessor = ACCESSORS.get(columnName);
        if (accessor == null) {
            throw new IllegalStateException("Unknown custom field slot: " + columnName);
        }
        return accessor;
    }

    /** Every column name in one pool, in slot order -- what allocation scans. */
    public static List<String> pool(String poolPrefix) {
        return ACCESSORS.keySet().stream()
                .filter(column -> column.startsWith(poolPrefix + "_"))
                .toList();
    }

    /**
     * Captures each slot's value type V once, where the getter and setter are
     * compile-time-checked to agree on it, then stores them Object-typed so one map
     * can hold every pool. Mirrors FieldDescriptor.of().
     */
    private static <V> void put(Map<String, SlotAccessor> map, String columnName, Class<V> valueType,
                                Integer maxLength, Function<CustomFieldSlots, V> getter,
                                BiConsumer<CustomFieldSlots, V> setter) {
        map.put(columnName, new SlotAccessor(columnName, valueType, maxLength,
                getter::apply,
                (slots, value) -> setter.accept(slots, valueType.cast(value))));
    }

    private static Map<String, SlotAccessor> buildAccessors() {
        Map<String, SlotAccessor> map = new LinkedHashMap<>();
''' + "\n".join(puts) + '''
        // unmodifiableMap, not Map.copyOf: pool() depends on insertion order
        // so allocation always takes the lowest free slot.
        return Collections.unmodifiableMap(map);
    }
}
'''

out = header + body + registry
open("src/main/java/com/blusimian/blusimian/entityModel/CustomFieldSlots.java", "w", encoding="utf-8").write(out)
print("generated", out.count("\n"), "lines")
