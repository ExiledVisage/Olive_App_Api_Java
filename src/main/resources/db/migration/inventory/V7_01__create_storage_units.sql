-- storage_units: tanklar: tur (son tank, on dinlendirme), kapasite ve birimi; seviye olcumu icin geometri (istege bagli)

CREATE TABLE storage_units (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(255) NOT NULL,
    capacity numeric(14,2) NOT NULL,
    capacity_unit character varying(255) NOT NULL,
    is_blend_tank boolean DEFAULT false NOT NULL,
    allow_multiple_production_product_types boolean DEFAULT false NOT NULL,
    allow_multiple_production_products boolean DEFAULT false NOT NULL,
    description text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    kind character varying(255) DEFAULT 'final'::character varying NOT NULL,
    inner_diameter_mm numeric(10,2),
    cylinder_height_mm numeric(10,2),
    bottom_cone_deg numeric(5,2),
    top_cone_deg numeric(5,2),
    ruler_offset_mm numeric(10,2),
    dead_volume_litre numeric(10,2),
    CONSTRAINT storage_units_capacity_unit_check CHECK (capacity_unit IN ('KG', 'LITRE')),
    CONSTRAINT storage_units_kind_check CHECK (kind IN ('final', 'pre_settling'))
);

CREATE SEQUENCE storage_units_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE storage_units_id_seq OWNED BY storage_units.id;

ALTER TABLE ONLY storage_units ALTER COLUMN id SET DEFAULT nextval('storage_units_id_seq'::regclass);

ALTER TABLE ONLY storage_units
    ADD CONSTRAINT storage_units_code_unique UNIQUE (code);

ALTER TABLE ONLY storage_units
    ADD CONSTRAINT storage_units_pkey PRIMARY KEY (id);

CREATE INDEX storage_units_allow_multiple_production_product_types_index ON storage_units
    USING btree (allow_multiple_production_product_types);

CREATE INDEX storage_units_allow_multiple_production_products_index ON storage_units
    USING btree (allow_multiple_production_products);

CREATE INDEX storage_units_is_active_index ON storage_units USING btree (is_active);

CREATE INDEX storage_units_is_blend_tank_index ON storage_units USING btree (is_blend_tank);
