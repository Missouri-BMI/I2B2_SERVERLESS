-- authorization tables --

drop table if exists AUTHZ_USER;
create table AUTHZ_USER (
    SSO_ID varchar(255) not null,
    BLACK_LISTED tinyint(1) not null,
    WHITE_LISTED tinyint(1) not null,
    primary key (SSO_ID)
) charset=utf8;
