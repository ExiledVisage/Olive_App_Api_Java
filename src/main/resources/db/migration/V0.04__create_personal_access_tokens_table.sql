-- Laravel: 2026_03_06_103137_create_personal_access_tokens_table.php

create table "personal_access_tokens" (
    "id" bigserial not null primary key,
    "tokenable_type" varchar(255) not null,
    "tokenable_id" bigint not null,
    "name" text not null,
    "token" varchar(64) not null,
    "abilities" text null,
    "last_used_at" timestamp(0) without time zone null,
    "expires_at" timestamp(0) without time zone null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

create index "personal_access_tokens_tokenable_type_tokenable_id_index"
    on "personal_access_tokens" ("tokenable_type", "tokenable_id");

alter table "personal_access_tokens"
    add constraint "personal_access_tokens_token_unique" unique ("token");

create index "personal_access_tokens_expires_at_index" on "personal_access_tokens" ("expires_at");
