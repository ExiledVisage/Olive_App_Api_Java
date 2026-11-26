-- integration_sync_runs: senkron gecmisi (docs/INTEGRATIONS.md 0). Katalog yenileme, siparis cekme ve stok
-- gonderiminin her calismasi: ne zaman, basarili mi, ozet ve hata. Entegrasyonlar ekranindaki gecmis buradan.

CREATE TABLE integration_sync_runs (
    id bigint NOT NULL,
    connection_id bigint NOT NULL,
    kind character varying(10) NOT NULL,
    triggered_by character varying(10) NOT NULL,
    started_at timestamp(0) without time zone NOT NULL,
    finished_at timestamp(0) without time zone,
    ok boolean,
    summary text,
    error text,
    CONSTRAINT integration_sync_runs_kind_check CHECK (kind IN ('catalog', 'orders', 'stock')),
    CONSTRAINT integration_sync_runs_trigger_check CHECK (triggered_by IN ('manual', 'schedule'))
);

CREATE SEQUENCE integration_sync_runs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE integration_sync_runs_id_seq OWNED BY integration_sync_runs.id;

ALTER TABLE ONLY integration_sync_runs
    ALTER COLUMN id SET DEFAULT nextval('integration_sync_runs_id_seq'::regclass);

ALTER TABLE ONLY integration_sync_runs
    ADD CONSTRAINT integration_sync_runs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY integration_sync_runs
    ADD CONSTRAINT integration_sync_runs_connection_fk FOREIGN KEY (connection_id)
        REFERENCES integration_connections(id) ON DELETE CASCADE;

CREATE INDEX integration_sync_runs_connection_index ON integration_sync_runs
    USING btree (connection_id, started_at DESC);
