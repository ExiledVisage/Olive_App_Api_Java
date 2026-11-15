-- production_stages: hattin asamalari (sirali)

CREATE TABLE production_stages (
    id bigint NOT NULL,
    production_line_id bigint NOT NULL,
    process_type_id bigint,
    name character varying(255),
    stage_order integer NOT NULL,
    is_required boolean DEFAULT true NOT NULL,
    requires_approval boolean DEFAULT false NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE production_stages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE production_stages_id_seq OWNED BY production_stages.id;

ALTER TABLE ONLY production_stages ALTER COLUMN id SET DEFAULT nextval('production_stages_id_seq'::regclass);

ALTER TABLE ONLY production_stages
    ADD CONSTRAINT production_stages_pkey PRIMARY KEY (id);

ALTER TABLE ONLY production_stages
    ADD CONSTRAINT production_stages_production_line_id_stage_order_unique UNIQUE (production_line_id, stage_order);

ALTER TABLE ONLY production_stages
    ADD CONSTRAINT production_stages_process_type_id_foreign FOREIGN KEY (process_type_id)
        REFERENCES process_types(id) ON DELETE RESTRICT;

ALTER TABLE ONLY production_stages
    ADD CONSTRAINT production_stages_production_line_id_foreign FOREIGN KEY (production_line_id)
        REFERENCES production_lines(id) ON DELETE CASCADE;
