-- batch_process_field_values: asama isleminin alan degerleri

CREATE TABLE batch_process_field_values (
    id bigint NOT NULL,
    batch_process_id bigint NOT NULL,
    process_type_field_id bigint NOT NULL,
    value_text text,
    value_decimal numeric(15,4),
    value_integer bigint,
    value_boolean boolean,
    value_datetime timestamp(0) without time zone,
    value_json jsonb,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE batch_process_field_values_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE batch_process_field_values_id_seq OWNED BY batch_process_field_values.id;

ALTER TABLE ONLY batch_process_field_values ALTER COLUMN id SET DEFAULT nextval('batch_process_field_values_id_seq'::regclass);

ALTER TABLE ONLY batch_process_field_values
    ADD CONSTRAINT batch_process_field_values_batch_process_id_process_type_field_ UNIQUE (batch_process_id, process_type_field_id);

ALTER TABLE ONLY batch_process_field_values
    ADD CONSTRAINT batch_process_field_values_pkey PRIMARY KEY (id);

ALTER TABLE ONLY batch_process_field_values
    ADD CONSTRAINT batch_process_field_values_batch_process_id_foreign FOREIGN KEY (batch_process_id)
        REFERENCES batch_processes(id) ON DELETE CASCADE;

ALTER TABLE ONLY batch_process_field_values
    ADD CONSTRAINT batch_process_field_values_process_type_field_id_foreign FOREIGN KEY (process_type_field_id)
        REFERENCES process_type_fields(id) ON DELETE CASCADE;
