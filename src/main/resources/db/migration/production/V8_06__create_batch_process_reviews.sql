-- batch_process_reviews: asama isleminin onay gecmisi

CREATE TABLE batch_process_reviews (
    id bigint NOT NULL,
    batch_process_id bigint NOT NULL,
    decision character varying(20) NOT NULL,
    reviewed_by_user_id bigint,
    submitted_by_user_id bigint,
    operation_data jsonb,
    note text,
    reviewed_at timestamp(0) without time zone NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE batch_process_reviews_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE batch_process_reviews_id_seq OWNED BY batch_process_reviews.id;

ALTER TABLE ONLY batch_process_reviews ALTER COLUMN id SET DEFAULT nextval('batch_process_reviews_id_seq'::regclass);

ALTER TABLE ONLY batch_process_reviews
    ADD CONSTRAINT batch_process_reviews_pkey PRIMARY KEY (id);

CREATE INDEX batch_process_reviews_batch_process_id_reviewed_at_index ON batch_process_reviews
    USING btree (batch_process_id, reviewed_at);

ALTER TABLE ONLY batch_process_reviews
    ADD CONSTRAINT batch_process_reviews_batch_process_id_foreign FOREIGN KEY (batch_process_id)
        REFERENCES batch_processes(id) ON DELETE CASCADE;

ALTER TABLE ONLY batch_process_reviews
    ADD CONSTRAINT batch_process_reviews_reviewed_by_user_id_foreign FOREIGN KEY (reviewed_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY batch_process_reviews
    ADD CONSTRAINT batch_process_reviews_submitted_by_user_id_foreign FOREIGN KEY (submitted_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;
