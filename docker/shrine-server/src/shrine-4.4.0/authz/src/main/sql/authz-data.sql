
-- authorization data --

insert into AUTHZ_USER (SSO_ID, BLACK_LISTED, WHITE_LISTED) values
     ('notListed', 0, 0),
     ('whiteList', 0, 1),
     ('blackList', 1, 0),
     ('bothLists', 1, 1)    /* this would be an edge case! */
;
