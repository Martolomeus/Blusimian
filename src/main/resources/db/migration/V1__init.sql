-- V1: initial schema. Flyway runs this once and records it in
-- flyway_schema_history so it never runs again. The next change goes in
-- V2__something.sql -- you never edit a migration that has already run.
--
-- Schema layout (pre-draws the future tenant-isolation boundary):
--   * public = global reference data (iso_countries) + the citext extension.
--   * app    = tenant-scoped data (contacts, companies). RLS will later apply here.

create schema if not exists app;

-- citext = case-insensitive text. Lives in public so reference + app tables can
-- both use it; the connection search_path (currentSchema=app,public) resolves it.
create
extension if not exists citext with schema public;

-- --------------------------------------------------------------------------
-- public.iso_countries  (global reference data; not tenant-scoped, no RLS)
-- Seed rows are appended below by scripts/gen_countries.mjs.
-- --------------------------------------------------------------------------
create table public.iso_countries
(
    code       char(2) primary key,
    name       text not null,
    sort_order int  not null default 0
);

-- --------------------------------------------------------------------------
-- app.contacts
-- --------------------------------------------------------------------------
create table app.contacts
(
    id                  uuid primary key      default gen_random_uuid(),
    first_name          varchar(100) not null,
    last_name           varchar(100) not null,
    salutation          varchar(50),
    email public.citext,
    phone               varchar(50),
    address_street      varchar(255),
    address_city        varchar(100),
    address_state       varchar(100),
    address_postal_code varchar(20),
    address_country     char(2),
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),

    -- Custom field slot pool -- identical to app.companies (see the notes there),
    -- and LAST in the table for the same reason. Keep the three pools in step:
    -- one CustomFieldSlots embeddable maps all of them.
    cf_text_01 varchar(255), cf_text_02 varchar(255), cf_text_03 varchar(255), cf_text_04 varchar(255),
    cf_text_05 varchar(255), cf_text_06 varchar(255), cf_text_07 varchar(255), cf_text_08 varchar(255),
    cf_text_09 varchar(255), cf_text_10 varchar(255), cf_text_11 varchar(255), cf_text_12 varchar(255),
    cf_text_13 varchar(255), cf_text_14 varchar(255), cf_text_15 varchar(255), cf_text_16 varchar(255),
    cf_text_17 varchar(255), cf_text_18 varchar(255), cf_text_19 varchar(255), cf_text_20 varchar(255),
    cf_text_21 varchar(255), cf_text_22 varchar(255), cf_text_23 varchar(255), cf_text_24 varchar(255),
    cf_text_25 varchar(255), cf_text_26 varchar(255), cf_text_27 varchar(255), cf_text_28 varchar(255),
    cf_text_29 varchar(255), cf_text_30 varchar(255), cf_text_31 varchar(255), cf_text_32 varchar(255),
    cf_text_33 varchar(255), cf_text_34 varchar(255), cf_text_35 varchar(255), cf_text_36 varchar(255),
    cf_text_37 varchar(255), cf_text_38 varchar(255), cf_text_39 varchar(255), cf_text_40 varchar(255),

    cf_num_01 numeric(19, 4), cf_num_02 numeric(19, 4), cf_num_03 numeric(19, 4), cf_num_04 numeric(19, 4),
    cf_num_05 numeric(19, 4), cf_num_06 numeric(19, 4), cf_num_07 numeric(19, 4), cf_num_08 numeric(19, 4),
    cf_num_09 numeric(19, 4), cf_num_10 numeric(19, 4), cf_num_11 numeric(19, 4), cf_num_12 numeric(19, 4),
    cf_num_13 numeric(19, 4), cf_num_14 numeric(19, 4), cf_num_15 numeric(19, 4), cf_num_16 numeric(19, 4),
    cf_num_17 numeric(19, 4), cf_num_18 numeric(19, 4), cf_num_19 numeric(19, 4), cf_num_20 numeric(19, 4),
    cf_num_21 numeric(19, 4), cf_num_22 numeric(19, 4), cf_num_23 numeric(19, 4), cf_num_24 numeric(19, 4),
    cf_num_25 numeric(19, 4),

    cf_bool_01 boolean, cf_bool_02 boolean, cf_bool_03 boolean, cf_bool_04 boolean, cf_bool_05 boolean,
    cf_bool_06 boolean, cf_bool_07 boolean, cf_bool_08 boolean, cf_bool_09 boolean, cf_bool_10 boolean,
    cf_bool_11 boolean, cf_bool_12 boolean, cf_bool_13 boolean, cf_bool_14 boolean, cf_bool_15 boolean,
    cf_bool_16 boolean, cf_bool_17 boolean, cf_bool_18 boolean, cf_bool_19 boolean, cf_bool_20 boolean,
    cf_bool_21 boolean, cf_bool_22 boolean, cf_bool_23 boolean, cf_bool_24 boolean, cf_bool_25 boolean,

    cf_date_01 date, cf_date_02 date, cf_date_03 date, cf_date_04 date, cf_date_05 date,
    cf_date_06 date, cf_date_07 date, cf_date_08 date, cf_date_09 date, cf_date_10 date,

    cf_longtext_01 text, cf_longtext_02 text, cf_longtext_03 text,
    cf_longtext_04 text, cf_longtext_05 text
);

