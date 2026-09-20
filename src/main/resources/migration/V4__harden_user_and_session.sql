-- Backfill audit timestamps on the roles seeded by V2 (V2 itself must not be edited: Flyway
-- checksums an applied migration and refuses to start if it changes).
update role
set created_date       = coalesce(created_date, now(6)),
    last_modified_date = coalesce(last_modified_date, now(6));

-- Uniqueness the service already assumes in signup. MySQL allows repeated NULLs in a unique
-- index, so users without a phone number are fine.
-- Before applying on a DB with existing data, check for duplicates:
--   select email from user group by email having count(*) > 1;
--   select phone_number from user where phone_number is not null group by phone_number having count(*) > 1;
--   select count(*) from user where email is null;
alter table user
    modify column email varchar(255) not null,
    add constraint uk_user_email unique (email),
    add constraint uk_user_phone_number unique (phone_number);

-- Every pre-V4 session was signed with a key that was regenerated on each restart, so none of
-- them can be verified anymore. Clear them so the NOT NULL columns below can be added cleanly.
delete from user_session;

alter table user_session
    drop column token,
    add column token_hash varchar(64) not null,
    add column expires_at datetime(6) not null,
    modify column user_id bigint not null,
    add constraint uk_user_session_token_hash unique (token_hash),
    add index idx_user_session_user_status (user_id, status);
