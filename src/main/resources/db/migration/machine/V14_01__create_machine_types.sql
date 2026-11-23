-- machine_types: makine tipleri (Kirici, Malaksor...): islev ve tipe ozel olcum alanlari

CREATE TABLE machine_types (
    id bigint NOT NULL,
    key character varying(50) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    sort_order integer DEFAULT 1 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE machine_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE machine_types_id_seq OWNED BY machine_types.id;

ALTER TABLE ONLY machine_types ALTER COLUMN id SET DEFAULT nextval('machine_types_id_seq'::regclass);

ALTER TABLE ONLY machine_types
    ADD CONSTRAINT machine_types_pkey PRIMARY KEY (id);

ALTER TABLE ONLY machine_types
    ADD CONSTRAINT machine_types_key_unique UNIQUE (key);
