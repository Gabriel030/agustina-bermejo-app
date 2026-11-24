alter table notification_delivery drop constraint notification_delivery_category_check;

alter table notification_delivery
    add constraint notification_delivery_category_check
        check (category in ('COLLECTION_REMINDER', 'ANNOUNCEMENT', 'BROADCAST', 'INVITATION')) not valid;
