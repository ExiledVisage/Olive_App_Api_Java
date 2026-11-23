-- machine_type_fields: makine tipinin alanlari; scope run: her calismada girilen olcum, machine: makine kaydinda
-- duran bilgi (kirici tipi gibi). Kullanilmis alan silinmez, pasife alinir

CREATE TABLE machine_type_fields (
    id bigint NOT NULL,
    machine_type_id bigint NOT NULL,
    field_key character varying(50) NOT NULL,
    label character varying(255) NOT NULL,
    data_type character varying(20) NOT NULL,
    scope character varying(10) DEFAULT 'run' NOT NULL,
    unit character varying(20),
    is_required boolean DEFAULT false NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    min_value numeric(14,4),
    max_value numeric(14,4),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT machine_type_fields_data_type_check CHECK (data_type IN ('decimal', 'integer', 'text', 'boolean')),
    CONSTRAINT machine_type_fields_scope_check CHECK (scope IN ('run', 'machine'))
);

CREATE SEQUENCE machine_type_fields_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE machine_type_fields_id_seq OWNED BY machine_type_fields.id;

ALTER TABLE ONLY machine_type_fields ALTER COLUMN id SET DEFAULT nextval('machine_type_fields_id_seq'::regclass);

ALTER TABLE ONLY machine_type_fields
    ADD CONSTRAINT machine_type_fields_pkey PRIMARY KEY (id);

ALTER TABLE ONLY machine_type_fields
    ADD CONSTRAINT machine_type_fields_key_unique UNIQUE (machine_type_id, field_key);

ALTER TABLE ONLY machine_type_fields
    ADD CONSTRAINT machine_type_fields_type_fk FOREIGN KEY (machine_type_id)
        REFERENCES machine_types(id) ON DELETE RESTRICT;
