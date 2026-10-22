alter table bank_transfer_receipt add column rejection_code varchar(30);

update bank_transfer_receipt set rejection_code = 'REVIEWER_REJECTION' where status = 'REJECTED';

alter table bank_transfer_receipt add constraint ck_bank_transfer_receipt_rejection_code
    check (rejection_code in ('REVIEWER_REJECTION', 'TRANSFER_DATE_MISMATCH'));

alter table bank_transfer_receipt add constraint ck_bank_transfer_receipt_rejection_code_matches_status
    check (rejection_code is null or status = 'REJECTED');
