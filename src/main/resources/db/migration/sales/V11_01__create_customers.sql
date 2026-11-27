-- customers: musteriler; tax_number VKN (10 hane) ya da TCKN (11 hane), faturada kullanilir (docs/INVOICES.md)

CREATE TABLE customers (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    company_name character varying(255),
    phone character varying(255),
    email character varying(255),
    notes text,
    tax_number character varying(11),
    tax_office character varying(100),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE customers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE customers_id_seq OWNED BY customers.id;

ALTER TABLE ONLY customers ALTER COLUMN id SET DEFAULT nextval('customers_id_seq'::regclass);

ALTER TABLE ONLY customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (id);