-- --------------------------------------------------------------------------
-- app.companies (a.k.a. "accounts")
--   * single-tenant for now: no tenant_id column, no Row-Level Security.
--   * owner_id / created_by_id / account_manager_id are kept for structure but
--     WITHOUT foreign keys (there is no users table yet).
--   * parent_company_id keeps a real self-referencing FK.
--   * custom field VALUES live in the cf_* slot pool at the bottom of this
--     table, NOT in a jsonb blob. Which slot a given custom field owns is
--     recorded in app.custom_field_definitions.column_name; without that row a
--     cf_* column is meaningless. Typed columns buy real indexes, real sorting,
--     real planner statistics and per-field writes -- see the notes on the pool.
-- --------------------------------------------------------------------------
create table app.companies
(
    id                           uuid primary key      default gen_random_uuid(),
    name                         varchar(255) not null,
    legal_name                   varchar(255),
    type                         varchar(100),
    industry                     varchar(100),
    description                  text,
    employee_range               varchar(30),
    website                      text,
    phone                        varchar(50),
    phone_alt                    varchar(50),

    billing_address_street       varchar(255),
    billing_address_city         varchar(100),
    billing_address_state        varchar(100),
    billing_address_postal_code  varchar(20),
    billing_address_country      char(2),

    shipping_address_street      varchar(255),
    shipping_address_city        varchar(100),
    shipping_address_state       varchar(100),
    shipping_address_postal_code varchar(20),
    shipping_address_country     char(2),

    parent_company_id            uuid         references app.companies (id) on delete set null,
    account_manager_id           uuid, -- FK to users omitted (no users table yet)
    owner_id                     uuid, -- FK to users omitted
    created_by_id                uuid, -- FK to users omitted

    vat_number                   varchar(50),

    archived_at                  timestamptz,
    created_at                   timestamptz  not null default now(),
    updated_at                   timestamptz  not null default now(),

    -- ----------------------------------------------------------------------
    -- Custom field slot pool: 40 text + 25 numeric + 25 boolean + 10 date,
    -- plus 5 long-text. Deliberately LAST in the table: Postgres deforms a
    -- tuple attribute-by-attribute up to the highest column a query touches,
    -- so a projection like `select id, name, updated_at` stops before the pool
    -- and never pays for it. Moving the pool later is impossible without a
    -- table rebuild, so it goes here from the start.
    --
    -- An unused slot costs one bit in the row's null bitmap (~23 bytes for the
    -- whole row), so an empty pool is effectively free on disk. It is NOT free
    -- on the wire: every column sends a 4-byte length prefix even when null,
    -- which is the real argument against over-provisioning the pool.
    --
    -- Pool sizes are a hard product limit, but a recoverable one: widening is
    -- `alter table ... add column cf_text_41 varchar(255)`, which is nullable
    -- with no default and therefore catalog-only and instant (it does take a
    -- brief ACCESS EXCLUSIVE lock, so use a lock_timeout and retry). The cost
    -- of widening is a migration plus regenerated slot accessors, not a
    -- rewrite -- so size for today, not for the worst case.
    --
    -- The pool is deliberately NOT indexed. 105 indexes maintained on every
    -- insert and update would cost far more than the filtering is worth, and
    -- most slots are never queried. A slot gets an index when a tenant marks
    -- that field filterable:
    --   create index concurrently idx_companies_cf_text_03
    --       on app.companies (cf_text_03);
    -- which is online, reversible, and invisible to Hibernate. Once tenant_id
    -- lands these become composite (tenant_id, cf_*) -- the same physical
    -- column holds "Lead Source" for one tenant and "Region" for another, so a
    -- single-column index on a slot would span unrelated fields.
    -- ----------------------------------------------------------------------
    cf_text_01 varchar(255), cf_text_02 varchar(255), cf_text_03 varchar(255), cf_text_04 varchar(255),
    cf_text_05 varchar(255), cf_text_06 varchar(255), cf_text_07 varchar(255), cf_text_08 varchar(255),
    cf_text_09 varchar(255), cf_text_10 varchar(255), cf_text_11 varchar(255), cf_text_12 varchar(255),
    cf_text_13 varchar(255), cf_text_14 varchar(255), cf_text_15 varchar(255), cf_text_16 varchar(255),
    cf_text_17 varchar(255), cf_text_18 varchar(255), cf_text_19 varchar(255), cf_text_20 varchar(255),
    cf_text_21 varchar(255), cf_text_22 varchar(255), cf_text_23 varchar(255), cf_text_24 varchar(255),
    cf_text_25 varchar(255), cf_text_26 varchar(255), cf_text_27 varchar(255), cf_text_28 varchar(255),
    cf_text_29 varchar(255), cf_text_30 varchar(255), cf_text_31 varchar(255), cf_text_32 varchar(255),
    cf_text_33 varchar(255), cf_text_34 varchar(255), cf_text_35 varchar(255), cf_text_36 varchar(255),
    cf_text_37 varchar(255), cf_text_38 varchar(255), cf_text_39 varchar(255), cf_text_40 varchar(255),

    -- numeric(19,4): 2 decimals for money, 4 so rates and percentages survive.
    cf_num_01 numeric(19, 4), cf_num_02 numeric(19, 4), cf_num_03 numeric(19, 4), cf_num_04 numeric(19, 4),
    cf_num_05 numeric(19, 4), cf_num_06 numeric(19, 4), cf_num_07 numeric(19, 4), cf_num_08 numeric(19, 4),
    cf_num_09 numeric(19, 4), cf_num_10 numeric(19, 4), cf_num_11 numeric(19, 4), cf_num_12 numeric(19, 4),
    cf_num_13 numeric(19, 4), cf_num_14 numeric(19, 4), cf_num_15 numeric(19, 4), cf_num_16 numeric(19, 4),
    cf_num_17 numeric(19, 4), cf_num_18 numeric(19, 4), cf_num_19 numeric(19, 4), cf_num_20 numeric(19, 4),
    cf_num_21 numeric(19, 4), cf_num_22 numeric(19, 4), cf_num_23 numeric(19, 4), cf_num_24 numeric(19, 4),
    cf_num_25 numeric(19, 4),

    cf_bool_01 boolean, cf_bool_02 boolean, cf_bool_03 boolean, cf_bool_04 boolean, cf_bool_05 boolean,
    cf_bool_06 boolean, cf_bool_07 boolean, cf_bool_08 boolean, cf_bool_09 boolean, cf_bool_10 boolean,
    cf_bool_11 boolean, cf_bool_12 boolean, cf_bool_13 boolean, cf_bool_14 boolean, cf_bool_15 boolean,
    cf_bool_16 boolean, cf_bool_17 boolean, cf_bool_18 boolean, cf_bool_19 boolean, cf_bool_20 boolean,
    cf_bool_21 boolean, cf_bool_22 boolean, cf_bool_23 boolean, cf_bool_24 boolean, cf_bool_25 boolean,

    -- date, not timestamptz: FieldDataType.DATE is a calendar date. A future
    -- DATETIME type gets its own pool rather than widening these.
    cf_date_01 date, cf_date_02 date, cf_date_03 date, cf_date_04 date, cf_date_05 date,
    cf_date_06 date, cf_date_07 date, cf_date_08 date, cf_date_09 date, cf_date_10 date,

    -- Long text (notes-style), unbounded so these TOAST individually. Map them
    -- @Basic(fetch = LAZY) in JPA -- which needs Hibernate bytecode enhancement
    -- to take effect -- so a list query doesn't detoast five of them per row.
    cf_longtext_01 text, cf_longtext_02 text, cf_longtext_03 text,
    cf_longtext_04 text, cf_longtext_05 text
);

