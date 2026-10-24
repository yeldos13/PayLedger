alter table outbox add column published_at timestamptz;

-- The publisher only ever scans unpublished rows, so keep that index small.
create index outbox_unpublished on outbox (created_at) where published_at is null;
