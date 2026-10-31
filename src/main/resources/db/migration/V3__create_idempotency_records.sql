create table idempotency_records (
    id               uuid        primary key,
    idempotency_key  varchar(128) not null,
    operation_type   varchar(30)  not null,
    request_hash     varchar(64)  not null,
    status           varchar(20)  not null,
    response_status  integer,
    response_body    text,
    created_at       timestamptz  not null,
    completed_at     timestamptz,
    constraint uk_idempotency_records_idempotency_key unique (idempotency_key),
    constraint chk_idempotency_records_status check (status in ('IN_PROGRESS', 'COMPLETED')),
    constraint chk_idempotency_records_operation_type check (operation_type in ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    constraint chk_idempotency_records_response check (
        (status = 'IN_PROGRESS' and response_status is null and response_body is null and completed_at is null)
        or
        (status = 'COMPLETED' and response_status is not null and response_body is not null and completed_at is not null)
    )
);

create index idx_idempotency_records_created_at on idempotency_records (created_at);
