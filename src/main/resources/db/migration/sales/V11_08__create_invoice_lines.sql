-- invoice_lines: fatura kalemleri (docs/INVOICES.md). unit_price KDV dahil birim fiyat; gross_amount = unit_price x
-- quantity, net_amount = gross_amount / (1 + vat_rate / 100) kurusa yuvarlanmis, vat_amount = gross - net.
-- description kesildigi andaki urun adi.

CREATE TABLE invoice_lines (
    id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    finished_product_id bigint NOT NULL,
    description character varying(255) NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(12,2) NOT NULL,
    vat_rate integer NOT NULL,
    net_amount numeric(12,2) NOT NULL,
    vat_amount numeric(12,2) NOT NULL,
    gross_amount numeric(12,2) NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT invoice_lines_vat_rate_check CHECK (vat_rate IN (0, 1, 10, 20))
);

CREATE SEQUENCE invoice_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE invoice_lines_id_seq OWNED BY invoice_lines.id;

ALTER TABLE ONLY invoice_lines ALTER COLUMN id SET DEFAULT nextval('invoice_lines_id_seq'::regclass);

ALTER TABLE ONLY invoice_lines
    ADD CONSTRAINT invoice_lines_pkey PRIMARY KEY (id);

CREATE INDEX invoice_lines_invoice_id_index ON invoice_lines USING btree (invoice_id);

ALTER TABLE ONLY invoice_lines
    ADD CONSTRAINT invoice_lines_invoice_id_foreign FOREIGN KEY (invoice_id)
        REFERENCES invoices(id) ON DELETE CASCADE;

ALTER TABLE ONLY invoice_lines
    ADD CONSTRAINT invoice_lines_finished_product_id_foreign FOREIGN KEY (finished_product_id)
        REFERENCES finished_products(id) ON DELETE RESTRICT;
