-- W3C traceparent of the request that wrote the event, so the Kafka publish joins the same trace.
alter table outbox add column trace_parent varchar(55);
