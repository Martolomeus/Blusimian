-- V1: initial schema. Flyway runs this once and records it in
-- flyway_schema_history so it never runs again. The next change goes in
-- V2__something.sql -- you never edit a migration that has already run.
--
-- Schema layout:
--   * public = global reference data (iso_countries).
--   * app    = tenant-scoped, RLS on every table.
--
-- Runs as blusimian_owner, which owns every object here. The app connects as
-- blusimian_app (docker/initdb/01-roles.sql), which owns nothing and gets only
-- the grants below. See Architecture/multitenancy.md.

create schema if not exists app;

grant usage on schema app to blusimian_app;
-- Explicit, because `make db-reset` recreates public without the default grant.
grant usage on schema public to blusimian_app;

-- Case-insensitive text ('ALICE' = 'alice', but 'é' <> 'e'). Not citext: citext
-- equality isn't leakproof, so under RLS it can't use an index. On Postgres 16,
-- LIKE on these columns needs a deterministic collation: email collate "default" ilike ?
create collation app.case_insensitive (provider = icu, locale = 'und-u-ks-level2', deterministic = false);

-- The tenant of the current transaction, set by TenantBindingJpaDialect. Raises
-- when unset (NULL in a fresh session, '' after a transaction-local value ends),
-- so a missing tenant is an error, never an empty result.
create function app.current_tenant_id() returns uuid
    language plpgsql
    stable
    parallel safe -- or no query on a tenant table could get a parallel plan
as
$$
declare
    v text := nullif(current_setting('app.tenant_id', true), '');
begin
    if v is null then
        raise exception 'tenant context not set (app.tenant_id)';
    end if;
    return v::uuid;
end
$$;

-- --------------------------------------------------------------------------
-- app.tenants: its id IS the tenant, so its policy compares id. No delete
-- grant: offboarding is still undecided.
-- --------------------------------------------------------------------------
create table app.tenants
(
    id         uuid primary key, -- assigned by TenantProvisioningService
    name       varchar(255) not null,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);

alter table app.tenants enable row level security;

create policy tenant_isolation on app.tenants
    to blusimian_app
    using (id = (select app.current_tenant_id()))
    with check (id = (select app.current_tenant_id()));

grant select, insert, update on app.tenants to blusimian_app;

-- --------------------------------------------------------------------------
-- Every table below carries the same tenant block:
--   * tenant_id as the second column, filled by its default from the
--     transaction's tenant. Never mapped in JPA.
--   * primary key (tenant_id, id), and composite FKs to it: FK checks ignore
--     RLS, so a plain FK could point at another tenant's row.
--   * every unique and index leads with tenant_id.
--   * RLS with the one tenant_isolation policy, and exactly select, insert,
--     update, delete for blusimian_app (TRUNCATE would ignore RLS).
-- RowLevelSecurityVerifier checks the RLS, policy and grant parts at startup.
-- --------------------------------------------------------------------------

-- --------------------------------------------------------------------------
-- app.users
-- --------------------------------------------------------------------------
create table app.users
(
    id            uuid         not null default gen_random_uuid(),
    tenant_id     uuid         not null default app.current_tenant_id()
        references app.tenants (id),
    username      varchar(100) collate app.case_insensitive not null,
    password_hash text         not null, -- DelegatingPasswordEncoder: {bcrypt}$2a$10$...
    display_name  varchar(255) not null,
    enabled       boolean      not null default true,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now(),

    constraint pk_users primary key (tenant_id, id),
    -- Global on purpose: login is username + password, with no tenant picker.
    constraint uq_users_username unique (username)
);

alter table app.users enable row level security;

create policy tenant_isolation on app.users
    to blusimian_app
    using (tenant_id = (select app.current_tenant_id()))
    with check (tenant_id = (select app.current_tenant_id()));

grant select, insert, update, delete on app.users to blusimian_app;

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

grant select on public.iso_countries to blusimian_app;

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

