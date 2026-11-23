-- machine_run_values: makine adiminda girilen olcumler (tipin scope=run alanlari); veri tipine gore tek kolon dolar

CREATE TABLE machine_run_values (
    id bigint NOT NULL,
    machine_run_id bigint NOT NULL,
    machine_type_field_id bigint NOT NULL,
    value_decimal numeric(14,4),
    value_integer bigint,
    value_text text,
    value_boolean boolean,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE machine_run_values_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE machine_run_values_id_seq OWNED BY machine_run_values.id;

ALTER TABLE ONLY machine_run_values ALTER COLUMN id SET DEFAULT nextval('machine_run_values_id_seq'::regclass);

ALTER TABLE ONLY machine_run_values
    ADD CONSTRAINT machine_run_values_pkey PRIMARY KEY (id);

ALTER TABLE ONLY machine_run_values
    ADD CONSTRAINT machine_run_values_field_unique UNIQUE (machine_run_id, machine_type_field_id);

ALTER TABLE ONLY machine_run_values
    ADD CONSTRAINT machine_run_values_run_fk FOREIGN KEY (machine_run_id)
        REFERENCES machine_runs(id) ON DELETE CASCADE;

ALTER TABLE ONLY machine_run_values
    ADD CONSTRAINT machine_run_values_field_fk FOREIGN KEY (machine_type_field_id)
        REFERENCES machine_type_fields(id) ON DELETE RESTRICT;
