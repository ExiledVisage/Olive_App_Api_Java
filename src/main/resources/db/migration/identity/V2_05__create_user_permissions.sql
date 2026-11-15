-- user_permissions: kullaniciya dogrudan verilen izinler

CREATE TABLE user_permissions (
    user_id bigint NOT NULL,
    permission_id bigint NOT NULL
);

ALTER TABLE ONLY user_permissions
    ADD CONSTRAINT user_permissions_pkey PRIMARY KEY (user_id, permission_id);

CREATE INDEX user_permissions_permission_id_index ON user_permissions USING btree (permission_id);

ALTER TABLE ONLY user_permissions
    ADD CONSTRAINT user_permissions_permission_id_fkey FOREIGN KEY (permission_id)
        REFERENCES permissions(id) ON DELETE CASCADE;

ALTER TABLE ONLY user_permissions
    ADD CONSTRAINT user_permissions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
