-- auth_sessions: oturumlar (cihaz basina giris)

CREATE TABLE auth_sessions (
    id uuid NOT NULL,
    user_id bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone
);

ALTER TABLE ONLY auth_sessions
    ADD CONSTRAINT auth_sessions_pkey PRIMARY KEY (id);

CREATE INDEX auth_sessions_user_id_index ON auth_sessions USING btree (user_id);

ALTER TABLE ONLY auth_sessions
    ADD CONSTRAINT auth_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
