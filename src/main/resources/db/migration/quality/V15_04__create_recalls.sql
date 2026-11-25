-- recalls: geri cagirma ve izlenebilirlik tatbikati (docs/QUALITY.md 3); trace ileri izin ozeti (olusturulurken,
-- yenilenebilir)

CREATE TABLE recalls (
    id bigint NOT NULL,
    code character varying(30) NOT NULL,
    kind character varying(10) NOT NULL,
    source_type character varying(20) NOT NULL,
    source_id bigint NOT NULL,
    reason text NOT NULL,
    status character varying(20) DEFAULT 'open'::character varying NOT NULL,
    started_at timestamp(0) without time zone NOT NULL,
    completed_at timestamp(0) without time zone,
    target_minutes integer,
    findings text,
    trace jsonb,
    traced_at timestamp(0) without time zone,
    created_by_user_id bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT recalls_kind_check CHECK (kind IN ('recall', 'drill')),
    CONSTRAINT recalls_source_type_check CHECK (source_type IN ('delivery', 'tank_material', 'run')),
    CONSTRAINT recalls_status_check CHECK (status IN ('open', 'completed', 'cancelled'))
);

CREATE SEQUENCE recalls_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE recalls_id_seq OWNED BY recalls.id;

ALTER TABLE ONLY recalls ALTER COLUMN id SET DEFAULT nextval('recalls_id_seq'::regclass);

ALTER TABLE ONLY recalls
    ADD CONSTRAINT recalls_pkey PRIMARY KEY (id);

ALTER TABLE ONLY recalls
    ADD CONSTRAINT recalls_code_unique UNIQUE (code);

ALTER TABLE ONLY recalls
    ADD CONSTRAINT recalls_user_fk FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL;
