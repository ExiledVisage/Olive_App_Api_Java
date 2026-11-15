-- batch_processes: partinin asama islemleri ve durumlari

CREATE TABLE batch_processes (
    id bigint NOT NULL,
    production_batch_id bigint NOT NULL,
    production_stage_id bigint NOT NULL,
    status character varying(255) DEFAULT 'waiting'::character varying NOT NULL,
    operator_id bigint,
    started_at timestamp(0) without time zone,
    completed_at timestamp(0) without time zone,
    note text,
    operation_data jsonb,
    submitted_by_user_id bigint,
    submitted_at timestamp(0) without time zone,
    approved_by_user_id bigint,
    approved_at timestamp(0) without time zone,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE batch_processes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE batch_processes_id_seq OWNED BY batch_processes.id;

ALTER TABLE ONLY batch_processes ALTER COLUMN id SET DEFAULT nextval('batch_processes_id_seq'::regclass);

ALTER TABLE ONLY batch_processes
    ADD CONSTRAINT batch_processes_pkey PRIMARY KEY (id);

ALTER TABLE ONLY batch_processes
    ADD CONSTRAINT batch_processes_production_batch_id_production_stage_id_unique UNIQUE (production_batch_id, production_stage_id);

CREATE INDEX batch_processes_operator_id_index ON batch_processes USING btree (operator_id);

CREATE INDEX batch_processes_production_batch_id_status_index ON batch_processes
    USING btree (production_batch_id, status);

ALTER TABLE ONLY batch_processes
    ADD CONSTRAINT batch_processes_approved_by_user_id_foreign FOREIGN KEY (approved_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY batch_processes
    ADD CONSTRAINT batch_processes_operator_id_foreign FOREIGN KEY (operator_id)
        REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY batch_processes
    ADD CONSTRAINT batch_processes_production_batch_id_foreign FOREIGN KEY (production_batch_id)
        REFERENCES production_batches(id) ON DELETE CASCADE;

ALTER TABLE ONLY batch_processes
    ADD CONSTRAINT batch_processes_production_stage_id_foreign FOREIGN KEY (production_stage_id)
        REFERENCES production_stages(id) ON DELETE RESTRICT;

ALTER TABLE ONLY batch_processes
    ADD CONSTRAINT batch_processes_submitted_by_user_id_foreign FOREIGN KEY (submitted_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

-- Bu tablodan once olusan tablolarin (materials, storage_movements) bu tabloya foreign key'leri

ALTER TABLE ONLY materials
    ADD CONSTRAINT materials_closed_by_batch_process_id_foreign FOREIGN KEY (closed_by_batch_process_id)
        REFERENCES batch_processes(id) ON DELETE SET NULL;

ALTER TABLE ONLY materials
    ADD CONSTRAINT materials_created_by_batch_process_id_foreign FOREIGN KEY (created_by_batch_process_id)
        REFERENCES batch_processes(id) ON DELETE SET NULL;

ALTER TABLE ONLY storage_movements
    ADD CONSTRAINT storage_movements_batch_process_id_foreign FOREIGN KEY (batch_process_id)
        REFERENCES batch_processes(id) ON DELETE SET NULL;
