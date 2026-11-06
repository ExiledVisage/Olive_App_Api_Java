-- V2: Spring Modulith Event Publication Registry.
--
-- Java'nin kendi altyapi tablosu; Laravel'in semasinin parcasi degil, Laravel kullanmaz.
-- Gecis kurali: Java Laravel tablolarina dokunmaz, yalnizca kendi altyapi tablolarini ekler.
-- Laravel'de migrate:fresh bu tabloyu da silerse Java bir sonraki acilista yeniden olusturur
-- (Flyway once V1'i baseline kabul eder, sonra bunu calistirir).
--
-- Kolonlar JpaEventPublication entity'siyle ayni; metin kolonlari Spring Modulith'in
-- PostgreSQL semasindaki gibi TEXT (varchar(255) serilestirilmis event'ler icin kisa kalir).

CREATE TABLE event_publication (
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT CHECK (status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED')),
    completion_attempts    INTEGER                  NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
