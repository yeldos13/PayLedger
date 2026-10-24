-- Transactional outbox: events are written in the same transaction as the transfer, then relayed to Kafka.
create table outbox (
    id           uuid primary key,
    aggregate_id uuid        not null,
    type         varchar(64) not null,
    payload      jsonb       not null,
    created_at   timestamptz not null
);
