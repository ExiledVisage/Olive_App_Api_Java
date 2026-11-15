-- product_suppliers: ambalaj tedarikcileri

CREATE TABLE product_suppliers (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(50) NOT NULL,
    phone character varying(50),
    email character varying(255),
    address text,
    note text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE product_suppliers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE product_suppliers_id_seq OWNED BY product_suppliers.id;

ALTER TABLE ONLY product_suppliers ALTER COLUMN id SET DEFAULT nextval('product_suppliers_id_seq'::regclass);

ALTER TABLE ONLY product_suppliers
    ADD CONSTRAINT product_suppliers_code_unique UNIQUE (code);

ALTER TABLE ONLY product_suppliers
    ADD CONSTRAINT product_suppliers_pkey PRIMARY KEY (id);

CREATE INDEX product_suppliers_is_active_index ON product_suppliers USING btree (is_active);
