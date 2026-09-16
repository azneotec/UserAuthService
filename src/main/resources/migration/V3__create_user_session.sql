create table user_session (
    created_date datetime(6),
    id bigint not null auto_increment,
    last_modified_date datetime(6),
    token varchar(255),
    status enum ('ACTIVE','INACTIVE') default 'ACTIVE',
    user_id bigint,
    primary key (id)
) engine=InnoDB;

alter table user_session add constraint FK_user_session_user foreign key (user_id) references user (id);
