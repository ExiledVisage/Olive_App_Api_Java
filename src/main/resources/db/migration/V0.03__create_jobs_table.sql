-- Laravel: 0001_01_01_000002_create_jobs_table.php

create table "jobs" (
    "id" bigserial not null primary key,
    "queue" varchar(255) not null,
    "payload" text not null,
    "attempts" smallint not null,
    "reserved_at" integer null,
    "available_at" integer not null,
    "created_at" integer not null
);

create index "jobs_queue_index" on "jobs" ("queue");

create table "job_batches" (
    "id" varchar(255) not null,
    "name" varchar(255) not null,
    "total_jobs" integer not null,
    "pending_jobs" integer not null,
    "failed_jobs" integer not null,
    "failed_job_ids" text not null,
    "options" text null,
    "cancelled_at" integer null,
    "created_at" integer not null,
    "finished_at" integer null
);

alter table "job_batches" add primary key ("id");

create table "failed_jobs" (
    "id" bigserial not null primary key,
    "uuid" varchar(255) not null,
    "connection" text not null,
    "queue" text not null,
    "payload" text not null,
    "exception" text not null,
    "failed_at" timestamp(0) without time zone not null default CURRENT_TIMESTAMP
);

alter table "failed_jobs" add constraint "failed_jobs_uuid_unique" unique ("uuid");
