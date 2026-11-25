-- quality_documents: BRC/IFS dokuman kaydi (docs/QUALITY.md 4); sonraki gozden gecirme son tarih + review_months

CREATE TABLE quality_documents (
    id bigint NOT NULL,
    code character varying(50) NOT NULL,
    title character varying(255) NOT NULL,
    kind character varying(30) NOT NULL,
    clause character varying(100),
    version character varying(30),
    owner_user_id bigint,
    status character varying(20) DEFAULT 'active'::character varying NOT NULL,
    review_months integer DEFAULT 12 NOT NULL,
    last_reviewed_at date,
    link text,
    note text,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone,
    CONSTRAINT quality_documents_kind_check
        CHECK (kind IN ('procedure', 'instruction', 'form', 'plan', 'policy', 'record')),
    CONSTRAINT quality_documents_status_check CHECK (status IN ('draft', 'active', 'obsolete')),
    CONSTRAINT quality_documents_review_months_check CHECK (review_months BETWEEN 1 AND 60)
);

CREATE SEQUENCE quality_documents_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE quality_documents_id_seq OWNED BY quality_documents.id;

ALTER TABLE ONLY quality_documents ALTER COLUMN id SET DEFAULT nextval('quality_documents_id_seq'::regclass);

ALTER TABLE ONLY quality_documents
    ADD CONSTRAINT quality_documents_pkey PRIMARY KEY (id);

ALTER TABLE ONLY quality_documents
    ADD CONSTRAINT quality_documents_code_unique UNIQUE (code);

ALTER TABLE ONLY quality_documents
    ADD CONSTRAINT quality_documents_owner_fk FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE SET NULL;
