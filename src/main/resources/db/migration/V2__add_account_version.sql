-- Optimistic locking: every balance update bumps the version, a stale write fails instead of overwriting.
alter table accounts add column version bigint not null default 0;
