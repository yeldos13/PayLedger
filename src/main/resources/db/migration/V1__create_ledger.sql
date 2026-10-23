create table accounts (
    id         uuid primary key,
    currency   varchar(3)  not null,
    type       varchar(16) not null check (type in ('CUSTOMER', 'SYSTEM')),
    balance    bigint      not null,
    created_at timestamptz not null,
    constraint customer_balance_non_negative check (type = 'SYSTEM' or balance >= 0)
);

-- One funding account per currency, the counterpart of every deposit.
create unique index accounts_system_currency on accounts (currency) where type = 'SYSTEM';

insert into accounts (id, currency, type, balance, created_at) values
    ('00000000-0000-0000-0000-000000000398', 'KZT', 'SYSTEM', 0, now()),
    ('00000000-0000-0000-0000-000000000840', 'USD', 'SYSTEM', 0, now()),
    ('00000000-0000-0000-0000-000000000978', 'EUR', 'SYSTEM', 0, now());

create table transfers (
    id         uuid primary key,
    from_id    uuid        not null references accounts (id),
    to_id      uuid        not null references accounts (id),
    amount     bigint      not null check (amount > 0),
    currency   varchar(3)  not null,
    created_at timestamptz not null
);

-- Double entry: every transfer writes one negative and one positive entry, so all entries sum to zero.
create table entries (
    id          uuid primary key,
    transfer_id uuid        not null references transfers (id),
    account_id  uuid        not null references accounts (id),
    amount      bigint      not null check (amount <> 0),
    created_at  timestamptz not null
);

create index entries_account on entries (account_id, created_at);
