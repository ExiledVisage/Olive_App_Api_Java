-- Laravel: 2026_09_18_100006_create_analytics_reports_table.php

create table "analytics_reports" (
    "id" bigserial not null primary key,
    "user_id" bigint not null,
    "name" varchar(120) not null,
    "config" json not null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "analytics_reports"
    add constraint "analytics_reports_user_id_foreign"
        foreign key ("user_id")
        references "users" ("id")
        on delete cascade;

create index "analytics_reports_user_id_updated_at_index"
    on "analytics_reports" ("user_id", "updated_at");
