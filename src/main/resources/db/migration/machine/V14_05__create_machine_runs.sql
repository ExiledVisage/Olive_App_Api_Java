-- machine_runs: partinin bir makine adimi (Sikim icinde Kirici, Malaksor...): hangi parcada, ne zaman. Asama geri alinirsa silinir

CREATE TABLE machine_runs (
    id bigint NOT NULL,
    batch_process_id bigint NOT NULL,
    step_order integer NOT NULL,
    machine_type_id bigint NOT NULL,
    machine_part_id bigint NOT NULL,
    started_at timestamp(0) without time zone NOT NULL,
    finished_at timestamp(0) without time zone,
    note text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE machine_runs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE machine_runs_id_seq OWNED BY machine_runs.id;

ALTER TABLE ONLY machine_runs ALTER COLUMN id SET DEFAULT nextval('machine_runs_id_seq'::regclass);

ALTER TABLE ONLY machine_runs
    ADD CONSTRAINT machine_runs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY machine_runs
    ADD CONSTRAINT machine_runs_step_unique UNIQUE (batch_process_id, step_order);

ALTER TABLE ONLY machine_runs
    ADD CONSTRAINT machine_runs_batch_process_fk FOREIGN KEY (batch_process_id)
        REFERENCES batch_processes(id) ON DELETE CASCADE;

ALTER TABLE ONLY machine_runs
    ADD CONSTRAINT machine_runs_type_fk FOREIGN KEY (machine_type_id)
        REFERENCES machine_types(id) ON DELETE RESTRICT;

ALTER TABLE ONLY machine_runs
    ADD CONSTRAINT machine_runs_part_fk FOREIGN KEY (machine_part_id)
        REFERENCES machine_parts(id) ON DELETE RESTRICT;

CREATE INDEX machine_runs_part_started_index ON machine_runs (machine_part_id, started_at);
