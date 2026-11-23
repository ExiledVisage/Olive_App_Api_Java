-- machines: fabrikadaki makineler; her tipten birden fazla olabilir. attributes: tipin scope=machine alanlari
-- (field_key -> deger)

CREATE TABLE machines (
    id bigint NOT NULL,
    machine_type_id bigint NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(255) NOT NULL,
    brand character varying(255),
    model character varying(255),
    capacity_kg_per_hour numeric(12,2),
    note text,
    attributes jsonb DEFAULT '{}'::jsonb NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE machines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE machines_id_seq OWNED BY machines.id;

ALTER TABLE ONLY machines ALTER COLUMN id SET DEFAULT nextval('machines_id_seq'::regclass);

ALTER TABLE ONLY machines
    ADD CONSTRAINT machines_pkey PRIMARY KEY (id);

ALTER TABLE ONLY machines
    ADD CONSTRAINT machines_code_unique UNIQUE (code);

ALTER TABLE ONLY machines
    ADD CONSTRAINT machines_type_fk FOREIGN KEY (machine_type_id)
        REFERENCES machine_types(id) ON DELETE RESTRICT;