create index idx_companies_name on app.companies (name);
create index idx_companies_owner_id on app.companies (owner_id);
create index idx_companies_archived on app.companies (archived_at) where archived_at is not null;
create index idx_companies_parent on app.companies (parent_company_id) where parent_company_id is not null;

-- --------------------------------------------------------------------------
-- iso_countries seed (generated; appended below)

insert into public.iso_countries (code, name)
values ('AF', 'Afghanistan'),
       ('AX', 'Åland Islands'),
       ('AL', 'Albania'),
       ('DZ', 'Algeria'),
       ('AS', 'American Samoa'),
       ('AD', 'Andorra'),
       ('AO', 'Angola'),
       ('AI', 'Anguilla'),
       ('AQ', 'Antarctica'),
       ('AG', 'Antigua & Barbuda'),
       ('AR', 'Argentina'),
       ('AM', 'Armenia'),
       ('AW', 'Aruba'),
       ('AU', 'Australia'),
       ('AT', 'Austria'),
       ('AZ', 'Azerbaijan'),
       ('BS', 'Bahamas'),
       ('BH', 'Bahrain'),
       ('BD', 'Bangladesh'),
       ('BB', 'Barbados'),
       ('BY', 'Belarus'),
       ('BE', 'Belgium'),
       ('BZ', 'Belize'),
       ('BJ', 'Benin'),
       ('BM', 'Bermuda'),
       ('BT', 'Bhutan'),
       ('BO', 'Bolivia'),
       ('BA', 'Bosnia & Herzegovina'),
       ('BW', 'Botswana'),
       ('BV', 'Bouvet Island'),
       ('BR', 'Brazil'),
       ('IO', 'British Indian Ocean Territory'),
       ('VG', 'British Virgin Islands'),
       ('BN', 'Brunei'),
       ('BG', 'Bulgaria'),
       ('BF', 'Burkina Faso'),
       ('BI', 'Burundi'),
       ('KH', 'Cambodia'),
       ('CM', 'Cameroon'),
       ('CA', 'Canada'),
       ('CV', 'Cape Verde'),
       ('BQ', 'Caribbean Netherlands'),
       ('KY', 'Cayman Islands'),
       ('CF', 'Central African Republic'),
       ('TD', 'Chad'),
       ('CL', 'Chile'),
       ('CN', 'China'),
       ('CX', 'Christmas Island'),
       ('CC', 'Cocos (Keeling) Islands'),
       ('CO', 'Colombia'),
       ('KM', 'Comoros'),
       ('CG', 'Congo - Brazzaville'),
       ('CD', 'Congo - Kinshasa'),
       ('CK', 'Cook Islands'),
       ('CR', 'Costa Rica'),
       ('CI', 'Côte d’Ivoire'),
       ('HR', 'Croatia'),
       ('CU', 'Cuba'),
       ('CW', 'Curaçao'),
       ('CY', 'Cyprus'),
       ('CZ', 'Czechia'),
       ('DK', 'Denmark'),
       ('DJ', 'Djibouti'),
       ('DM', 'Dominica'),
       ('DO', 'Dominican Republic'),
       ('EC', 'Ecuador'),
       ('EG', 'Egypt'),
       ('SV', 'El Salvador'),
       ('GQ', 'Equatorial Guinea'),
       ('ER', 'Eritrea'),
       ('EE', 'Estonia'),
       ('SZ', 'Eswatini'),
       ('ET', 'Ethiopia'),
       ('FK', 'Falkland Islands'),
       ('FO', 'Faroe Islands'),
       ('FJ', 'Fiji'),
       ('FI', 'Finland'),
       ('FR', 'France'),
       ('GF', 'French Guiana'),
       ('PF', 'French Polynesia'),
       ('TF', 'French Southern Territories'),
       ('GA', 'Gabon'),
       ('GM', 'Gambia'),
       ('GE', 'Georgia'),
       ('DE', 'Germany'),
       ('GH', 'Ghana'),
       ('GI', 'Gibraltar'),
       ('GR', 'Greece'),
       ('GL', 'Greenland'),
       ('GD', 'Grenada'),
       ('GP', 'Guadeloupe'),
       ('GU', 'Guam'),
       ('GT', 'Guatemala'),
       ('GG', 'Guernsey'),
       ('GN', 'Guinea'),
       ('GW', 'Guinea-Bissau'),
       ('GY', 'Guyana'),
       ('HT', 'Haiti'),
       ('HM', 'Heard & McDonald Islands'),
       ('HN', 'Honduras'),
       ('HK', 'Hong Kong SAR China'),
       ('HU', 'Hungary'),
       ('IS', 'Iceland'),
       ('IN', 'India'),
       ('ID', 'Indonesia'),
       ('IR', 'Iran'),
       ('IQ', 'Iraq'),
       ('IE', 'Ireland'),
       ('IM', 'Isle of Man'),
       ('IL', 'Israel'),
       ('IT', 'Italy'),
       ('JM', 'Jamaica'),
       ('JP', 'Japan'),
       ('JE', 'Jersey'),
       ('JO', 'Jordan'),
       ('KZ', 'Kazakhstan'),
       ('KE', 'Kenya'),
       ('KI', 'Kiribati'),
       ('XK', 'Kosovo'),
       ('KW', 'Kuwait'),
       ('KG', 'Kyrgyzstan'),
       ('LA', 'Laos'),
       ('LV', 'Latvia'),
       ('LB', 'Lebanon'),
       ('LS', 'Lesotho'),
       ('LR', 'Liberia'),
       ('LY', 'Libya'),
       ('LI', 'Liechtenstein'),
       ('LT', 'Lithuania'),
       ('LU', 'Luxembourg'),
       ('MO', 'Macao SAR China'),
       ('MG', 'Madagascar'),
       ('MW', 'Malawi'),
       ('MY', 'Malaysia'),
       ('MV', 'Maldives'),
       ('ML', 'Mali'),
       ('MT', 'Malta'),
       ('MH', 'Marshall Islands'),
       ('MQ', 'Martinique'),
       ('MR', 'Mauritania'),
       ('MU', 'Mauritius'),
       ('YT', 'Mayotte'),
       ('MX', 'Mexico'),
       ('FM', 'Micronesia'),
       ('MD', 'Moldova'),
       ('MC', 'Monaco'),
       ('MN', 'Mongolia'),
       ('ME', 'Montenegro'),
       ('MS', 'Montserrat'),
       ('MA', 'Morocco'),
       ('MZ', 'Mozambique'),
       ('MM', 'Myanmar (Burma)'),
       ('NA', 'Namibia'),
       ('NR', 'Nauru'),
       ('NP', 'Nepal'),
       ('NL', 'Netherlands'),
       ('NC', 'New Caledonia'),
       ('NZ', 'New Zealand'),
       ('NI', 'Nicaragua'),
       ('NE', 'Niger'),
       ('NG', 'Nigeria'),
       ('NU', 'Niue'),
       ('NF', 'Norfolk Island'),
       ('KP', 'North Korea'),
       ('MK', 'North Macedonia'),
       ('MP', 'Northern Mariana Islands'),
       ('NO', 'Norway'),
       ('OM', 'Oman'),
       ('PK', 'Pakistan'),
       ('PW', 'Palau'),
       ('PS', 'Palestinian Territories'),
       ('PA', 'Panama'),
       ('PG', 'Papua New Guinea'),
       ('PY', 'Paraguay'),
       ('PE', 'Peru'),
       ('PH', 'Philippines'),
       ('PN', 'Pitcairn Islands'),
       ('PL', 'Poland'),
       ('PT', 'Portugal'),
       ('PR', 'Puerto Rico'),
       ('QA', 'Qatar'),
       ('RE', 'Réunion'),
       ('RO', 'Romania'),
       ('RU', 'Russia'),
       ('RW', 'Rwanda'),
       ('WS', 'Samoa'),
       ('SM', 'San Marino'),
       ('ST', 'São Tomé & Príncipe'),
       ('CQ', 'Sark'),
       ('SA', 'Saudi Arabia'),
       ('SN', 'Senegal'),
       ('RS', 'Serbia'),
       ('SC', 'Seychelles'),
       ('SL', 'Sierra Leone'),
       ('SG', 'Singapore'),
       ('SX', 'Sint Maarten'),
       ('SK', 'Slovakia'),
       ('SI', 'Slovenia'),
       ('SB', 'Solomon Islands'),
       ('SO', 'Somalia'),
       ('ZA', 'South Africa'),
       ('GS', 'South Georgia & South Sandwich Islands'),
       ('KR', 'South Korea'),
       ('SS', 'South Sudan'),
       ('ES', 'Spain'),
       ('LK', 'Sri Lanka'),
       ('BL', 'St. Barthélemy'),
       ('SH', 'St. Helena'),
       ('KN', 'St. Kitts & Nevis'),
       ('LC', 'St. Lucia'),
       ('MF', 'St. Martin'),
       ('PM', 'St. Pierre & Miquelon'),
       ('VC', 'St. Vincent & Grenadines'),
       ('SD', 'Sudan'),
       ('SR', 'Suriname'),
       ('SJ', 'Svalbard & Jan Mayen'),
       ('SE', 'Sweden'),
       ('CH', 'Switzerland'),
       ('SY', 'Syria'),
       ('TW', 'Taiwan'),
       ('TJ', 'Tajikistan'),
       ('TZ', 'Tanzania'),
       ('TH', 'Thailand'),
       ('TL', 'Timor-Leste'),
       ('TG', 'Togo'),
       ('TK', 'Tokelau'),
       ('TO', 'Tonga'),
       ('TT', 'Trinidad & Tobago'),
       ('TN', 'Tunisia'),
       ('TR', 'Türkiye'),
       ('TM', 'Turkmenistan'),
       ('TC', 'Turks & Caicos Islands'),
       ('TV', 'Tuvalu'),
       ('UM', 'U.S. Outlying Islands'),
       ('VI', 'U.S. Virgin Islands'),
       ('UG', 'Uganda'),
       ('UA', 'Ukraine'),
       ('AE', 'United Arab Emirates'),
       ('GB', 'United Kingdom'),
       ('US', 'United States'),
       ('UY', 'Uruguay'),
       ('UZ', 'Uzbekistan'),
       ('VU', 'Vanuatu'),
       ('VA', 'Vatican City'),
       ('VE', 'Venezuela'),
       ('VN', 'Vietnam'),
       ('WF', 'Wallis & Futuna'),
       ('EH', 'Western Sahara'),
       ('YE', 'Yemen'),
       ('ZM', 'Zambia'),
       ('ZW', 'Zimbabwe');

