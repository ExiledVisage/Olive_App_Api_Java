-- finished_product_storages: bitmis urun depolari

CREATE TABLE finished_product_storages (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE finished_product_storages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_product_storages_id_seq OWNED BY finished_product_storages.id;

ALTER TABLE ONLY finished_product_storages ALTER COLUMN id SET DEFAULT nextval('finished_product_storages_id_seq'::regclass);

ALTER TABLE ONLY finished_product_storages
    ADD CONSTRAINT finished_product_storages_pkey PRIMARY KEY (id);
