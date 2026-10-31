create table wallets (
    id          uuid           primary key,
    holder_name varchar(120)   not null,
    balance     numeric(19, 2) not null default 0.00,
    status      varchar(20)    not null,
    version     bigint         not null default 0,
    created_at  timestamptz    not null,
    updated_at  timestamptz    not null,
    constraint chk_wallets_balance_non_negative check (balance >= 0),
    constraint chk_wallets_status check (status in ('ACTIVE', 'INACTIVE'))
);

create index idx_wallets_status_created_at on wallets (status, created_at desc, id desc);
create index idx_wallets_created_at on wallets (created_at desc, id desc);
