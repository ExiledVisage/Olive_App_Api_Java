-- machine_parts: makinenin bagimsiz calisan birimleri (malaksor haznesi gibi); her makinede en az bir tane

CREATE TABLE machine_parts (
    id bigint NOT NULL,
    machine_id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(255) NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE machine_parts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE machine_parts_id_seq OWNED BY machine_parts.id;

ALTER TABLE ONLY machine_parts ALTER COLUMN id SET DEFAULT nextval('machine_parts_id_seq'::regclass);

ALTER TABLE ONLY machine_parts
    ADD CONSTRAINT machine_parts_pkey PRIMARY KEY (id);

ALTER TABLE ONLY machine_parts
    ADD CONSTRAINT machine_parts_code_unique UNIQUE (machine_id, code);

ALTER TABLE ONLY machine_parts
    ADD CONSTRAINT machine_parts_machine_fk FOREIGN KEY (machine_id)
        REFERENCES machines(id) ON DELETE CASCADE;
