-- production_batches: uretim partileri; alim fiyati ve kanali yalnizca teslimatta (fiyat KDV haric, oran ayri)

CREATE TABLE production_batches (
    id bigint NOT NULL,
    batch_code character varying(255) NOT NULL,
    parent_batch_id bigint,
    production_line_id bigint NOT NULL,
    created_by_user_id bigint,
    status character varying(255) DEFAULT 'pending'::character varying NOT NULL,
    quantity_kg numeric(14,4),
    truck_plate character varying(255),
    weighing_slip_no character varying(255),
    received_at timestamp(0) without time zone,
    started_at timestamp(0) without time zone,
    completed_at timestamp(0) without time zone,
    note text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    supplier_id bigint,
    supplier_field_id bigint,
    supply_channel character varying(20),
    price_per_kg numeric(12,4),
    vat_rate numeric(5,2),
    transport_cost numeric(14,2),
    price_note text,
    CONSTRAINT production_batches_supply_channel_check CHECK (supply_channel IN ('farmer', 'trader', 'own_grove'))
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

CREATE INDEX production_batches_parent_batch_id_index ON production_batches USING btree (parent_batch_id);

CREATE INDEX production_batches_production_line_id_status_index ON production_batches
    USING btree (production_line_id, status);

ALTER TABLE ONLY production_batches
    ADD CONSTRAINT production_batches_created_by_user_id_foreign FOREIGN KEY (created_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

-- Grubun teslimati (ana parti); bos ise satir teslimattir
ALTER TABLE ONLY production_batches
    ADD CONSTRAINT production_batches_parent_batch_id_foreign FOREIGN KEY (parent_batch_id)
        REFERENCES production_batches(id) ON DELETE RESTRICT;

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
