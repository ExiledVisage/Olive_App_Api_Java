-- production_lines: uretim hatlari

CREATE TABLE production_lines (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE production_lines_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE production_lines_id_seq OWNED BY production_lines.id;

ALTER TABLE ONLY production_lines ALTER COLUMN id SET DEFAULT nextval('production_lines_id_seq'::regclass);

ALTER TABLE ONLY production_lines
    ADD CONSTRAINT production_lines_pkey PRIMARY KEY (id);
