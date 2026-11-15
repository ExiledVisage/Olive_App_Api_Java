-- packaging_items: ambalaj kalemleri (sise, kapak, etiket...)

CREATE TABLE packaging_items (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(30) NOT NULL,
    current_stock integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE packaging_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE packaging_items_id_seq OWNED BY packaging_items.id;

ALTER TABLE ONLY packaging_items ALTER COLUMN id SET DEFAULT nextval('packaging_items_id_seq'::regclass);

ALTER TABLE ONLY packaging_items
    ADD CONSTRAINT packaging_items_pkey PRIMARY KEY (id);
