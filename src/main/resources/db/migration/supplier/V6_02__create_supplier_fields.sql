-- supplier_fields: tedarikcilerin tarlalari

CREATE TABLE supplier_fields (
    id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    code character varying(50) NOT NULL,
    location character varying(255),
    area_decare numeric(10,2),
    note text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE supplier_fields_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE supplier_fields_id_seq OWNED BY supplier_fields.id;

ALTER TABLE ONLY supplier_fields ALTER COLUMN id SET DEFAULT nextval('supplier_fields_id_seq'::regclass);

ALTER TABLE ONLY supplier_fields
    ADD CONSTRAINT supplier_fields_code_unique UNIQUE (code);

ALTER TABLE ONLY supplier_fields
    ADD CONSTRAINT supplier_fields_pkey PRIMARY KEY (id);

ALTER TABLE ONLY supplier_fields
    ADD CONSTRAINT supplier_fields_supplier_id_foreign FOREIGN KEY (supplier_id)
        REFERENCES suppliers(id) ON DELETE CASCADE;
