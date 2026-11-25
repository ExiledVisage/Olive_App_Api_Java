-- filling_setups: dolum kosusunun hat hazirligi (docs/QUALITY.md 2): uc onay, onaylayan ve zaman; kosu basina bir

CREATE TABLE filling_setups (
    id bigint NOT NULL,
    finished_product_run_id bigint NOT NULL,
    conveyor_clean boolean NOT NULL,
    air_checked boolean NOT NULL,
    previous_product_cleared boolean NOT NULL,
    note text,
    approved_by_user_id bigint,
    approved_at timestamp(0) without time zone NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE filling_setups_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE filling_setups_id_seq OWNED BY filling_setups.id;

ALTER TABLE ONLY filling_setups ALTER COLUMN id SET DEFAULT nextval('filling_setups_id_seq'::regclass);

ALTER TABLE ONLY filling_setups
    ADD CONSTRAINT filling_setups_pkey PRIMARY KEY (id);

ALTER TABLE ONLY filling_setups
    ADD CONSTRAINT filling_setups_run_unique UNIQUE (finished_product_run_id);

ALTER TABLE ONLY filling_setups
    ADD CONSTRAINT filling_setups_run_fk FOREIGN KEY (finished_product_run_id)
        REFERENCES finished_product_runs(id) ON DELETE CASCADE;

ALTER TABLE ONLY filling_setups
    ADD CONSTRAINT filling_setups_user_fk FOREIGN KEY (approved_by_user_id) REFERENCES users(id) ON DELETE SET NULL;
