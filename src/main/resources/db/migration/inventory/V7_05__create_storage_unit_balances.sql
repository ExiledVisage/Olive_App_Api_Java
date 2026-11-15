-- storage_unit_balances: tank stok toplamlari (tank, urun, birim)

CREATE TABLE storage_unit_balances (
    id bigint NOT NULL,
    storage_unit_id bigint NOT NULL,
    production_product_id bigint NOT NULL,
    current_quantity numeric(14,2) DEFAULT '0'::numeric NOT NULL,
    unit character varying(255) NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT storage_unit_balances_unit_check CHECK (unit IN ('KG', 'LITRE'))
);

CREATE SEQUENCE storage_unit_balances_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE storage_unit_balances_id_seq OWNED BY storage_unit_balances.id;

ALTER TABLE ONLY storage_unit_balances ALTER COLUMN id SET DEFAULT nextval('storage_unit_balances_id_seq'::regclass);

ALTER TABLE ONLY storage_unit_balances
    ADD CONSTRAINT storage_unit_balances_pkey PRIMARY KEY (id);

ALTER TABLE ONLY storage_unit_balances
    ADD CONSTRAINT storage_unit_balances_storage_unit_id_production_product_id_uni UNIQUE (storage_unit_id, production_product_id, unit);

ALTER TABLE ONLY storage_unit_balances
    ADD CONSTRAINT storage_unit_balances_production_product_id_foreign FOREIGN KEY (production_product_id)
        REFERENCES production_products(id) ON DELETE RESTRICT;

ALTER TABLE ONLY storage_unit_balances
    ADD CONSTRAINT storage_unit_balances_storage_unit_id_foreign FOREIGN KEY (storage_unit_id)
        REFERENCES storage_units(id) ON DELETE CASCADE;
