-- Fraud module's own copy of what it needs from each transfer; it never reads ledger tables.
create table fraud_transfers (
    transfer_id   uuid primary key,
    to_account_id uuid        not null,
    occurred_at   timestamptz not null
);

create index fraud_transfers_to_account on fraud_transfers (to_account_id, occurred_at);

create table fraud_alerts (
    id          bigserial primary key,
    transfer_id uuid        not null,
    rule        varchar(32) not null,
    created_at  timestamptz not null
);

create index fraud_alerts_transfer on fraud_alerts (transfer_id);
