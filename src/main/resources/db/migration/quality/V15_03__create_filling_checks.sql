-- filling_checks: dolumun saatlik kontrolu (docs/QUALITY.md 2): numune agirliklari, hedef ve tolerans, gorsel
-- kontroller, cam kirigi

CREATE TABLE filling_checks (
    id bigint NOT NULL,
    finished_product_run_id bigint NOT NULL,
    checked_at timestamp(0) without time zone NOT NULL,
    weight_1 numeric(8,2) NOT NULL,
    weight_2 numeric(8,2),
    weight_3 numeric(8,2),
    weight_4 numeric(8,2),
    weight_5 numeric(8,2),
    target_g numeric(8,2) NOT NULL,
    tolerance_g numeric(8,2) NOT NULL,
    average_g numeric(8,2) NOT NULL,
    weight_ok boolean NOT NULL,
    seal_ok boolean,
    cap_ok boolean,
    label_ok boolean,
    coding_ok boolean,
    glass_broken boolean DEFAULT false NOT NULL,
    note text,
    checked_by_user_id bigint,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE filling_checks_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE filling_checks_id_seq OWNED BY filling_checks.id;

ALTER TABLE ONLY filling_checks ALTER COLUMN id SET DEFAULT nextval('filling_checks_id_seq'::regclass);

ALTER TABLE ONLY filling_checks
    ADD CONSTRAINT filling_checks_pkey PRIMARY KEY (id);

ALTER TABLE ONLY filling_checks
    ADD CONSTRAINT filling_checks_run_fk FOREIGN KEY (finished_product_run_id)
        REFERENCES finished_product_runs(id) ON DELETE CASCADE;

ALTER TABLE ONLY filling_checks
    ADD CONSTRAINT filling_checks_user_fk FOREIGN KEY (checked_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX filling_checks_run_index ON filling_checks USING btree (finished_product_run_id, checked_at);
