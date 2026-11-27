-- invoices: siparisten kesilen satis faturasi (docs/INVOICES.md). Numara yil icinde sirali: onek + yil + 9 hane
-- (OLV2026000000001). Alici bilgileri kesildigi andaki haliyle saklanir; musteri sonradan degisse de fatura degismez.
-- Tutarlar KDV dahil kalem tutarlarindan hesaplanir (gross_total musterinin odedigi). Siparis basina tek gecerli
-- (issued) fatura; iptal edilen (cancelled) fatura numarasiyla kalir, yerine yenisi kesilebilir.

CREATE TABLE invoices (
    id bigint NOT NULL,
    order_id bigint NOT NULL,
    customer_id bigint NOT NULL,
    number character varying(16) NOT NULL,
    year smallint NOT NULL,
    sequence integer NOT NULL,
    status character varying(10) DEFAULT 'issued'::character varying NOT NULL,
    issue_date date NOT NULL,
    buyer_name character varying(255) NOT NULL,
    buyer_tax_number character varying(11),
    buyer_tax_office character varying(100),
    buyer_address text,
    buyer_district character varying(255),
    buyer_city character varying(255),
    buyer_country character varying(255),
    buyer_email character varying(255),
    buyer_phone character varying(255),
    currency character varying(3) DEFAULT 'TRY'::character varying NOT NULL,
    net_total numeric(12,2) NOT NULL,
    vat_total numeric(12,2) NOT NULL,
    gross_total numeric(12,2) NOT NULL,
    note text,
    created_by_user_id bigint,
    cancelled_at timestamp(0) without time zone,
    cancelled_by_user_id bigint,
    cancel_reason text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT invoices_status_check CHECK (status IN ('issued', 'cancelled'))
);

CREATE SEQUENCE invoices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE invoices_id_seq OWNED BY invoices.id;

ALTER TABLE ONLY invoices ALTER COLUMN id SET DEFAULT nextval('invoices_id_seq'::regclass);

ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);

ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_number_unique UNIQUE (number);

ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_year_sequence_unique UNIQUE (year, sequence);

CREATE UNIQUE INDEX invoices_order_issued_unique ON invoices USING btree (order_id) WHERE status = 'issued';

CREATE INDEX invoices_customer_id_index ON invoices USING btree (customer_id);

CREATE INDEX invoices_issue_date_index ON invoices USING btree (issue_date);

ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_order_id_foreign FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE RESTRICT;

ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_customer_id_foreign FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT;

ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_created_by_user_id_foreign FOREIGN KEY (created_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY invoices
    ADD CONSTRAINT invoices_cancelled_by_user_id_foreign FOREIGN KEY (cancelled_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;
