-- packaging_lot_movements: ambalaj lot hareketleri

CREATE TABLE packaging_lot_movements (
    id bigint NOT NULL,
    packaging_lot_id bigint NOT NULL,
    packaging_item_id bigint NOT NULL,
    finished_product_run_id bigint,
    user_id bigint,
    source_type character varying(50) NOT NULL,
    movement_type character varying(50) NOT NULL,
    quantity integer NOT NULL,
    note text,
    moved_at timestamp(0) without time zone NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    scrap_reason character varying(20),
    CONSTRAINT packaging_lot_movements_scrap_reason_check CHECK (scrap_reason IN ('broken', 'defective', 'other'))
);

CREATE SEQUENCE packaging_lot_movements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE packaging_lot_movements_id_seq OWNED BY packaging_lot_movements.id;

ALTER TABLE ONLY packaging_lot_movements ALTER COLUMN id SET DEFAULT nextval('packaging_lot_movements_id_seq'::regclass);

ALTER TABLE ONLY packaging_lot_movements
    ADD CONSTRAINT packaging_lot_movements_pkey PRIMARY KEY (id);

CREATE INDEX pkg_lot_mov_item_idx ON packaging_lot_movements USING btree (packaging_item_id);

CREATE INDEX pkg_lot_mov_lot_time_idx ON packaging_lot_movements USING btree (packaging_lot_id, moved_at);

CREATE INDEX pkg_lot_mov_run_idx ON packaging_lot_movements USING btree (finished_product_run_id);

CREATE INDEX pkg_lot_mov_type_idx ON packaging_lot_movements USING btree (movement_type);

ALTER TABLE ONLY packaging_lot_movements
    ADD CONSTRAINT packaging_lot_movements_packaging_item_id_foreign FOREIGN KEY (packaging_item_id)
        REFERENCES packaging_items(id) ON DELETE RESTRICT;

ALTER TABLE ONLY packaging_lot_movements
    ADD CONSTRAINT packaging_lot_movements_packaging_lot_id_foreign FOREIGN KEY (packaging_lot_id)
        REFERENCES packaging_lots(id) ON DELETE RESTRICT;

ALTER TABLE ONLY packaging_lot_movements
    ADD CONSTRAINT packaging_lot_movements_user_id_foreign FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE SET NULL;
