-- analytics_reports: kayitli analitik raporlar

CREATE TABLE analytics_reports (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    name character varying(120) NOT NULL,
    config jsonb NOT NULL,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE analytics_reports_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE analytics_reports_id_seq OWNED BY analytics_reports.id;

ALTER TABLE ONLY analytics_reports ALTER COLUMN id SET DEFAULT nextval('analytics_reports_id_seq'::regclass);

ALTER TABLE ONLY analytics_reports
    ADD CONSTRAINT analytics_reports_pkey PRIMARY KEY (id);

CREATE INDEX analytics_reports_user_id_updated_at_index ON analytics_reports USING btree (user_id, updated_at);

ALTER TABLE ONLY analytics_reports
    ADD CONSTRAINT analytics_reports_user_id_foreign FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
