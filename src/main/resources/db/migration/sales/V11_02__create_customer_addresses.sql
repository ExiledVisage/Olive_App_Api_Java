-- customer_addresses: musteri adresleri

CREATE TABLE customer_addresses (
    id bigint NOT NULL,
    customer_id bigint NOT NULL,
    label character varying(255) NOT NULL,
    country character varying(255) DEFAULT 'TR'::character varying NOT NULL,
    city character varying(255) NOT NULL,
    district character varying(255),
    neighborhood character varying(255),
    street character varying(255),
    building_no character varying(255),
    apartment_no character varying(255),
    postal_code character varying(255),
    full_address text,
    latitude numeric(10,7),
    longitude numeric(10,7),
    is_default boolean DEFAULT false NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE customer_addresses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE customer_addresses_id_seq OWNED BY customer_addresses.id;

ALTER TABLE ONLY customer_addresses ALTER COLUMN id SET DEFAULT nextval('customer_addresses_id_seq'::regclass);

ALTER TABLE ONLY customer_addresses
    ADD CONSTRAINT customer_addresses_pkey PRIMARY KEY (id);

CREATE INDEX customer_addresses_customer_id_index ON customer_addresses USING btree (customer_id);

ALTER TABLE ONLY customer_addresses
    ADD CONSTRAINT customer_addresses_customer_id_foreign FOREIGN KEY (customer_id)
        REFERENCES customers(id) ON DELETE CASCADE;
