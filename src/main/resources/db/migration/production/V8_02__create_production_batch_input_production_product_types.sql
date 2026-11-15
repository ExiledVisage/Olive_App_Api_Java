-- production_batch_input_production_product_types: partinin girdi urun tipleri

CREATE TABLE production_batch_input_production_product_types (
    id bigint NOT NULL,
    production_batch_id bigint NOT NULL,
    production_product_type_id bigint NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE production_batch_input_production_product_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE production_batch_input_production_product_types_id_seq OWNED BY production_batch_input_production_product_types.id;

ALTER TABLE ONLY production_batch_input_production_product_types ALTER COLUMN id SET DEFAULT nextval('production_batch_input_production_product_types_id_seq'::regclass);

ALTER TABLE ONLY production_batch_input_production_product_types
    ADD CONSTRAINT production_batch_input_production_product_types_pkey PRIMARY KEY (id);

CREATE INDEX prod_batch_input_batch_idx ON production_batch_input_production_product_types
    USING btree (production_batch_id);

CREATE INDEX prod_batch_input_type_idx ON production_batch_input_production_product_types
    USING btree (production_product_type_id);

ALTER TABLE ONLY production_batch_input_production_product_types
    ADD CONSTRAINT prod_batch_input_batch_fk FOREIGN KEY (production_batch_id)
        REFERENCES production_batches(id) ON DELETE CASCADE;

ALTER TABLE ONLY production_batch_input_production_product_types
    ADD CONSTRAINT prod_batch_input_type_fk FOREIGN KEY (production_product_type_id)
        REFERENCES production_product_types(id) ON DELETE RESTRICT;
