alter table student
    add column guardian_full_name varchar(200),
    add column guardian_dni varchar(20),
    add column guardian_relationship varchar(60),
    add column guardian_phone varchar(40),
    add column guardian_email varchar(255),
    add column emergency_contact_name varchar(200),
    add column emergency_contact_relationship varchar(60),
    add column emergency_contact_phone varchar(40);
