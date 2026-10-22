-- Seeds the first admin so ROLE_ADMIN can be granted to others through PUT /users/{id}/role/admin.
-- The password is the bcrypt hash of a known development value ("password"). Change it, or remove this
-- user, before this migration runs anywhere that is not a local environment.
-- Idempotent: an existing user with this email is left untouched, and a role link is only added when missing.
insert into user (name, email, password, phone_number, status, created_date, last_modified_date)
select 'Admin Azad', 'azad.admintest1@yopmail.com',
       '$2a$10$hV3UsxIZL2yfvJOuMkcg7O1R5hogHbyai7QnrgMaT6dGkZkjwCbvu',
       '+6500990001', 'ACTIVE', now(6), now(6)
where not exists (select 1 from user where email = 'azad.admintest1@yopmail.com');

insert into user_roles (user_id, roles_id)
select u.id, r.id
from user u
         join role r on r.value in ('ROLE_USER', 'ROLE_ADMIN')
where u.email = 'azad.admintest1@yopmail.com'
  and not exists (select 1 from user_roles ur where ur.user_id = u.id and ur.roles_id = r.id);
