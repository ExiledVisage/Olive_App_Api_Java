-- integration_external_refs: bizdeki kaydin muhasebe programindaki karsiligi (docs/INVOICES.md): kind 'contact'
-- (local_id musteri) ya da 'product' (local_id bitmis urun). Ilk faturada bulunur ya da acilir, sonra yeniden
-- kullanilir.

CREATE TABLE integration_external_refs (
    id bigint NOT NULL,
    connection_id bigint NOT NULL,
    kind character varying(10) NOT NULL,
    local_id bigint NOT NULL,
    external_id character varying(64) NOT NULL,
    created_at timestamp(0) without time zone,
    CONSTRAINT integration_external_refs_kind_check CHECK (kind IN ('contact', 'product'))
);

CREATE SEQUENCE integration_external_refs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE integration_external_refs_id_seq OWNED BY integration_external_refs.id;

ALTER TABLE ONLY integration_external_refs
    ALTER COLUMN id SET DEFAULT nextval('integration_external_refs_id_seq'::regclass);

ALTER TABLE ONLY integration_external_refs
    ADD CONSTRAINT integration_external_refs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY integration_external_refs
    ADD CONSTRAINT integration_external_refs_unique UNIQUE (connection_id, kind, local_id);

ALTER TABLE ONLY integration_external_refs
    ADD CONSTRAINT integration_external_refs_connection_fk FOREIGN KEY (connection_id)
        REFERENCES integration_connections(id) ON DELETE CASCADE;
