-- A client retry with the same key must find the first transfer instead of moving money again.
alter table transfers add column idempotency_key varchar(255) not null unique;
