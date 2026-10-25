package com.blusimian.blusimian.entityModel;

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

    public static final String POOL_TEXT = "cf_text";
    public static final String POOL_NUM = "cf_num";
    public static final String POOL_BOOL = "cf_bool";
    public static final String POOL_DATE = "cf_date";
    public static final String POOL_LONGTEXT = "cf_longtext";

    // ---- text pool: 40 slots (varchar(255)) ----
    @Column(name = "cf_text_01", length = 255)
    private String cfText01;

    @Column(name = "cf_text_02", length = 255)
    private String cfText02;

    @Column(name = "cf_text_03", length = 255)
    private String cfText03;

    @Column(name = "cf_text_04", length = 255)
    private String cfText04;

    @Column(name = "cf_text_05", length = 255)
    private String cfText05;

    @Column(name = "cf_text_06", length = 255)
    private String cfText06;

    @Column(name = "cf_text_07", length = 255)
    private String cfText07;

    @Column(name = "cf_text_08", length = 255)
    private String cfText08;

    @Column(name = "cf_text_09", length = 255)
    private String cfText09;

    @Column(name = "cf_text_10", length = 255)
    private String cfText10;

    @Column(name = "cf_text_11", length = 255)
    private String cfText11;

    @Column(name = "cf_text_12", length = 255)
    private String cfText12;

    @Column(name = "cf_text_13", length = 255)
    private String cfText13;

    @Column(name = "cf_text_14", length = 255)
    private String cfText14;

    @Column(name = "cf_text_15", length = 255)
    private String cfText15;

    @Column(name = "cf_text_16", length = 255)
    private String cfText16;

    @Column(name = "cf_text_17", length = 255)
    private String cfText17;

    @Column(name = "cf_text_18", length = 255)
    private String cfText18;

    @Column(name = "cf_text_19", length = 255)
    private String cfText19;

    @Column(name = "cf_text_20", length = 255)
    private String cfText20;

    @Column(name = "cf_text_21", length = 255)
    private String cfText21;

    @Column(name = "cf_text_22", length = 255)
    private String cfText22;

    @Column(name = "cf_text_23", length = 255)
    private String cfText23;

    @Column(name = "cf_text_24", length = 255)
    private String cfText24;

    @Column(name = "cf_text_25", length = 255)
    private String cfText25;

    @Column(name = "cf_text_26", length = 255)
    private String cfText26;

    @Column(name = "cf_text_27", length = 255)
    private String cfText27;

    @Column(name = "cf_text_28", length = 255)
    private String cfText28;

    @Column(name = "cf_text_29", length = 255)
    private String cfText29;

    @Column(name = "cf_text_30", length = 255)
    private String cfText30;

    @Column(name = "cf_text_31", length = 255)
    private String cfText31;

    @Column(name = "cf_text_32", length = 255)
    private String cfText32;

    @Column(name = "cf_text_33", length = 255)
    private String cfText33;

    @Column(name = "cf_text_34", length = 255)
    private String cfText34;

    @Column(name = "cf_text_35", length = 255)
    private String cfText35;

    @Column(name = "cf_text_36", length = 255)
    private String cfText36;

    @Column(name = "cf_text_37", length = 255)
    private String cfText37;

    @Column(name = "cf_text_38", length = 255)
    private String cfText38;

    @Column(name = "cf_text_39", length = 255)
    private String cfText39;

    @Column(name = "cf_text_40", length = 255)
    private String cfText40;


    // ---- num pool: 25 slots (numeric(19,4)) ----
    @Column(name = "cf_num_01")
    private BigDecimal cfNum01;

    @Column(name = "cf_num_02")
    private BigDecimal cfNum02;

    @Column(name = "cf_num_03")
    private BigDecimal cfNum03;

    @Column(name = "cf_num_04")
    private BigDecimal cfNum04;

    @Column(name = "cf_num_05")
    private BigDecimal cfNum05;

    @Column(name = "cf_num_06")
    private BigDecimal cfNum06;

    @Column(name = "cf_num_07")
    private BigDecimal cfNum07;

    @Column(name = "cf_num_08")
    private BigDecimal cfNum08;

    @Column(name = "cf_num_09")
    private BigDecimal cfNum09;

    @Column(name = "cf_num_10")
    private BigDecimal cfNum10;

    @Column(name = "cf_num_11")
    private BigDecimal cfNum11;

    @Column(name = "cf_num_12")
    private BigDecimal cfNum12;

    @Column(name = "cf_num_13")
    private BigDecimal cfNum13;

    @Column(name = "cf_num_14")
    private BigDecimal cfNum14;

    @Column(name = "cf_num_15")
    private BigDecimal cfNum15;

    @Column(name = "cf_num_16")
    private BigDecimal cfNum16;

    @Column(name = "cf_num_17")
    private BigDecimal cfNum17;

    @Column(name = "cf_num_18")
    private BigDecimal cfNum18;

    @Column(name = "cf_num_19")
    private BigDecimal cfNum19;

    @Column(name = "cf_num_20")
    private BigDecimal cfNum20;

    @Column(name = "cf_num_21")
    private BigDecimal cfNum21;

    @Column(name = "cf_num_22")
    private BigDecimal cfNum22;

    @Column(name = "cf_num_23")
    private BigDecimal cfNum23;

    @Column(name = "cf_num_24")
    private BigDecimal cfNum24;

    @Column(name = "cf_num_25")
    private BigDecimal cfNum25;


    // ---- bool pool: 25 slots (boolean) ----
    @Column(name = "cf_bool_01")
    private Boolean cfBool01;

    @Column(name = "cf_bool_02")
    private Boolean cfBool02;

    @Column(name = "cf_bool_03")
    private Boolean cfBool03;

    @Column(name = "cf_bool_04")
    private Boolean cfBool04;

    @Column(name = "cf_bool_05")
    private Boolean cfBool05;

    @Column(name = "cf_bool_06")
    private Boolean cfBool06;

    @Column(name = "cf_bool_07")
    private Boolean cfBool07;

    @Column(name = "cf_bool_08")
    private Boolean cfBool08;

    @Column(name = "cf_bool_09")
    private Boolean cfBool09;

    @Column(name = "cf_bool_10")
    private Boolean cfBool10;

    @Column(name = "cf_bool_11")
    private Boolean cfBool11;

    @Column(name = "cf_bool_12")
    private Boolean cfBool12;

    @Column(name = "cf_bool_13")
    private Boolean cfBool13;

    @Column(name = "cf_bool_14")
    private Boolean cfBool14;

    @Column(name = "cf_bool_15")
    private Boolean cfBool15;

    @Column(name = "cf_bool_16")
    private Boolean cfBool16;

    @Column(name = "cf_bool_17")
    private Boolean cfBool17;

    @Column(name = "cf_bool_18")
    private Boolean cfBool18;

    @Column(name = "cf_bool_19")
    private Boolean cfBool19;

    @Column(name = "cf_bool_20")
    private Boolean cfBool20;

    @Column(name = "cf_bool_21")
    private Boolean cfBool21;

    @Column(name = "cf_bool_22")
    private Boolean cfBool22;

    @Column(name = "cf_bool_23")
    private Boolean cfBool23;

    @Column(name = "cf_bool_24")
    private Boolean cfBool24;

    @Column(name = "cf_bool_25")
    private Boolean cfBool25;


    // ---- date pool: 10 slots (date) ----
    @Column(name = "cf_date_01")
    private LocalDate cfDate01;

    @Column(name = "cf_date_02")
    private LocalDate cfDate02;

    @Column(name = "cf_date_03")
    private LocalDate cfDate03;

    @Column(name = "cf_date_04")
    private LocalDate cfDate04;

    @Column(name = "cf_date_05")
    private LocalDate cfDate05;

    @Column(name = "cf_date_06")
    private LocalDate cfDate06;

    @Column(name = "cf_date_07")
    private LocalDate cfDate07;

    @Column(name = "cf_date_08")
    private LocalDate cfDate08;

    @Column(name = "cf_date_09")
    private LocalDate cfDate09;

    @Column(name = "cf_date_10")
    private LocalDate cfDate10;


    // ---- longtext pool: 5 slots (text) ----
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "cf_longtext_01")
    private String cfLongtext01;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "cf_longtext_02")
    private String cfLongtext02;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "cf_longtext_03")
    private String cfLongtext03;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "cf_longtext_04")
    private String cfLongtext04;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "cf_longtext_05")
    private String cfLongtext05;


    public String getCfText01() {
        return cfText01;
    }

    public void setCfText01(String cfText01) {
        this.cfText01 = cfText01;
    }

    public String getCfText02() {
        return cfText02;
    }

    public void setCfText02(String cfText02) {
        this.cfText02 = cfText02;
    }

    public String getCfText03() {
        return cfText03;
    }

    public void setCfText03(String cfText03) {
        this.cfText03 = cfText03;
    }

    public String getCfText04() {
        return cfText04;
    }

    public void setCfText04(String cfText04) {
        this.cfText04 = cfText04;
    }

    public String getCfText05() {
        return cfText05;
    }

    public void setCfText05(String cfText05) {
        this.cfText05 = cfText05;
    }

    public String getCfText06() {
        return cfText06;
    }

    public void setCfText06(String cfText06) {
        this.cfText06 = cfText06;
    }

    public String getCfText07() {
        return cfText07;
    }

    public void setCfText07(String cfText07) {
        this.cfText07 = cfText07;
    }

    public String getCfText08() {
        return cfText08;
    }

    public void setCfText08(String cfText08) {
        this.cfText08 = cfText08;
    }

    public String getCfText09() {
        return cfText09;
    }

    public void setCfText09(String cfText09) {
        this.cfText09 = cfText09;
    }

    public String getCfText10() {
        return cfText10;
    }

    public void setCfText10(String cfText10) {
        this.cfText10 = cfText10;
    }

    public String getCfText11() {
        return cfText11;
    }

    public void setCfText11(String cfText11) {
        this.cfText11 = cfText11;
    }

    public String getCfText12() {
        return cfText12;
    }

    public void setCfText12(String cfText12) {
        this.cfText12 = cfText12;
    }

    public String getCfText13() {
        return cfText13;
    }

    public void setCfText13(String cfText13) {
        this.cfText13 = cfText13;
    }

    public String getCfText14() {
        return cfText14;
    }

    public void setCfText14(String cfText14) {
        this.cfText14 = cfText14;
    }

    public String getCfText15() {
        return cfText15;
    }

    public void setCfText15(String cfText15) {
        this.cfText15 = cfText15;
    }

    public String getCfText16() {
        return cfText16;
    }

    public void setCfText16(String cfText16) {
        this.cfText16 = cfText16;
    }

    public String getCfText17() {
        return cfText17;
    }

    public void setCfText17(String cfText17) {
        this.cfText17 = cfText17;
    }

    public String getCfText18() {
        return cfText18;
    }

    public void setCfText18(String cfText18) {
        this.cfText18 = cfText18;
    }

    public String getCfText19() {
        return cfText19;
    }

    public void setCfText19(String cfText19) {
        this.cfText19 = cfText19;
    }

    public String getCfText20() {
        return cfText20;
    }

    public void setCfText20(String cfText20) {
        this.cfText20 = cfText20;
    }

    public String getCfText21() {
        return cfText21;
    }

    public void setCfText21(String cfText21) {
        this.cfText21 = cfText21;
    }

    public String getCfText22() {
        return cfText22;
    }

    public void setCfText22(String cfText22) {
        this.cfText22 = cfText22;
    }

    public String getCfText23() {
        return cfText23;
    }

    public void setCfText23(String cfText23) {
        this.cfText23 = cfText23;
    }

    public String getCfText24() {
        return cfText24;
    }

    public void setCfText24(String cfText24) {
        this.cfText24 = cfText24;
    }

    public String getCfText25() {
        return cfText25;
    }

    public void setCfText25(String cfText25) {
        this.cfText25 = cfText25;
    }

    public String getCfText26() {
        return cfText26;
    }

    public void setCfText26(String cfText26) {
        this.cfText26 = cfText26;
    }

    public String getCfText27() {
        return cfText27;
    }

    public void setCfText27(String cfText27) {
        this.cfText27 = cfText27;
    }

    public String getCfText28() {
        return cfText28;
    }

    public void setCfText28(String cfText28) {
        this.cfText28 = cfText28;
    }

    public String getCfText29() {
        return cfText29;
    }

    public void setCfText29(String cfText29) {
        this.cfText29 = cfText29;
    }

    public String getCfText30() {
        return cfText30;
    }

    public void setCfText30(String cfText30) {
        this.cfText30 = cfText30;
    }

    public String getCfText31() {
        return cfText31;
    }

    public void setCfText31(String cfText31) {
        this.cfText31 = cfText31;
    }

    public String getCfText32() {
        return cfText32;
    }

    public void setCfText32(String cfText32) {
        this.cfText32 = cfText32;
    }

    public String getCfText33() {
        return cfText33;
    }

    public void setCfText33(String cfText33) {
        this.cfText33 = cfText33;
    }

    public String getCfText34() {
        return cfText34;
    }

    public void setCfText34(String cfText34) {
        this.cfText34 = cfText34;
    }

    public String getCfText35() {
        return cfText35;
    }

    public void setCfText35(String cfText35) {
        this.cfText35 = cfText35;
    }

    public String getCfText36() {
        return cfText36;
    }

    public void setCfText36(String cfText36) {
        this.cfText36 = cfText36;
    }

    public String getCfText37() {
        return cfText37;
    }

    public void setCfText37(String cfText37) {
        this.cfText37 = cfText37;
    }

    public String getCfText38() {
        return cfText38;
    }

    public void setCfText38(String cfText38) {
        this.cfText38 = cfText38;
    }

    public String getCfText39() {
        return cfText39;
    }

    public void setCfText39(String cfText39) {
        this.cfText39 = cfText39;
    }

    public String getCfText40() {
        return cfText40;
    }

    public void setCfText40(String cfText40) {
        this.cfText40 = cfText40;
    }

    public BigDecimal getCfNum01() {
        return cfNum01;
    }

    public void setCfNum01(BigDecimal cfNum01) {
        this.cfNum01 = cfNum01;
    }

    public BigDecimal getCfNum02() {
        return cfNum02;
    }

    public void setCfNum02(BigDecimal cfNum02) {
        this.cfNum02 = cfNum02;
    }

    public BigDecimal getCfNum03() {
        return cfNum03;
    }

    public void setCfNum03(BigDecimal cfNum03) {
        this.cfNum03 = cfNum03;
    }

    public BigDecimal getCfNum04() {
        return cfNum04;
    }

    public void setCfNum04(BigDecimal cfNum04) {
        this.cfNum04 = cfNum04;
    }

    public BigDecimal getCfNum05() {
        return cfNum05;
    }

    public void setCfNum05(BigDecimal cfNum05) {
        this.cfNum05 = cfNum05;
    }

    public BigDecimal getCfNum06() {
        return cfNum06;
    }

    public void setCfNum06(BigDecimal cfNum06) {
        this.cfNum06 = cfNum06;
    }

    public BigDecimal getCfNum07() {
        return cfNum07;
    }

    public void setCfNum07(BigDecimal cfNum07) {
        this.cfNum07 = cfNum07;
    }

    public BigDecimal getCfNum08() {
        return cfNum08;
    }

    public void setCfNum08(BigDecimal cfNum08) {
        this.cfNum08 = cfNum08;
    }

    public BigDecimal getCfNum09() {
        return cfNum09;
    }

    public void setCfNum09(BigDecimal cfNum09) {
        this.cfNum09 = cfNum09;
    }

    public BigDecimal getCfNum10() {
        return cfNum10;
    }

    public void setCfNum10(BigDecimal cfNum10) {
        this.cfNum10 = cfNum10;
    }

    public BigDecimal getCfNum11() {
        return cfNum11;
    }

    public void setCfNum11(BigDecimal cfNum11) {
        this.cfNum11 = cfNum11;
    }

    public BigDecimal getCfNum12() {
        return cfNum12;
    }

    public void setCfNum12(BigDecimal cfNum12) {
        this.cfNum12 = cfNum12;
    }

    public BigDecimal getCfNum13() {
        return cfNum13;
    }

    public void setCfNum13(BigDecimal cfNum13) {
        this.cfNum13 = cfNum13;
    }

    public BigDecimal getCfNum14() {
        return cfNum14;
    }

    public void setCfNum14(BigDecimal cfNum14) {
        this.cfNum14 = cfNum14;
    }

    public BigDecimal getCfNum15() {
        return cfNum15;
    }

    public void setCfNum15(BigDecimal cfNum15) {
        this.cfNum15 = cfNum15;
    }

    public BigDecimal getCfNum16() {
        return cfNum16;
    }

    public void setCfNum16(BigDecimal cfNum16) {
        this.cfNum16 = cfNum16;
    }

    public BigDecimal getCfNum17() {
        return cfNum17;
    }

    public void setCfNum17(BigDecimal cfNum17) {
        this.cfNum17 = cfNum17;
    }

    public BigDecimal getCfNum18() {
        return cfNum18;
    }

    public void setCfNum18(BigDecimal cfNum18) {
        this.cfNum18 = cfNum18;
    }

    public BigDecimal getCfNum19() {
        return cfNum19;
    }

    public void setCfNum19(BigDecimal cfNum19) {
        this.cfNum19 = cfNum19;
    }

    public BigDecimal getCfNum20() {
        return cfNum20;
    }

    public void setCfNum20(BigDecimal cfNum20) {
        this.cfNum20 = cfNum20;
    }

    public BigDecimal getCfNum21() {
        return cfNum21;
    }

    public void setCfNum21(BigDecimal cfNum21) {
        this.cfNum21 = cfNum21;
    }

    public BigDecimal getCfNum22() {
        return cfNum22;
    }

    public void setCfNum22(BigDecimal cfNum22) {
        this.cfNum22 = cfNum22;
    }

    public BigDecimal getCfNum23() {
        return cfNum23;
    }

    public void setCfNum23(BigDecimal cfNum23) {
        this.cfNum23 = cfNum23;
    }

    public BigDecimal getCfNum24() {
        return cfNum24;
    }

    public void setCfNum24(BigDecimal cfNum24) {
        this.cfNum24 = cfNum24;
    }

    public BigDecimal getCfNum25() {
        return cfNum25;
    }

    public void setCfNum25(BigDecimal cfNum25) {
        this.cfNum25 = cfNum25;
    }

    public Boolean getCfBool01() {
        return cfBool01;
    }

    public void setCfBool01(Boolean cfBool01) {
        this.cfBool01 = cfBool01;
    }

    public Boolean getCfBool02() {
        return cfBool02;
    }

    public void setCfBool02(Boolean cfBool02) {
        this.cfBool02 = cfBool02;
    }

    public Boolean getCfBool03() {
        return cfBool03;
    }

    public void setCfBool03(Boolean cfBool03) {
        this.cfBool03 = cfBool03;
    }

    public Boolean getCfBool04() {
        return cfBool04;
    }

    public void setCfBool04(Boolean cfBool04) {
        this.cfBool04 = cfBool04;
    }

    public Boolean getCfBool05() {
        return cfBool05;
    }

    public void setCfBool05(Boolean cfBool05) {
        this.cfBool05 = cfBool05;
    }

    public Boolean getCfBool06() {
        return cfBool06;
    }

    public void setCfBool06(Boolean cfBool06) {
        this.cfBool06 = cfBool06;
    }

    public Boolean getCfBool07() {
        return cfBool07;
    }

    public void setCfBool07(Boolean cfBool07) {
        this.cfBool07 = cfBool07;
    }

    public Boolean getCfBool08() {
        return cfBool08;
    }

    public void setCfBool08(Boolean cfBool08) {
        this.cfBool08 = cfBool08;
    }

    public Boolean getCfBool09() {
        return cfBool09;
    }

    public void setCfBool09(Boolean cfBool09) {
        this.cfBool09 = cfBool09;
    }

    public Boolean getCfBool10() {
        return cfBool10;
    }

    public void setCfBool10(Boolean cfBool10) {
        this.cfBool10 = cfBool10;
    }

    public Boolean getCfBool11() {
        return cfBool11;
    }

    public void setCfBool11(Boolean cfBool11) {
        this.cfBool11 = cfBool11;
    }

    public Boolean getCfBool12() {
        return cfBool12;
    }

    public void setCfBool12(Boolean cfBool12) {
        this.cfBool12 = cfBool12;
    }

    public Boolean getCfBool13() {
        return cfBool13;
    }

    public void setCfBool13(Boolean cfBool13) {
        this.cfBool13 = cfBool13;
    }

    public Boolean getCfBool14() {
        return cfBool14;
    }

    public void setCfBool14(Boolean cfBool14) {
        this.cfBool14 = cfBool14;
    }

    public Boolean getCfBool15() {
        return cfBool15;
    }

    public void setCfBool15(Boolean cfBool15) {
        this.cfBool15 = cfBool15;
    }

    public Boolean getCfBool16() {
        return cfBool16;
    }

    public void setCfBool16(Boolean cfBool16) {
        this.cfBool16 = cfBool16;
    }

    public Boolean getCfBool17() {
        return cfBool17;
    }

    public void setCfBool17(Boolean cfBool17) {
        this.cfBool17 = cfBool17;
    }

    public Boolean getCfBool18() {
        return cfBool18;
    }

    public void setCfBool18(Boolean cfBool18) {
        this.cfBool18 = cfBool18;
    }

    public Boolean getCfBool19() {
        return cfBool19;
    }

    public void setCfBool19(Boolean cfBool19) {
        this.cfBool19 = cfBool19;
    }

    public Boolean getCfBool20() {
        return cfBool20;
    }

    public void setCfBool20(Boolean cfBool20) {
        this.cfBool20 = cfBool20;
    }

    public Boolean getCfBool21() {
        return cfBool21;
    }

    public void setCfBool21(Boolean cfBool21) {
        this.cfBool21 = cfBool21;
    }

    public Boolean getCfBool22() {
        return cfBool22;
    }

    public void setCfBool22(Boolean cfBool22) {
        this.cfBool22 = cfBool22;
    }

    public Boolean getCfBool23() {
        return cfBool23;
    }

    public void setCfBool23(Boolean cfBool23) {
        this.cfBool23 = cfBool23;
    }

    public Boolean getCfBool24() {
        return cfBool24;
    }

    public void setCfBool24(Boolean cfBool24) {
        this.cfBool24 = cfBool24;
    }

    public Boolean getCfBool25() {
        return cfBool25;
    }

    public void setCfBool25(Boolean cfBool25) {
        this.cfBool25 = cfBool25;
    }

    public LocalDate getCfDate01() {
        return cfDate01;
    }

    public void setCfDate01(LocalDate cfDate01) {
        this.cfDate01 = cfDate01;
    }

    public LocalDate getCfDate02() {
        return cfDate02;
    }

    public void setCfDate02(LocalDate cfDate02) {
        this.cfDate02 = cfDate02;
    }

    public LocalDate getCfDate03() {
        return cfDate03;
    }

    public void setCfDate03(LocalDate cfDate03) {
        this.cfDate03 = cfDate03;
    }

    public LocalDate getCfDate04() {
        return cfDate04;
    }

    public void setCfDate04(LocalDate cfDate04) {
        this.cfDate04 = cfDate04;
    }

    public LocalDate getCfDate05() {
        return cfDate05;
    }

    public void setCfDate05(LocalDate cfDate05) {
        this.cfDate05 = cfDate05;
    }

    public LocalDate getCfDate06() {
        return cfDate06;
    }

    public void setCfDate06(LocalDate cfDate06) {
        this.cfDate06 = cfDate06;
    }

    public LocalDate getCfDate07() {
        return cfDate07;
    }

    public void setCfDate07(LocalDate cfDate07) {
        this.cfDate07 = cfDate07;
    }

    public LocalDate getCfDate08() {
        return cfDate08;
    }

    public void setCfDate08(LocalDate cfDate08) {
        this.cfDate08 = cfDate08;
    }

    public LocalDate getCfDate09() {
        return cfDate09;
    }

    public void setCfDate09(LocalDate cfDate09) {
        this.cfDate09 = cfDate09;
    }

    public LocalDate getCfDate10() {
        return cfDate10;
    }

    public void setCfDate10(LocalDate cfDate10) {
        this.cfDate10 = cfDate10;
    }

    public String getCfLongtext01() {
        return cfLongtext01;
    }

    public void setCfLongtext01(String cfLongtext01) {
        this.cfLongtext01 = cfLongtext01;
    }

    public String getCfLongtext02() {
        return cfLongtext02;
    }

    public void setCfLongtext02(String cfLongtext02) {
        this.cfLongtext02 = cfLongtext02;
    }

    public String getCfLongtext03() {
        return cfLongtext03;
    }

    public void setCfLongtext03(String cfLongtext03) {
        this.cfLongtext03 = cfLongtext03;
    }

    public String getCfLongtext04() {
        return cfLongtext04;
    }

    public void setCfLongtext04(String cfLongtext04) {
        this.cfLongtext04 = cfLongtext04;
    }

    public String getCfLongtext05() {
        return cfLongtext05;
    }

    public void setCfLongtext05(String cfLongtext05) {
        this.cfLongtext05 = cfLongtext05;
    }

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
        put(map, "cf_text_01", String.class, 255, CustomFieldSlots::getCfText01, CustomFieldSlots::setCfText01);
        put(map, "cf_text_02", String.class, 255, CustomFieldSlots::getCfText02, CustomFieldSlots::setCfText02);
        put(map, "cf_text_03", String.class, 255, CustomFieldSlots::getCfText03, CustomFieldSlots::setCfText03);
        put(map, "cf_text_04", String.class, 255, CustomFieldSlots::getCfText04, CustomFieldSlots::setCfText04);
        put(map, "cf_text_05", String.class, 255, CustomFieldSlots::getCfText05, CustomFieldSlots::setCfText05);
        put(map, "cf_text_06", String.class, 255, CustomFieldSlots::getCfText06, CustomFieldSlots::setCfText06);
        put(map, "cf_text_07", String.class, 255, CustomFieldSlots::getCfText07, CustomFieldSlots::setCfText07);
        put(map, "cf_text_08", String.class, 255, CustomFieldSlots::getCfText08, CustomFieldSlots::setCfText08);
        put(map, "cf_text_09", String.class, 255, CustomFieldSlots::getCfText09, CustomFieldSlots::setCfText09);
        put(map, "cf_text_10", String.class, 255, CustomFieldSlots::getCfText10, CustomFieldSlots::setCfText10);
        put(map, "cf_text_11", String.class, 255, CustomFieldSlots::getCfText11, CustomFieldSlots::setCfText11);
        put(map, "cf_text_12", String.class, 255, CustomFieldSlots::getCfText12, CustomFieldSlots::setCfText12);
        put(map, "cf_text_13", String.class, 255, CustomFieldSlots::getCfText13, CustomFieldSlots::setCfText13);
        put(map, "cf_text_14", String.class, 255, CustomFieldSlots::getCfText14, CustomFieldSlots::setCfText14);
        put(map, "cf_text_15", String.class, 255, CustomFieldSlots::getCfText15, CustomFieldSlots::setCfText15);
        put(map, "cf_text_16", String.class, 255, CustomFieldSlots::getCfText16, CustomFieldSlots::setCfText16);
        put(map, "cf_text_17", String.class, 255, CustomFieldSlots::getCfText17, CustomFieldSlots::setCfText17);
        put(map, "cf_text_18", String.class, 255, CustomFieldSlots::getCfText18, CustomFieldSlots::setCfText18);
        put(map, "cf_text_19", String.class, 255, CustomFieldSlots::getCfText19, CustomFieldSlots::setCfText19);
        put(map, "cf_text_20", String.class, 255, CustomFieldSlots::getCfText20, CustomFieldSlots::setCfText20);
        put(map, "cf_text_21", String.class, 255, CustomFieldSlots::getCfText21, CustomFieldSlots::setCfText21);
        put(map, "cf_text_22", String.class, 255, CustomFieldSlots::getCfText22, CustomFieldSlots::setCfText22);
        put(map, "cf_text_23", String.class, 255, CustomFieldSlots::getCfText23, CustomFieldSlots::setCfText23);
        put(map, "cf_text_24", String.class, 255, CustomFieldSlots::getCfText24, CustomFieldSlots::setCfText24);
        put(map, "cf_text_25", String.class, 255, CustomFieldSlots::getCfText25, CustomFieldSlots::setCfText25);
        put(map, "cf_text_26", String.class, 255, CustomFieldSlots::getCfText26, CustomFieldSlots::setCfText26);
        put(map, "cf_text_27", String.class, 255, CustomFieldSlots::getCfText27, CustomFieldSlots::setCfText27);
        put(map, "cf_text_28", String.class, 255, CustomFieldSlots::getCfText28, CustomFieldSlots::setCfText28);
        put(map, "cf_text_29", String.class, 255, CustomFieldSlots::getCfText29, CustomFieldSlots::setCfText29);
        put(map, "cf_text_30", String.class, 255, CustomFieldSlots::getCfText30, CustomFieldSlots::setCfText30);
        put(map, "cf_text_31", String.class, 255, CustomFieldSlots::getCfText31, CustomFieldSlots::setCfText31);
        put(map, "cf_text_32", String.class, 255, CustomFieldSlots::getCfText32, CustomFieldSlots::setCfText32);
        put(map, "cf_text_33", String.class, 255, CustomFieldSlots::getCfText33, CustomFieldSlots::setCfText33);
        put(map, "cf_text_34", String.class, 255, CustomFieldSlots::getCfText34, CustomFieldSlots::setCfText34);
        put(map, "cf_text_35", String.class, 255, CustomFieldSlots::getCfText35, CustomFieldSlots::setCfText35);
        put(map, "cf_text_36", String.class, 255, CustomFieldSlots::getCfText36, CustomFieldSlots::setCfText36);
        put(map, "cf_text_37", String.class, 255, CustomFieldSlots::getCfText37, CustomFieldSlots::setCfText37);
        put(map, "cf_text_38", String.class, 255, CustomFieldSlots::getCfText38, CustomFieldSlots::setCfText38);
        put(map, "cf_text_39", String.class, 255, CustomFieldSlots::getCfText39, CustomFieldSlots::setCfText39);
        put(map, "cf_text_40", String.class, 255, CustomFieldSlots::getCfText40, CustomFieldSlots::setCfText40);
        put(map, "cf_num_01", BigDecimal.class, null, CustomFieldSlots::getCfNum01, CustomFieldSlots::setCfNum01);
        put(map, "cf_num_02", BigDecimal.class, null, CustomFieldSlots::getCfNum02, CustomFieldSlots::setCfNum02);
        put(map, "cf_num_03", BigDecimal.class, null, CustomFieldSlots::getCfNum03, CustomFieldSlots::setCfNum03);
        put(map, "cf_num_04", BigDecimal.class, null, CustomFieldSlots::getCfNum04, CustomFieldSlots::setCfNum04);
        put(map, "cf_num_05", BigDecimal.class, null, CustomFieldSlots::getCfNum05, CustomFieldSlots::setCfNum05);
        put(map, "cf_num_06", BigDecimal.class, null, CustomFieldSlots::getCfNum06, CustomFieldSlots::setCfNum06);
        put(map, "cf_num_07", BigDecimal.class, null, CustomFieldSlots::getCfNum07, CustomFieldSlots::setCfNum07);
        put(map, "cf_num_08", BigDecimal.class, null, CustomFieldSlots::getCfNum08, CustomFieldSlots::setCfNum08);
        put(map, "cf_num_09", BigDecimal.class, null, CustomFieldSlots::getCfNum09, CustomFieldSlots::setCfNum09);
        put(map, "cf_num_10", BigDecimal.class, null, CustomFieldSlots::getCfNum10, CustomFieldSlots::setCfNum10);
        put(map, "cf_num_11", BigDecimal.class, null, CustomFieldSlots::getCfNum11, CustomFieldSlots::setCfNum11);
        put(map, "cf_num_12", BigDecimal.class, null, CustomFieldSlots::getCfNum12, CustomFieldSlots::setCfNum12);
        put(map, "cf_num_13", BigDecimal.class, null, CustomFieldSlots::getCfNum13, CustomFieldSlots::setCfNum13);
        put(map, "cf_num_14", BigDecimal.class, null, CustomFieldSlots::getCfNum14, CustomFieldSlots::setCfNum14);
        put(map, "cf_num_15", BigDecimal.class, null, CustomFieldSlots::getCfNum15, CustomFieldSlots::setCfNum15);
        put(map, "cf_num_16", BigDecimal.class, null, CustomFieldSlots::getCfNum16, CustomFieldSlots::setCfNum16);
        put(map, "cf_num_17", BigDecimal.class, null, CustomFieldSlots::getCfNum17, CustomFieldSlots::setCfNum17);
        put(map, "cf_num_18", BigDecimal.class, null, CustomFieldSlots::getCfNum18, CustomFieldSlots::setCfNum18);
        put(map, "cf_num_19", BigDecimal.class, null, CustomFieldSlots::getCfNum19, CustomFieldSlots::setCfNum19);
        put(map, "cf_num_20", BigDecimal.class, null, CustomFieldSlots::getCfNum20, CustomFieldSlots::setCfNum20);
        put(map, "cf_num_21", BigDecimal.class, null, CustomFieldSlots::getCfNum21, CustomFieldSlots::setCfNum21);
        put(map, "cf_num_22", BigDecimal.class, null, CustomFieldSlots::getCfNum22, CustomFieldSlots::setCfNum22);
        put(map, "cf_num_23", BigDecimal.class, null, CustomFieldSlots::getCfNum23, CustomFieldSlots::setCfNum23);
        put(map, "cf_num_24", BigDecimal.class, null, CustomFieldSlots::getCfNum24, CustomFieldSlots::setCfNum24);
        put(map, "cf_num_25", BigDecimal.class, null, CustomFieldSlots::getCfNum25, CustomFieldSlots::setCfNum25);
        put(map, "cf_bool_01", Boolean.class, null, CustomFieldSlots::getCfBool01, CustomFieldSlots::setCfBool01);
        put(map, "cf_bool_02", Boolean.class, null, CustomFieldSlots::getCfBool02, CustomFieldSlots::setCfBool02);
        put(map, "cf_bool_03", Boolean.class, null, CustomFieldSlots::getCfBool03, CustomFieldSlots::setCfBool03);
        put(map, "cf_bool_04", Boolean.class, null, CustomFieldSlots::getCfBool04, CustomFieldSlots::setCfBool04);
        put(map, "cf_bool_05", Boolean.class, null, CustomFieldSlots::getCfBool05, CustomFieldSlots::setCfBool05);
        put(map, "cf_bool_06", Boolean.class, null, CustomFieldSlots::getCfBool06, CustomFieldSlots::setCfBool06);
        put(map, "cf_bool_07", Boolean.class, null, CustomFieldSlots::getCfBool07, CustomFieldSlots::setCfBool07);
        put(map, "cf_bool_08", Boolean.class, null, CustomFieldSlots::getCfBool08, CustomFieldSlots::setCfBool08);
        put(map, "cf_bool_09", Boolean.class, null, CustomFieldSlots::getCfBool09, CustomFieldSlots::setCfBool09);
        put(map, "cf_bool_10", Boolean.class, null, CustomFieldSlots::getCfBool10, CustomFieldSlots::setCfBool10);
        put(map, "cf_bool_11", Boolean.class, null, CustomFieldSlots::getCfBool11, CustomFieldSlots::setCfBool11);
        put(map, "cf_bool_12", Boolean.class, null, CustomFieldSlots::getCfBool12, CustomFieldSlots::setCfBool12);
        put(map, "cf_bool_13", Boolean.class, null, CustomFieldSlots::getCfBool13, CustomFieldSlots::setCfBool13);
        put(map, "cf_bool_14", Boolean.class, null, CustomFieldSlots::getCfBool14, CustomFieldSlots::setCfBool14);
        put(map, "cf_bool_15", Boolean.class, null, CustomFieldSlots::getCfBool15, CustomFieldSlots::setCfBool15);
        put(map, "cf_bool_16", Boolean.class, null, CustomFieldSlots::getCfBool16, CustomFieldSlots::setCfBool16);
        put(map, "cf_bool_17", Boolean.class, null, CustomFieldSlots::getCfBool17, CustomFieldSlots::setCfBool17);
        put(map, "cf_bool_18", Boolean.class, null, CustomFieldSlots::getCfBool18, CustomFieldSlots::setCfBool18);
        put(map, "cf_bool_19", Boolean.class, null, CustomFieldSlots::getCfBool19, CustomFieldSlots::setCfBool19);
        put(map, "cf_bool_20", Boolean.class, null, CustomFieldSlots::getCfBool20, CustomFieldSlots::setCfBool20);
        put(map, "cf_bool_21", Boolean.class, null, CustomFieldSlots::getCfBool21, CustomFieldSlots::setCfBool21);
        put(map, "cf_bool_22", Boolean.class, null, CustomFieldSlots::getCfBool22, CustomFieldSlots::setCfBool22);
        put(map, "cf_bool_23", Boolean.class, null, CustomFieldSlots::getCfBool23, CustomFieldSlots::setCfBool23);
        put(map, "cf_bool_24", Boolean.class, null, CustomFieldSlots::getCfBool24, CustomFieldSlots::setCfBool24);
        put(map, "cf_bool_25", Boolean.class, null, CustomFieldSlots::getCfBool25, CustomFieldSlots::setCfBool25);
        put(map, "cf_date_01", LocalDate.class, null, CustomFieldSlots::getCfDate01, CustomFieldSlots::setCfDate01);
        put(map, "cf_date_02", LocalDate.class, null, CustomFieldSlots::getCfDate02, CustomFieldSlots::setCfDate02);
        put(map, "cf_date_03", LocalDate.class, null, CustomFieldSlots::getCfDate03, CustomFieldSlots::setCfDate03);
        put(map, "cf_date_04", LocalDate.class, null, CustomFieldSlots::getCfDate04, CustomFieldSlots::setCfDate04);
        put(map, "cf_date_05", LocalDate.class, null, CustomFieldSlots::getCfDate05, CustomFieldSlots::setCfDate05);
        put(map, "cf_date_06", LocalDate.class, null, CustomFieldSlots::getCfDate06, CustomFieldSlots::setCfDate06);
        put(map, "cf_date_07", LocalDate.class, null, CustomFieldSlots::getCfDate07, CustomFieldSlots::setCfDate07);
        put(map, "cf_date_08", LocalDate.class, null, CustomFieldSlots::getCfDate08, CustomFieldSlots::setCfDate08);
        put(map, "cf_date_09", LocalDate.class, null, CustomFieldSlots::getCfDate09, CustomFieldSlots::setCfDate09);
        put(map, "cf_date_10", LocalDate.class, null, CustomFieldSlots::getCfDate10, CustomFieldSlots::setCfDate10);
        put(map, "cf_longtext_01", String.class, null, CustomFieldSlots::getCfLongtext01, CustomFieldSlots::setCfLongtext01);
        put(map, "cf_longtext_02", String.class, null, CustomFieldSlots::getCfLongtext02, CustomFieldSlots::setCfLongtext02);
        put(map, "cf_longtext_03", String.class, null, CustomFieldSlots::getCfLongtext03, CustomFieldSlots::setCfLongtext03);
        put(map, "cf_longtext_04", String.class, null, CustomFieldSlots::getCfLongtext04, CustomFieldSlots::setCfLongtext04);
        put(map, "cf_longtext_05", String.class, null, CustomFieldSlots::getCfLongtext05, CustomFieldSlots::setCfLongtext05);
        // unmodifiableMap, not Map.copyOf: pool() depends on insertion order
        // so allocation always takes the lowest free slot.
        return Collections.unmodifiableMap(map);
    }
}
