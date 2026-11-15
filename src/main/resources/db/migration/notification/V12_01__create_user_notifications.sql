-- user_notifications: kullanici bildirimleri

CREATE TABLE user_notifications (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    sent_by_user_id bigint,
    type character varying(40) DEFAULT 'nudge'::character varying NOT NULL,
    channel character varying(20) DEFAULT 'in_app'::character varying NOT NULL,
    title character varying(255) NOT NULL,
    body text,
    subject_type character varying(60),
    subject_id bigint,
    read_at timestamp(0) without time zone,
    created_at timestamp(0) without time zone,
    updated_at timestamp(0) without time zone
);

CREATE SEQUENCE user_notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE user_notifications_id_seq OWNED BY user_notifications.id;

ALTER TABLE ONLY user_notifications ALTER COLUMN id SET DEFAULT nextval('user_notifications_id_seq'::regclass);

ALTER TABLE ONLY user_notifications
    ADD CONSTRAINT user_notifications_pkey PRIMARY KEY (id);

CREATE INDEX user_notif_subject_idx ON user_notifications USING btree (subject_type, subject_id, created_at);

CREATE INDEX user_notifications_user_id_created_at_index ON user_notifications USING btree (user_id, created_at);

CREATE INDEX user_notifications_user_id_read_at_index ON user_notifications USING btree (user_id, read_at);

ALTER TABLE ONLY user_notifications
    ADD CONSTRAINT user_notifications_sent_by_user_id_foreign FOREIGN KEY (sent_by_user_id)
        REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE ONLY user_notifications
    ADD CONSTRAINT user_notifications_user_id_foreign FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
