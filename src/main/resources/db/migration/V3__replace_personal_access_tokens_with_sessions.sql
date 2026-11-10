-- Sanctum token'lari yerine JWT: bir oturum (refresh token ailesi) ve donen refresh token'lar.

drop table if exists personal_access_tokens;

create table auth_sessions (
    id uuid primary key,
    user_id bigint not null references users (id) on delete cascade,
    created_at timestamptz not null,
    revoked_at timestamptz null
);

create index auth_sessions_user_id_index on auth_sessions (user_id);

create table refresh_tokens (
    id bigserial primary key,
    session_id uuid not null references auth_sessions (id) on delete cascade,
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    consumed_at timestamptz null,
    created_at timestamptz not null
);

create index refresh_tokens_session_id_index on refresh_tokens (session_id);
