-- attribute_definitions: attribute (ozellik) tanimlari: anahtar, veri tipi, birim

CREATE TABLE attribute_definitions (
    id bigint NOT NULL,
    attribute_key character varying(255) NOT NULL,
    label character varying(255) NOT NULL,
    data_type character varying(255) NOT NULL,
    unit character varying(255),
    description text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE attribute_definitions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE attribute_definitions_id_seq OWNED BY attribute_definitions.id;

ALTER TABLE ONLY attribute_definitions ALTER COLUMN id SET DEFAULT nextval('attribute_definitions_id_seq'::regclass);

ALTER TABLE ONLY attribute_definitions
    ADD CONSTRAINT attribute_definitions_attribute_key_unique UNIQUE (attribute_key);

ALTER TABLE ONLY attribute_definitions
    ADD CONSTRAINT attribute_definitions_pkey PRIMARY KEY (id);
