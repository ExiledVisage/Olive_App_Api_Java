-- Laravel: 2026_09_28_100000_create_user_notifications_table.php

create table "user_notifications" (
    "id" bigserial not null primary key,
    "user_id" bigint not null,
    "sent_by_user_id" bigint null,
    "type" varchar(40) not null default 'nudge',
    "channel" varchar(20) not null default 'in_app',
    "title" varchar(255) not null,
    "body" text null,
    "subject_type" varchar(60) null,
    "subject_id" bigint null,
    "read_at" timestamp(0) without time zone null,
    "created_at" timestamp(0) without time zone null,
    "updated_at" timestamp(0) without time zone null
);

alter table "user_notifications"
    add constraint "user_notifications_user_id_foreign"
        foreign key ("user_id")
        references "users" ("id")
        on delete cascade;

alter table "user_notifications"
    add constraint "user_notifications_sent_by_user_id_foreign"
        foreign key ("sent_by_user_id")
        references "users" ("id")
        on delete set null;

create index "user_notifications_user_id_read_at_index"
    on "user_notifications" ("user_id", "read_at");

create index "user_notifications_user_id_created_at_index"
    on "user_notifications" ("user_id", "created_at");

create index "user_notif_subject_idx"
    on "user_notifications" ("subject_type", "subject_id", "created_at");
