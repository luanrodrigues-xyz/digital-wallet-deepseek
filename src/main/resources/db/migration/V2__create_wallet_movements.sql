create table wallet_movements (
    id                bigint         generated always as identity primary key,
    wallet_id         uuid           not null references wallets (id),
    type              varchar(30)    not null,
    amount            numeric(19, 2) not null,
    balance_after     numeric(19, 2) not null,
    related_wallet_id uuid           references wallets (id),
    transfer_id       uuid,
    idempotency_key   varchar(128),
    created_at        timestamptz    not null,
    constraint chk_wallet_movements_amount_positive check (amount > 0),
    constraint chk_wallet_movements_balance_after_non_negative check (balance_after >= 0),
    constraint chk_wallet_movements_type check (type in ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER_IN', 'TRANSFER_OUT')),
    constraint chk_wallet_movements_transfer_pair check (
        (type in ('TRANSFER_IN', 'TRANSFER_OUT') and transfer_id is not null and related_wallet_id is not null)
        or
        (type in ('DEPOSIT', 'WITHDRAWAL') and transfer_id is null and related_wallet_id is null)
    )
);

create index idx_wallet_movements_wallet_created
    on wallet_movements (wallet_id, created_at desc, id desc);

create index idx_wallet_movements_transfer
    on wallet_movements (transfer_id)
    where transfer_id is not null;

create index idx_wallet_movements_idempotency_key
    on wallet_movements (idempotency_key)
    where idempotency_key is not null;
