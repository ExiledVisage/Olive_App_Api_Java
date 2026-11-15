-- process_types: proses tipleri

CREATE TABLE process_types (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    category character varying(255) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE process_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE process_types_id_seq OWNED BY process_types.id;

ALTER TABLE ONLY process_types ALTER COLUMN id SET DEFAULT nextval('process_types_id_seq'::regclass);

ALTER TABLE ONLY process_types
    ADD CONSTRAINT process_types_pkey PRIMARY KEY (id);
