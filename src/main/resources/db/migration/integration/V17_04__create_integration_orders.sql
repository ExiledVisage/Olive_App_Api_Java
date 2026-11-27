-- integration_orders: platformdan gelen her siparis bir kez (docs/INTEGRATIONS.md 0). Kalemleri eslesirse siparis
-- moduline taslak olarak alinir (imported, order_id); eslesmeyen kalem varsa siparise girmeden bekler (pending,
-- problem) ve urun eslestirilince yeniden denenir. payload platformdan gelen siparis (yeniden deneme icin).
-- platform_status: pazar yerine bildirilen son durum (preparing = "hazirlaniyor", invoiced = faturalandi ve fatura
-- numarasi, invoice_sent = fatura dosyasi da yuklendi; docs/INVOICES.md); bildirim reddedilirse platform_error
-- yazilir, zamanlayici daha sonra yeniden dener (platform_attempted_at).

CREATE TABLE integration_orders (
    id bigint NOT NULL,
    connection_id bigint NOT NULL,
    external_id character varying(64) NOT NULL,
    external_number character varying(64),
    status character varying(10) NOT NULL,
    order_id bigint,
    problem text,
    payload jsonb NOT NULL,
    received_at timestamp(0) without time zone NOT NULL,
    imported_at timestamp(0) without time zone,
    platform_status character varying(20),
    platform_error text,
    platform_attempted_at timestamp(0) without time zone,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT integration_orders_status_check CHECK (status IN ('imported', 'pending')),
    CONSTRAINT integration_orders_platform_status_check
        CHECK (platform_status IN ('preparing', 'invoiced', 'invoice_sent'))
);

CREATE SEQUENCE integration_orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE integration_orders_id_seq OWNED BY integration_orders.id;

ALTER TABLE ONLY integration_orders ALTER COLUMN id SET DEFAULT nextval('integration_orders_id_seq'::regclass);

ALTER TABLE ONLY integration_orders
    ADD CONSTRAINT integration_orders_pkey PRIMARY KEY (id);

ALTER TABLE ONLY integration_orders
    ADD CONSTRAINT integration_orders_external_unique UNIQUE (connection_id, external_id);

ALTER TABLE ONLY integration_orders
    ADD CONSTRAINT integration_orders_connection_fk FOREIGN KEY (connection_id)
        REFERENCES integration_connections(id) ON DELETE CASCADE;

ALTER TABLE ONLY integration_orders
    ADD CONSTRAINT integration_orders_order_fk FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;