-- --------------------------------------------------------------------------
-- app.contacts
-- --------------------------------------------------------------------------
create table app.contacts
(
    id                  uuid         not null default gen_random_uuid(),
    tenant_id           uuid         not null default app.current_tenant_id()
        references app.tenants (id),
    first_name          varchar(100) not null,
    last_name           varchar(100) not null,
    salutation          varchar(50),
    email               text collate app.case_insensitive,
    phone               varchar(50),
    address_street      varchar(255),
    address_city        varchar(100),
    address_state       varchar(100),
    address_postal_code varchar(20),
    address_country     char(2),
    created_at          timestamptz  not null default now(),
    updated_at          timestamptz  not null default now(),

    constraint pk_contacts primary key (tenant_id, id),

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

alter table app.contacts enable row level security;

create policy tenant_isolation on app.contacts
    to blusimian_app
    using (tenant_id = (select app.current_tenant_id()))
    with check (tenant_id = (select app.current_tenant_id()));

grant select, insert, update, delete on app.contacts to blusimian_app;

-- --------------------------------------------------------------------------
-- app.companies (a.k.a. "accounts")
--   * owner_id / created_by_id / account_manager_id reference app.users.
--   * custom field VALUES live in the cf_* slot pool at the bottom of this
--     table, NOT in a jsonb blob. Which slot a given custom field owns is
--     recorded in app.custom_field_definitions.column_name; without that row a
--     cf_* column is meaningless. Typed columns buy real indexes, real sorting,
--     real planner statistics and per-field writes -- see the notes on the pool.
-- --------------------------------------------------------------------------
create table app.companies
(
    id                           uuid         not null default gen_random_uuid(),
    tenant_id                    uuid         not null default app.current_tenant_id()
        references app.tenants (id),
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

    parent_company_id            uuid,
    account_manager_id           uuid,
    owner_id                     uuid,
    created_by_id                uuid,

    vat_number                   varchar(50),

    archived_at                  timestamptz,
    created_at                   timestamptz  not null default now(),
    updated_at                   timestamptz  not null default now(),

    constraint pk_companies primary key (tenant_id, id),
    constraint fk_companies_parent_company_id foreign key (tenant_id, parent_company_id)
        references app.companies (tenant_id, id) on delete set null (parent_company_id),
    constraint fk_companies_account_manager_id foreign key (tenant_id, account_manager_id)
        references app.users (tenant_id, id) on delete set null (account_manager_id),
    constraint fk_companies_owner_id foreign key (tenant_id, owner_id)
        references app.users (tenant_id, id) on delete set null (owner_id),
    constraint fk_companies_created_by_id foreign key (tenant_id, created_by_id)
        references app.users (tenant_id, id) on delete set null (created_by_id),

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
    --       on app.companies (tenant_id, cf_text_03);
    -- which is online, reversible, and invisible to Hibernate. It leads with
    -- tenant_id because the same physical column holds "Lead Source" for one
    -- tenant and "Region" for another.
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

create index idx_companies_name on app.companies (tenant_id, name);
create index idx_companies_owner_id on app.companies (tenant_id, owner_id);
create index idx_companies_archived on app.companies (tenant_id, archived_at) where archived_at is not null;
create index idx_companies_parent on app.companies (tenant_id, parent_company_id) where parent_company_id is not null;

alter table app.companies enable row level security;

create policy tenant_isolation on app.companies
    to blusimian_app
    using (tenant_id = (select app.current_tenant_id()))
    with check (tenant_id = (select app.current_tenant_id()));

grant select, insert, update, delete on app.companies to blusimian_app;

-- --------------------------------------------------------------------------
-- app.company_contacts  (link rows between a company and a contact)
-- --------------------------------------------------------------------------
create table app.company_contacts
(
    id         uuid        not null default gen_random_uuid(),
    tenant_id  uuid        not null default app.current_tenant_id()
        references app.tenants (id),
    contact_id uuid,
    company_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint pk_company_contacts primary key (tenant_id, id),
    constraint fk_company_contacts_contact_id foreign key (tenant_id, contact_id)
        references app.contacts (tenant_id, id),
    constraint fk_company_contacts_company_id foreign key (tenant_id, company_id)
        references app.companies (tenant_id, id)
);

-- findByCompanyId / findByContactId, and the FK checks when a company or contact is deleted.
create index idx_company_contacts_company on app.company_contacts (tenant_id, company_id);
create index idx_company_contacts_contact on app.company_contacts (tenant_id, contact_id);

alter table app.company_contacts enable row level security;

create policy tenant_isolation on app.company_contacts
    to blusimian_app
    using (tenant_id = (select app.current_tenant_id()))
    with check (tenant_id = (select app.current_tenant_id()));

grant select, insert, update, delete on app.company_contacts to blusimian_app;

-- --------------------------------------------------------------------------
-- app.deals  (opportunities). A flat record entity like companies and contacts. customer_id is
--   a nullable FK to the owning company; ON DELETE SET NULL keeps the deal if its
--   company is removed. Mapped in Java as a plain UUID (not a @ManyToOne), so a
--   deal serializes flat.
-- --------------------------------------------------------------------------
create table app.deals
(
    id          uuid         not null default gen_random_uuid(),
    tenant_id   uuid         not null default app.current_tenant_id()
        references app.tenants (id),
    deal_name   varchar(255) not null,
    amount      numeric(19, 2),
    customer_id uuid,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),

    constraint pk_deals primary key (tenant_id, id),
    -- DealService turns a violation into a 400 by finding "customer_id" in this name.
    constraint fk_deals_customer_id foreign key (tenant_id, customer_id)
        references app.companies (tenant_id, id) on delete set null (customer_id),

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

create index idx_deals_customer on app.deals (tenant_id, customer_id) where customer_id is not null;

alter table app.deals enable row level security;

create policy tenant_isolation on app.deals
    to blusimian_app
    using (tenant_id = (select app.current_tenant_id()))
    with check (tenant_id = (select app.current_tenant_id()));

grant select, insert, update, delete on app.deals to blusimian_app;

-- --------------------------------------------------------------------------
-- app.custom_field_definitions
--   One row = one custom field available on one entity type, AND the physical
--   slot on that entity's table where its values live (column_name). This
--   table IS the mapping from the tenant's vocabulary to the column pool:
--   without a row here, a cf_* column on app.companies is unreadable.
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
    id          uuid         not null default gen_random_uuid(),
    tenant_id   uuid         not null default app.current_tenant_id()
        references app.tenants (id),

    entity_type varchar(50)  not null
        constraint ck_cfd_entity_type check (entity_type in ('COMPANY', 'CONTACT', 'DEAL')),
    field_key   varchar(100) collate app.case_insensitive not null, -- the record's JSON key (write-once)
    label       varchar(255) not null,                              -- human label for the UI (mutable)

    data_type   varchar(30)  not null
        constraint ck_cfd_data_type
            check (data_type in ('TEXT', 'URL', 'PHONE', 'EMAIL', 'NUMBER', 'BOOLEAN', 'DATE', 'LONGTEXT')),

    -- Which physical slot on the entity's table holds this field's values, e.g.
    -- 'cf_text_03'. Assigned by the service from the first free slot of the
    -- pool matching data_type. Per tenant: the same cf_text_03 column holds
    -- "Lead Source" for one tenant and "Region" for another, which is why any
    -- index on a slot must be composite on tenant_id.
    column_name varchar(20)  not null
        constraint ck_cfd_column_name
            check (column_name ~ '^cf_(text|num|date|bool|longtext)_[0-9]{2}$'),

    required    boolean      not null default false,
    sort_order  int          not null default 0,

    -- type-specific rules (picklist options, min/max, regex, a stricter length
    -- than the slot's own varchar(255) ...). Empty until a field type needs it;
    -- here now so adding one needs no schema change.
    config      jsonb        not null default '{}',

    -- archive instead of hard delete. NULL = active.
    archived_at timestamptz,

    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),

    constraint pk_custom_field_definitions primary key (tenant_id, id),

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

    -- field_key unique per tenant and entity type, case-insensitive through its
    -- collation. Spans archived rows: the key stays reserved until purge. Its
    -- leading (tenant_id, entity_type) also serves the by-entity-type reads.
    constraint uq_custom_field_key unique (tenant_id, entity_type, field_key),

    -- One definition per slot. This is also the concurrency guard on slot
    -- allocation: two simultaneous creations that pick the same free slot race
    -- here, the loser takes a unique violation, and the service retries with
    -- the next free slot.
    constraint uq_cfd_column unique (tenant_id, entity_type, column_name)
);

alter table app.custom_field_definitions enable row level security;

create policy tenant_isolation on app.custom_field_definitions
    to blusimian_app
    using (tenant_id = (select app.current_tenant_id()))
    with check (tenant_id = (select app.current_tenant_id()));

grant select, insert, update, delete on app.custom_field_definitions to blusimian_app;

-- --------------------------------------------------------------------------
-- app.entity_layouts  (the "mother" row)
--   One row = one named layout for one entity type. Pure presentation config;
--   owns no field values. Real FKs + ON DELETE CASCADE down the tree, so deleting
--   a layout cleanly removes its sections and their fields (no archive lifecycle,
--   unlike custom_field_definitions: a layout row is structure, not data).
--   * is_default picks which layout GET /{entityType} serves; assignment rules
--     (per profile / record type) are a future concern.
-- --------------------------------------------------------------------------
create table app.entity_layouts
(
    id          uuid         not null default gen_random_uuid(),
    tenant_id   uuid         not null default app.current_tenant_id()
        references app.tenants (id),

    entity_type varchar(50)  not null
        constraint ck_layout_entity_type check (entity_type in ('COMPANY', 'CONTACT', 'DEAL')),
    label       varchar(255) not null,
    is_default  boolean      not null default false,

    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),

    constraint pk_entity_layouts primary key (tenant_id, id)
);

