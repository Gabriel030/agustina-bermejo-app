create table mp_webhook_received_event (
    id bigserial primary key,
    received_at timestamp not null,
    x_signature varchar(500) not null,
    x_request_id varchar(200) not null,
    mp_payment_id varchar(100) not null,
    billing_mode_at_receipt varchar(10) not null,
    processing_status varchar(20) not null,
    constraint ck_mp_webhook_received_event_status check (processing_status in ('RECEIVED')),
    constraint ck_mp_webhook_received_event_mode check (billing_mode_at_receipt in ('OFF', 'MIRROR', 'FULL'))
);

create index ix_mp_webhook_received_event_received_at on mp_webhook_received_event (received_at);
create index ix_mp_webhook_received_event_mp_payment_id on mp_webhook_received_event (mp_payment_id);