create table app.company_contacts
(
    id         uuid primary key     default gen_random_uuid(),
    contact_id uuid references app.contacts (id),
    company_id uuid references app.companies (id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- --------------------------------------------------------------------------
-- app.deals  (opportunities). A flat record entity like companies and contacts. customer_id is
--   a nullable FK to the owning company; ON DELETE SET NULL keeps the deal if its
--   company is removed. Mapped in Java as a plain UUID (not a @ManyToOne), so a
--   deal serializes flat.
-- --------------------------------------------------------------------------
create table app.deals
(
    id          uuid primary key      default gen_random_uuid(),
    deal_name   varchar(255) not null,
    amount      numeric(19, 2),
    customer_id uuid         references app.companies (id) on delete set null,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),

    -- Custom field slot pool -- identical to app.companies (see the notes there),
    -- and LAST in the table for the same reason. Keep the three pools in step:
    -- one CustomFieldSlots embeddable maps all of them.
    cf_text_01 varchar(255), cf_text_02 varchar(255), cf_text_03 varchar(255), cf_text_04 varchar(255),
    cf_text_05 varchar(255), cf_text_06 varchar(255), cf_text_07 varchar(255), cf_text_08 varchar(255),
    cf_text_09 varchar(255), cf_text_10 varchar(255), cf_text_11 varchar(255), cf_text_12 varchar(255),
    cf_text_13 varchar(255), cf_text_14 varchar(255), cf_text_15 varchar(255), cf_text_16 varchar(255),
    cf_text_17 varchar(255), cf_text_18 varchar(255), cf_text_19 varchar(255), cf_text_20 varchar(255),
    cf_text_21 varchar(255), cf_text_22 varchar(255), cf_text_23 varchar(255), cf_text_24 varchar(255),
    cf_text_25 varchar(255), cf_text_26 varchar(255), cf_text_27 varchar(255), cf_text_28 varchar(255),
    cf_text_29 varchar(255), cf_text_30 varchar(255), cf_text_31 varchar(255), cf_text_32 varchar(255),
    cf_text_33 varchar(255), cf_text_34 varchar(255), cf_text_35 varchar(255), cf_text_36 varchar(255),
    cf_text_37 varchar(255), cf_text_38 varchar(255), cf_text_39 varchar(255), cf_text_40 varchar(255),

    cf_num_01 numeric(19, 4), cf_num_02 numeric(19, 4), cf_num_03 numeric(19, 4), cf_num_04 numeric(19, 4),
    cf_num_05 numeric(19, 4), cf_num_06 numeric(19, 4), cf_num_07 numeric(19, 4), cf_num_08 numeric(19, 4),
    cf_num_09 numeric(19, 4), cf_num_10 numeric(19, 4), cf_num_11 numeric(19, 4), cf_num_12 numeric(19, 4),
    cf_num_13 numeric(19, 4), cf_num_14 numeric(19, 4), cf_num_15 numeric(19, 4), cf_num_16 numeric(19, 4),
    cf_num_17 numeric(19, 4), cf_num_18 numeric(19, 4), cf_num_19 numeric(19, 4), cf_num_20 numeric(19, 4),
    cf_num_21 numeric(19, 4), cf_num_22 numeric(19, 4), cf_num_23 numeric(19, 4), cf_num_24 numeric(19, 4),
    cf_num_25 numeric(19, 4),

    cf_bool_01 boolean, cf_bool_02 boolean, cf_bool_03 boolean, cf_bool_04 boolean, cf_bool_05 boolean,
    cf_bool_06 boolean, cf_bool_07 boolean, cf_bool_08 boolean, cf_bool_09 boolean, cf_bool_10 boolean,
    cf_bool_11 boolean, cf_bool_12 boolean, cf_bool_13 boolean, cf_bool_14 boolean, cf_bool_15 boolean,
    cf_bool_16 boolean, cf_bool_17 boolean, cf_bool_18 boolean, cf_bool_19 boolean, cf_bool_20 boolean,
    cf_bool_21 boolean, cf_bool_22 boolean, cf_bool_23 boolean, cf_bool_24 boolean, cf_bool_25 boolean,

    cf_date_01 date, cf_date_02 date, cf_date_03 date, cf_date_04 date, cf_date_05 date,
    cf_date_06 date, cf_date_07 date, cf_date_08 date, cf_date_09 date, cf_date_10 date,

    cf_longtext_01 text, cf_longtext_02 text, cf_longtext_03 text,
    cf_longtext_04 text, cf_longtext_05 text
);

create index idx_deals_customer on app.deals (customer_id) where customer_id is not null;

-- --------------------------------------------------------------------------
-- app.custom_field_definitions
--   One row = one custom field available on one entity type, AND the physical
--   slot on that entity's table where its values live (column_name). This
--   table IS the mapping from the tenant's vocabulary to the column pool:
--   without a row here, a cf_* column on app.companies is unreadable.
--   * single-tenant for now: tenant_id is deferred (see commented column).
--   * entity_type / data_type are constrained to the Java enum values so a bad
--     string can't slip in via SQL -- Hibernate would only choke on it at read.
--   * field_key, data_type and column_name are all write-once at the app level
--     (the entity has no setters): renaming the key breaks the layout's flat
--     keyspace, and changing the type or the slot would strand existing values
--     in a column of the wrong type.
--   * deleting a field archives it (archived_at). The slot stays allocated --
--     an archived field's values must remain readable, and reusing the column
--     would silently hand one field's data to another. Freeing a slot is a
--     separate, explicit purge: null the column on every row, then delete this
--     row, which releases both field_key and column_name.
-- --------------------------------------------------------------------------
create table app.custom_field_definitions
(
    id          uuid primary key       default gen_random_uuid(),
    -- tenant_id uuid not null,         -- added with the multitenancy layer (+ RLS)

    entity_type varchar(50)   not null
        constraint ck_cfd_entity_type check (entity_type in ('COMPANY', 'CONTACT', 'DEAL')),
    field_key   public.citext not null,            -- the record's JSON key (write-once)
    label       varchar(255)  not null,            -- human label for the UI (mutable)

    data_type   varchar(30)   not null
        constraint ck_cfd_data_type
            check (data_type in ('TEXT', 'URL', 'PHONE', 'EMAIL', 'NUMBER', 'BOOLEAN', 'DATE', 'LONGTEXT')),

    -- Which physical slot on the entity's table holds this field's values, e.g.
    -- 'cf_text_03'. Assigned by the service from the first free slot of the
    -- pool matching data_type. Per TENANT once tenant_id lands: the same
    -- cf_text_03 column holds "Lead Source" for one tenant and "Region" for
    -- another, which is why any index on a slot must be composite on tenant_id.
    column_name varchar(20)   not null
        constraint ck_cfd_column_name
            check (column_name ~ '^cf_(text|num|date|bool|longtext)_[0-9]{2}$'),

    required    boolean       not null default false,
    sort_order  int           not null default 0,

    -- type-specific rules (picklist options, min/max, regex, a stricter length
    -- than the slot's own varchar(255) ...). Empty until a field type needs it;
    -- here now so adding one needs no schema change.
    config      jsonb         not null default '{}',

    -- archive instead of hard delete. NULL = active.
    archived_at timestamptz,

    created_at  timestamptz   not null default now(),
    updated_at  timestamptz   not null default now(),

    -- length cap that citext (unlike varchar(n)) doesn't carry itself
    constraint ck_cfd_field_key_len check (length(field_key) <= 100),

    -- A field must hold a slot from the pool matching its type, so "NUMBER
    -- field parked in a text column" is unrepresentable rather than merely
    -- discouraged. The underscores are escaped because a bare _ is a
    -- single-character wildcard in LIKE.
    constraint ck_cfd_type_matches_slot check (
        case data_type
            when 'TEXT' then column_name like 'cf\_text\_%'
            when 'URL' then column_name like 'cf\_text\_%'
            when 'PHONE' then column_name like 'cf\_text\_%'
            when 'EMAIL' then column_name like 'cf\_text\_%'
            when 'NUMBER' then column_name like 'cf\_num\_%'
            when 'BOOLEAN' then column_name like 'cf\_bool\_%'
            when 'DATE' then column_name like 'cf\_date\_%'
            when 'LONGTEXT' then column_name like 'cf\_longtext\_%'
            else false
            end
        ),

    -- field_key unique per entity type; case-insensitive because field_key is
    -- citext. Spans archived rows: the key stays reserved until purge.
    -- Becomes (tenant_id, entity_type, field_key) once tenant_id lands.
    constraint uq_custom_field_key unique (entity_type, field_key),

    -- One definition per slot. This is also the concurrency guard on slot
    -- allocation: two simultaneous creations that pick the same free slot race
    -- here, the loser takes a unique violation, and the service retries with
    -- the next free slot. Becomes (tenant_id, entity_type, column_name) later.
    constraint uq_cfd_column unique (entity_type, column_name)
);

-- The read paths are findByEntityType (the write path's "known" set) and the
-- active-and-ordered form query. The table is small -- dozens of rows per
-- tenant -- so this single index is plenty; it mainly keeps the plan stable.
create index idx_custom_field_defs_entity on app.custom_field_definitions (entity_type);

-- --------------------------------------------------------------------------
-- app.entity_layouts  (the "mother" row)
--   One row = one named layout for one entity type. Pure presentation config;
--   owns no field values. Real FKs + ON DELETE CASCADE down the tree, so deleting
--   a layout cleanly removes its sections and their fields (no archive lifecycle,
--   unlike custom_field_definitions: a layout row is structure, not data).
--   * single-tenant for now: tenant_id deferred (see commented column).
--   * is_default picks which layout GET /{entityType} serves; assignment rules
--     (per profile / record type) are a future concern.
-- --------------------------------------------------------------------------
create table app.entity_layouts
(
    id          uuid primary key      default gen_random_uuid(),
    -- tenant_id uuid not null,         -- added with the multitenancy layer (+ RLS)

    entity_type varchar(50)  not null
        constraint ck_layout_entity_type check (entity_type in ('COMPANY', 'CONTACT', 'DEAL')),
    label       varchar(255) not null,
    is_default  boolean      not null default false,

    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

-- at most one default layout per entity type (becomes (tenant_id, entity_type) later)
create unique index uq_entity_layout_default
    on app.entity_layouts (entity_type) where is_default;

-- --------------------------------------------------------------------------
-- app.entity_layout_sections  (children of a layout)
--   column_count: 1- or 2-column sections are near-universal in CRM layouts;
--   one cheap int now beats a migration later (same rationale as custom_field_definitions.config).
-- --------------------------------------------------------------------------
create table app.entity_layout_sections
(
    id           uuid primary key      default gen_random_uuid(),
    layout_id    uuid         not null
        references app.entity_layouts (id) on delete cascade,
    label        varchar(255) not null,
    column_count int          not null default 1
        constraint ck_section_column_count check (column_count between 1 and 2),
    sort_order   int          not null default 0,

    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now()
);

create index idx_layout_sections_layout on app.entity_layout_sections (layout_id);

-- --------------------------------------------------------------------------
-- app.entity_layout_section_fields  (a field placed inside a section)
--   field_key alone identifies the field (one flat keyspace per entity): it is the
--   entity's JSON property name for a built-in field ('website', 'legalName') or a
--   custom field's field_key ('lead_source') -- the key the frontend reads the value
--   under, never dereferenced as a SQL column. No FK to the field definition by
--   design -- validated in the service, like the custom field slot values. A uniform
--   string key keeps every placed field homogeneous (one render path).
-- --------------------------------------------------------------------------
create table app.entity_layout_section_fields
(
    id           uuid primary key       default gen_random_uuid(),
    section_id   uuid          not null
        references app.entity_layout_sections (id) on delete cascade,
    field_key    public.citext not null,
    sort_order   int           not null default 0,

    created_at   timestamptz   not null default now(),
    updated_at   timestamptz   not null default now(),

    constraint ck_lsf_field_key_len check (length(field_key) <= 100),
    -- a given field is placed at most once per section (case-insensitive via citext).
    -- "once per layout" is stricter than the DB can express without denormalizing
    -- layout_id onto this row; left to the service-layer validator instead.
    constraint uq_section_field unique (section_id, field_key)
);

create index idx_layout_section_fields_section
    on app.entity_layout_section_fields (section_id);

-- --------------------------------------------------------------------------
-- Seed: one default layout per entity type, so every detail page has an arrangement
-- to render. Fixed UUIDs so the trees are stable across DB wipes. Every required field
-- is placed (a field that is on no layout can't be filled in on create). The company
-- layout places a custom field too ('lead_source', seeded here) next to built-in ones.
-- --------------------------------------------------------------------------
insert into app.custom_field_definitions
    (entity_type, field_key, label, data_type, column_name, required, sort_order)
values ('COMPANY', 'lead_source', 'Lead Source', 'TEXT', 'cf_text_01', false, 10);

insert into app.entity_layouts (id, entity_type, label, is_default)
values ('a0000000-0000-0000-0000-000000000001', 'COMPANY', 'Default', true),
       ('a0000000-0000-0000-0000-000000000002', 'CONTACT', 'Default', true),
       ('a0000000-0000-0000-0000-000000000003', 'DEAL', 'Default', true);

insert into app.entity_layout_sections (id, layout_id, label, column_count, sort_order)
values ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001', 'Overview', 2, 10),
       ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000001', 'Contact details', 2, 20),
       ('b0000000-0000-0000-0000-000000000003', 'a0000000-0000-0000-0000-000000000001', 'About', 1, 30),
       ('b0000000-0000-0000-0000-000000000004', 'a0000000-0000-0000-0000-000000000002', 'Contact', 2, 10),
       ('b0000000-0000-0000-0000-000000000005', 'a0000000-0000-0000-0000-000000000002', 'Address', 2, 20),
       ('b0000000-0000-0000-0000-000000000006', 'a0000000-0000-0000-0000-000000000003', 'Deal', 2, 10);

insert into app.entity_layout_section_fields (section_id, field_key, sort_order)
values -- COMPANY / Overview
       ('b0000000-0000-0000-0000-000000000001', 'name', 10),
       ('b0000000-0000-0000-0000-000000000001', 'legalName', 20),
       ('b0000000-0000-0000-0000-000000000001', 'type', 30),
       ('b0000000-0000-0000-0000-000000000001', 'industry', 40),
       ('b0000000-0000-0000-0000-000000000001', 'employeeRange', 50),
       ('b0000000-0000-0000-0000-000000000001', 'lead_source', 60),
       -- COMPANY / Contact details
       ('b0000000-0000-0000-0000-000000000002', 'website', 10),
       ('b0000000-0000-0000-0000-000000000002', 'phone', 20),
       ('b0000000-0000-0000-0000-000000000002', 'phoneAlt', 30),
       ('b0000000-0000-0000-0000-000000000002', 'vatNumber', 40),
       -- COMPANY / About
       ('b0000000-0000-0000-0000-000000000003', 'description', 10),
       -- CONTACT / Contact
       ('b0000000-0000-0000-0000-000000000004', 'salutation', 10),
       ('b0000000-0000-0000-0000-000000000004', 'firstName', 20),
       ('b0000000-0000-0000-0000-000000000004', 'lastName', 30),
       ('b0000000-0000-0000-0000-000000000004', 'email', 40),
       ('b0000000-0000-0000-0000-000000000004', 'phone', 50),
       -- CONTACT / Address
       ('b0000000-0000-0000-0000-000000000005', 'street', 10),
       ('b0000000-0000-0000-0000-000000000005', 'city', 20),
       ('b0000000-0000-0000-0000-000000000005', 'state', 30),
       ('b0000000-0000-0000-0000-000000000005', 'postalCode', 40),
       ('b0000000-0000-0000-0000-000000000005', 'countryCode', 50),
       -- DEAL / Deal
       ('b0000000-0000-0000-0000-000000000006', 'dealName', 10),
       ('b0000000-0000-0000-0000-000000000006', 'amount', 20),
       ('b0000000-0000-0000-0000-000000000006', 'customerId', 30);
