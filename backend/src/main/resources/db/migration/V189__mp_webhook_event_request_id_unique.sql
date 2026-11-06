delete from mp_webhook_received_event duplicate
using mp_webhook_received_event kept
where duplicate.x_request_id = kept.x_request_id
  and duplicate.x_request_id <> ''
  and duplicate.id > kept.id;

create unique index uq_mp_webhook_received_event_request_id
    on mp_webhook_received_event (x_request_id)
    where x_request_id <> '';
