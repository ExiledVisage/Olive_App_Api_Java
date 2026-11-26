-- integration_product_links: platformdaki urun (ya da varyant) ile bizim bitmis urunumuzun eslestirmesi
-- (docs/INTEGRATIONS.md 0). Katalog yenilenince platformdaki her urun bir satir; barkod, yoksa SKU tutarsa
-- kendiliginden eslesir (match_source barcode/sku), elle baglanan "manual", bilerek eslestirilmeyen "ignored"
-- (kendiliginden eslestirmeye girmez). pushed_stock platforma en son gonderilen satilabilir adet: satilabilir adet
-- bundan farkliysa stok gonderilir; gonderim basarisizsa degismez ve sonraki calismada yeniden denenir.

CREATE TABLE integration_product_links (
    id bigint NOT NULL,
    connection_id bigint NOT NULL,
    external_product_id character varying(64) NOT NULL,
    external_variant_id character varying(64) DEFAULT ''::character varying NOT NULL,
    external_name character varying(255) NOT NULL,
    external_sku character varying(100),
    external_barcode character varying(64),
    external_stock integer,
    finished_product_id bigint,
    match_source character varying(10),
    pushed_stock integer,
    pushed_at timestamp(0) without time zone,
    push_error text,
    last_seen_at timestamp(0) without time zone NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT integration_product_links_source_check
        CHECK (match_source IN ('barcode', 'sku', 'manual', 'ignored'))
);

CREATE SEQUENCE integration_product_links_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE integration_product_links_id_seq OWNED BY integration_product_links.id;

ALTER TABLE ONLY integration_product_links
    ALTER COLUMN id SET DEFAULT nextval('integration_product_links_id_seq'::regclass);

ALTER TABLE ONLY integration_product_links
    ADD CONSTRAINT integration_product_links_pkey PRIMARY KEY (id);

ALTER TABLE ONLY integration_product_links
    ADD CONSTRAINT integration_product_links_external_unique
        UNIQUE (connection_id, external_product_id, external_variant_id);

ALTER TABLE ONLY integration_product_links
    ADD CONSTRAINT integration_product_links_connection_fk FOREIGN KEY (connection_id)
        REFERENCES integration_connections(id) ON DELETE CASCADE;

ALTER TABLE ONLY integration_product_links
    ADD CONSTRAINT integration_product_links_product_fk FOREIGN KEY (finished_product_id)
        REFERENCES finished_products(id) ON DELETE SET NULL;

CREATE INDEX integration_product_links_product_index ON integration_product_links
    USING btree (finished_product_id);
