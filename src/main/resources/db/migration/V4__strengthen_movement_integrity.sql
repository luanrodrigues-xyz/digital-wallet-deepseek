alter table wallet_movements
    alter column idempotency_key set not null,
    add constraint fk_movements_idempotency
        foreign key (idempotency_key) references idempotency_records (idempotency_key),
    add constraint chk_movements_distinct_wallets
        check (related_wallet_id is null or related_wallet_id <> wallet_id),
    add constraint chk_movements_amount_finite
        check (amount <= 99999999999999999.99),
    add constraint chk_movements_balance_finite
        check (balance_after <= 99999999999999999.99);

create unique index uk_movements_key_type on wallet_movements (idempotency_key, type);

alter table wallets
    add constraint chk_wallets_balance_finite check (balance <= 99999999999999999.99),
    add constraint chk_wallets_holder_name check (length(btrim(holder_name)) > 0);
