create table role (
    created_date datetime(6),
    id bigint not null auto_increment,
    last_modified_date datetime(6),
    value varchar(255),
    status enum ('ACTIVE','INACTIVE') default 'ACTIVE',
    primary key (id)
) engine=InnoDB;

create table user (
    created_date datetime(6),
    id bigint not null auto_increment,
    last_modified_date datetime(6),
    email varchar(255),
    name varchar(255),
    password varchar(255),
    phone_number varchar(255),
    status enum ('ACTIVE','INACTIVE') default 'ACTIVE',
    primary key (id)
) engine=InnoDB;

create table user_roles (
    roles_id bigint not null,
    user_id bigint not null
) engine=InnoDB;

alter table user_roles add constraint FK_user_roles_role foreign key (roles_id) references role (id);
alter table user_roles add constraint FK_user_roles_user foreign key (user_id) references user (id);
