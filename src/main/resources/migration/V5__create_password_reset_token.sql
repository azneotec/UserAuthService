create table password_reset_token (
    id bigint not null auto_increment,
    created_date datetime(6),
    last_modified_date datetime(6),
    status enum ('ACTIVE','INACTIVE') default 'ACTIVE',
    token_hash varchar(64) not null,
    expires_at datetime(6) not null,
    used_at datetime(6) null,
    user_id bigint not null,
    primary key (id),
    constraint uk_password_reset_token_hash unique (token_hash),
    constraint FK_password_reset_token_user foreign key (user_id) references user (id),
    index idx_password_reset_token_user (user_id)
) engine=InnoDB;
