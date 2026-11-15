-- storage_unit_allowed_production_product_types: tanka girebilen urun tipleri

CREATE TABLE storage_unit_allowed_production_product_types (
    id bigint NOT NULL,
    storage_unit_id bigint NOT NULL,
    production_product_type_id bigint NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE storage_unit_allowed_production_product_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE storage_unit_allowed_production_product_types_id_seq OWNED BY storage_unit_allowed_production_product_types.id;

ALTER TABLE ONLY storage_unit_allowed_production_product_types ALTER COLUMN id SET DEFAULT nextval('storage_unit_allowed_production_product_types_id_seq'::regclass);

ALTER TABLE ONLY storage_unit_allowed_production_product_types
    ADD CONSTRAINT storage_unit_allowed_production_product_types_pkey PRIMARY KEY (id);

ALTER TABLE ONLY storage_unit_allowed_production_product_types
    ADD CONSTRAINT storage_unit_allowed_production_product_types_storage_unit_id_p UNIQUE (storage_unit_id, production_product_type_id);

ALTER TABLE ONLY storage_unit_allowed_production_product_types
    ADD CONSTRAINT storage_unit_allowed_production_product_types_production_produc FOREIGN KEY (production_product_type_id)
        REFERENCES production_product_types(id) ON DELETE RESTRICT;

ALTER TABLE ONLY storage_unit_allowed_production_product_types
    ADD CONSTRAINT storage_unit_allowed_production_product_types_storage_unit_id_f FOREIGN KEY (storage_unit_id)
        REFERENCES storage_units(id) ON DELETE CASCADE;