-- at most one default layout per tenant and entity type
create unique index uq_entity_layout_default
    on app.entity_layouts (tenant_id, entity_type) where is_default;

alter table app.entity_layouts enable row level security;

create policy tenant_isolation on app.entity_layouts
    to blusimian_app
    using (tenant_id = (select app.current_tenant_id()))
    with check (tenant_id = (select app.current_tenant_id()));

grant select, insert, update, delete on app.entity_layouts to blusimian_app;

-- --------------------------------------------------------------------------
-- app.entity_layout_sections  (children of a layout)
--   column_count: 1- or 2-column sections are near-universal in CRM layouts;
--   one cheap int now beats a migration later (same rationale as custom_field_definitions.config).
-- --------------------------------------------------------------------------
create table app.entity_layout_sections
(
    id           uuid         not null default gen_random_uuid(),
    tenant_id    uuid         not null default app.current_tenant_id()
        references app.tenants (id),
    layout_id    uuid         not null,
    label        varchar(255) not null,
    column_count int          not null default 1
        constraint ck_section_column_count check (column_count between 1 and 2),
    sort_order   int          not null default 0,

    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),

    constraint pk_entity_layout_sections primary key (tenant_id, id),
    constraint fk_entity_layout_sections_layout_id foreign key (tenant_id, layout_id)
        references app.entity_layouts (tenant_id, id) on delete cascade
);

