-- refresh_tokens: oturumlarin refresh token'lari

CREATE TABLE refresh_tokens (
    id bigint NOT NULL,
    session_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL
);

CREATE SEQUENCE refresh_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE refresh_tokens_id_seq OWNED BY refresh_tokens.id;

ALTER TABLE ONLY refresh_tokens ALTER COLUMN id SET DEFAULT nextval('refresh_tokens_id_seq'::regclass);

ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);

ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash);

CREATE INDEX refresh_tokens_session_id_index ON refresh_tokens USING btree (session_id);

ALTER TABLE ONLY refresh_tokens
    ADD CONSTRAINT refresh_tokens_session_id_fkey FOREIGN KEY (session_id)
        REFERENCES auth_sessions(id) ON DELETE CASCADE;
