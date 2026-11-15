-- production_products: urun kategorileri (zeytin, yag, pirina...) ve varsayilan yogunluk

CREATE TABLE production_products (
    id bigint NOT NULL,
    key character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    default_density_kg_per_litre numeric(6,4)
);

CREATE SEQUENCE production_products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE production_products_id_seq OWNED BY production_products.id;

ALTER TABLE ONLY production_products ALTER COLUMN id SET DEFAULT nextval('production_products_id_seq'::regclass);

ALTER TABLE ONLY production_products
    ADD CONSTRAINT production_products_key_unique UNIQUE (key);

ALTER TABLE ONLY production_products
    ADD CONSTRAINT production_products_pkey PRIMARY KEY (id);
