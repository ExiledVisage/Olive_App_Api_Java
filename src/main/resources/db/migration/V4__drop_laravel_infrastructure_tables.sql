-- Laravel'in kendi altyapi tablolari: Java bunlari kullanmaz (kuyruk, onbellek, oturum, migration kaydi).
-- Is verisi tasimazlar; satirlari gecici ve Laravel'e ozeldir.

drop table if exists migrations;
drop table if exists cache;
drop table if exists cache_locks;
drop table if exists jobs;
drop table if exists job_batches;
drop table if exists failed_jobs;
drop table if exists sessions;
drop table if exists password_reset_tokens;
