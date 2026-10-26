-- Fraud module inbox: ids of events already handled, so a redelivered event is processed only once.
create table fraud_processed_events (
    event_id     uuid primary key,
    processed_at timestamptz not null
);
