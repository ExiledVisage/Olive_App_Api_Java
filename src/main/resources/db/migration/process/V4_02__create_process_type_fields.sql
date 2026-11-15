-- process_type_fields: proses tipinin alanlari

CREATE TABLE process_type_fields (
    id bigint NOT NULL,
    process_type_id bigint NOT NULL,
    field_key character varying(255) NOT NULL,
    label character varying(255) NOT NULL,
    data_type character varying(255) NOT NULL,
    is_required boolean DEFAULT false NOT NULL,
    unit character varying(255),
    sort_order integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    options jsonb,
    default_value text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE process_type_fields_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE process_type_fields_id_seq OWNED BY process_type_fields.id;

ALTER TABLE ONLY process_type_fields ALTER COLUMN id SET DEFAULT nextval('process_type_fields_id_seq'::regclass);

ALTER TABLE ONLY process_type_fields
    ADD CONSTRAINT process_type_fields_pkey PRIMARY KEY (id);

ALTER TABLE ONLY process_type_fields
    ADD CONSTRAINT process_type_fields_process_type_id_field_key_unique UNIQUE (process_type_id, field_key);

ALTER TABLE ONLY process_type_fields
    ADD CONSTRAINT process_type_fields_process_type_id_foreign FOREIGN KEY (process_type_id)
        REFERENCES process_types(id) ON DELETE CASCADE;
