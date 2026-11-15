-- event_publication: Spring Modulith olay kaydi (yayinlanan ve tamamlanan olaylar)

CREATE TABLE event_publication (
    id uuid NOT NULL,
    listener_id text NOT NULL,
    event_type text NOT NULL,
    serialized_event text NOT NULL,
    publication_date timestamp with time zone NOT NULL,
    completion_date timestamp with time zone,
    status text,
    completion_attempts integer DEFAULT 0 NOT NULL,
    last_resubmission_date timestamp with time zone,
    CONSTRAINT event_publication_status_check CHECK (status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED'))
);

ALTER TABLE ONLY event_publication
    ADD CONSTRAINT event_publication_pkey PRIMARY KEY (id);

CREATE INDEX event_publication_by_completion_date_idx ON event_publication USING btree (completion_date);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
