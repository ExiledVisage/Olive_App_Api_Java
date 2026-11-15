-- production_product_types: urun tipleri (Gemlik, Ayvalik...), birim ve yogunluk

CREATE TABLE production_product_types (
    id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(255) NOT NULL,
    type character varying(255),
    unit character varying(255) DEFAULT 'LITRE'::character varying NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    density_kg_per_litre numeric(6,4),
    CONSTRAINT production_product_types_unit_check CHECK (unit IN ('KG', 'LITRE'))
);

CREATE SEQUENCE production_product_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE production_product_types_id_seq OWNED BY production_product_types.id;

ALTER TABLE ONLY production_product_types ALTER COLUMN id SET DEFAULT nextval('production_product_types_id_seq'::regclass);

ALTER TABLE ONLY production_product_types
    ADD CONSTRAINT production_product_types_code_unique UNIQUE (code);

ALTER TABLE ONLY production_product_types
    ADD CONSTRAINT production_product_types_pkey PRIMARY KEY (id);

ALTER TABLE ONLY production_product_types
    ADD CONSTRAINT production_product_types_production_product_id_foreign FOREIGN KEY (production_product_id)
        REFERENCES production_products(id) ON DELETE RESTRICT;
