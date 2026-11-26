-- integration_connections: site ve pazar yeri baglantilari (docs/INTEGRATIONS.md 0). Erisim bilgileri sunucu
-- anahtariyla sifreli (AES-GCM, credentials_encrypted); cevapta maskeli doner. Stok gonderimi baslangicta kapali.

CREATE TABLE integration_connections (
    id bigint NOT NULL,
    provider character varying(20) NOT NULL,
    name character varying(100) NOT NULL,
    base_url character varying(255),
    credentials_encrypted text NOT NULL,
    status character varying(10) DEFAULT 'active'::character varying NOT NULL,
    order_pull_enabled boolean DEFAULT true NOT NULL,
    stock_push_enabled boolean DEFAULT false NOT NULL,
    last_order_sync_at timestamp(0) without time zone,
    last_stock_sync_at timestamp(0) without time zone,
    last_error text,
    last_error_at timestamp(0) without time zone,
    created_by_user_id bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT integration_connections_provider_check
        CHECK (provider IN ('woocommerce', 'trendyol', 'hepsiburada')),
    CONSTRAINT integration_connections_status_check CHECK (status IN ('active', 'paused'))
);

CREATE SEQUENCE integration_connections_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE integration_connections_id_seq OWNED BY integration_connections.id;

ALTER TABLE ONLY integration_connections
    ALTER COLUMN id SET DEFAULT nextval('integration_connections_id_seq'::regclass);

ALTER TABLE ONLY integration_connections
    ADD CONSTRAINT integration_connections_pkey PRIMARY KEY (id);

ALTER TABLE ONLY integration_connections
    ADD CONSTRAINT integration_connections_name_unique UNIQUE (name);

ALTER TABLE ONLY integration_connections
    ADD CONSTRAINT integration_connections_user_fk FOREIGN KEY (created_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;