create index idx_layout_sections_layout on app.entity_layout_sections (tenant_id, layout_id);

alter table app.entity_layout_sections enable row level security;

create policy tenant_isolation on app.entity_layout_sections
    to blusimian_app
    using (tenant_id = (select app.current_tenant_id()))
    with check (tenant_id = (select app.current_tenant_id()));

grant select, insert, update, delete on app.entity_layout_sections to blusimian_app;

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
    id           uuid         not null default gen_random_uuid(),
    tenant_id    uuid         not null default app.current_tenant_id()
        references app.tenants (id),
    section_id   uuid         not null,
    field_key    varchar(100) collate app.case_insensitive not null,
    sort_order   int          not null default 0,

    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),

    constraint pk_entity_layout_section_fields primary key (tenant_id, id),
    constraint fk_entity_layout_section_fields_section_id foreign key (tenant_id, section_id)
        references app.entity_layout_sections (tenant_id, id) on delete cascade,
    -- a given field is placed at most once per section (case-insensitive through
    -- the collation). "once per layout" is stricter than the DB can express without
    -- denormalizing layout_id onto this row; left to the service-layer validator instead.
    -- Its leading (tenant_id, section_id) also serves the FK.
    constraint uq_section_field unique (tenant_id, section_id, field_key)
);

alter table app.entity_layout_section_fields enable row level security;

create policy tenant_isolation on app.entity_layout_section_fields
    to blusimian_app
    using (tenant_id = (select app.current_tenant_id()))
    with check (tenant_id = (select app.current_tenant_id()));

grant select, insert, update, delete on app.entity_layout_section_fields to blusimian_app;

-- --------------------------------------------------------------------------
-- app.find_login_user: the one read before a tenant is known. SECURITY DEFINER
-- runs it as the owner, which RLS doesn't apply to, so it is kept narrow: one
-- row by exact username, nothing writable. Every name in it is schema-qualified,
-- the collation included, because search_path is pinned to pg_catalog.
-- --------------------------------------------------------------------------
create function app.find_login_user(p_username varchar)
    returns table
            (
                user_id       uuid,
                tenant_id     uuid,
                username      varchar,
                password_hash text,
                display_name  varchar,
                enabled       boolean
            )
    language sql
    stable
    security definer
    set search_path = pg_catalog
as
$$
select u.id, u.tenant_id, u.username, u.password_hash, u.display_name, u.enabled
from app.users u
where u.username = p_username collate app.case_insensitive
$$;

revoke execute on function app.find_login_user(varchar) from public;
grant execute on function app.find_login_user(varchar) to blusimian_app;
