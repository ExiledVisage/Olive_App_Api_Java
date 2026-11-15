-- order_status_histories: siparis durum gecmisi

CREATE TABLE order_status_histories (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    from_status character varying(30),
    to_status character varying(30) NOT NULL,
    changed_by_user_id bigint,
    note text,
    changed_at timestamp(0) without time zone NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE order_status_histories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE order_status_histories_id_seq OWNED BY order_status_histories.id;

ALTER TABLE ONLY order_status_histories ALTER COLUMN id SET DEFAULT nextval('order_status_histories_id_seq'::regclass);

ALTER TABLE ONLY order_status_histories
    ADD CONSTRAINT order_status_histories_pkey PRIMARY KEY (id);

CREATE INDEX order_status_histories_order_id_changed_at_index ON order_status_histories
    USING btree (order_id, changed_at);

CREATE INDEX order_status_histories_to_status_index ON order_status_histories USING btree (to_status);

ALTER TABLE ONLY order_status_histories
    ADD CONSTRAINT order_status_histories_changed_by_user_id_foreign FOREIGN KEY (changed_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY order_status_histories
    ADD CONSTRAINT order_status_histories_order_id_foreign FOREIGN KEY (order_id)
        REFERENCES orders(id) ON DELETE CASCADE;
