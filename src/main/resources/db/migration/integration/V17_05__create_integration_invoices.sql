-- integration_invoices: satis faturasinin muhasebe programina (Parasut) gonderimi (docs/INVOICES.md). Adimlar:
-- cari ve urunler bulunur ya da acilir, satis faturasi olusur (sent, external_id), resmilestirme acik ise e-fatura
-- ya da e-arsiv isi baslar (formalizing, job_id) ve bitince formalized (official_number). Hata olursa failed ve
-- gerekce (error); yeniden deneme kaldigi adimdan devam eder (olusmus fatura yeniden olusturulmaz). Bizde iptal
-- edilen fatura Parasut'te de iptal edilir (cancelled); resmilesmis fatura Parasut'ten iptal edilmelidir.

CREATE TABLE integration_invoices (
    id bigint NOT NULL,
    connection_id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    status character varying(12) NOT NULL,
    external_id character varying(64),
    e_document_type character varying(10),
    job_id character varying(64),
    official_number character varying(32),
    error text,
    attempted_at timestamp(0) without time zone,
    sent_at timestamp(0) without time zone,
    formalized_at timestamp(0) without time zone,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT integration_invoices_status_check
        CHECK (status IN ('pending', 'sent', 'formalizing', 'formalized', 'failed', 'cancelled')),
    CONSTRAINT integration_invoices_e_document_type_check CHECK (e_document_type IN ('e_invoice', 'e_archive'))
);

CREATE SEQUENCE integration_invoices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE integration_invoices_id_seq OWNED BY integration_invoices.id;

ALTER TABLE ONLY integration_invoices ALTER COLUMN id SET DEFAULT nextval('integration_invoices_id_seq'::regclass);

ALTER TABLE ONLY integration_invoices
    ADD CONSTRAINT integration_invoices_pkey PRIMARY KEY (id);

ALTER TABLE ONLY integration_invoices
    ADD CONSTRAINT integration_invoices_unique UNIQUE (connection_id, invoice_id);

CREATE INDEX integration_invoices_invoice_id_index ON integration_invoices USING btree (invoice_id);

ALTER TABLE ONLY integration_invoices
    ADD CONSTRAINT integration_invoices_connection_fk FOREIGN KEY (connection_id)
        REFERENCES integration_connections(id) ON DELETE CASCADE;

ALTER TABLE ONLY integration_invoices
    ADD CONSTRAINT integration_invoices_invoice_fk FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE;
