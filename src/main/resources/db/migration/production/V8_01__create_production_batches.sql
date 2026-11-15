-- production_batches: uretim partileri

CREATE TABLE production_batches (
    id bigint NOT NULL,
    batch_code character varying(255) NOT NULL,
    production_line_id bigint NOT NULL,
    created_by_user_id bigint,
    status character varying(255) DEFAULT 'pending'::character varying NOT NULL,
    quantity_kg numeric(14,4),
    received_at timestamp(0) without time zone,
    started_at timestamp(0) without time zone,
    completed_at timestamp(0) without time zone,
    note text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    supplier_id bigint,
    supplier_field_id bigint
);

CREATE SEQUENCE production_batches_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE production_batches_id_seq OWNED BY production_batches.id;

ALTER TABLE ONLY production_batches ALTER COLUMN id SET DEFAULT nextval('production_batches_id_seq'::regclass);

ALTER TABLE ONLY production_batches
    ADD CONSTRAINT production_batches_batch_code_unique UNIQUE (batch_code);

ALTER TABLE ONLY production_batches
    ADD CONSTRAINT production_batches_pkey PRIMARY KEY (id);

CREATE INDEX production_batches_created_by_user_id_index ON production_batches USING btree (created_by_user_id);

CREATE INDEX production_batches_production_line_id_status_index ON production_batches
    USING btree (production_line_id, status);

ALTER TABLE ONLY production_batches
    ADD CONSTRAINT production_batches_created_by_user_id_foreign FOREIGN KEY (created_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY production_batches
    ADD CONSTRAINT production_batches_production_line_id_foreign FOREIGN KEY (production_line_id)
        REFERENCES production_lines(id) ON DELETE RESTRICT;

ALTER TABLE ONLY production_batches
    ADD CONSTRAINT production_batches_supplier_field_id_foreign FOREIGN KEY (supplier_field_id)
        REFERENCES supplier_fields(id) ON DELETE SET NULL;

ALTER TABLE ONLY production_batches
    ADD CONSTRAINT production_batches_supplier_id_foreign FOREIGN KEY (supplier_id)
        REFERENCES suppliers(id) ON DELETE SET NULL;

-- Bu tablodan once olusan tablolarin (materials) bu tabloya foreign key'leri

ALTER TABLE ONLY materials
    ADD CONSTRAINT materials_production_batch_id_foreign FOREIGN KEY (production_batch_id)
        REFERENCES production_batches(id) ON DELETE SET NULL;
