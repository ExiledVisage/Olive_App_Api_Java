-- finished_product_storage_slots: depo raflari

CREATE TABLE finished_product_storage_slots (
    id bigint NOT NULL,
    finished_product_storage_id bigint NOT NULL,
    name character varying(50),
    volume_litre numeric(10,4) NOT NULL,
    max_quantity integer NOT NULL,
    current_quantity integer DEFAULT 0 NOT NULL,
    finished_product_id bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE finished_product_storage_slots_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE finished_product_storage_slots_id_seq OWNED BY finished_product_storage_slots.id;

ALTER TABLE ONLY finished_product_storage_slots ALTER COLUMN id SET DEFAULT nextval('finished_product_storage_slots_id_seq'::regclass);

ALTER TABLE ONLY finished_product_storage_slots
    ADD CONSTRAINT finished_product_storage_slots_pkey PRIMARY KEY (id);

CREATE INDEX finished_product_storage_slots_finished_product_storage_id_volu ON finished_product_storage_slots
    USING btree (finished_product_storage_id, volume_litre);

ALTER TABLE ONLY finished_product_storage_slots
    ADD CONSTRAINT finished_product_storage_slots_finished_product_id_foreign FOREIGN KEY (finished_product_id)
        REFERENCES finished_products(id) ON DELETE SET NULL;

ALTER TABLE ONLY finished_product_storage_slots
    ADD CONSTRAINT finished_product_storage_slots_finished_product_storage_id_fore FOREIGN KEY (finished_product_storage_id)
        REFERENCES finished_product_storages(id) ON DELETE CASCADE;
