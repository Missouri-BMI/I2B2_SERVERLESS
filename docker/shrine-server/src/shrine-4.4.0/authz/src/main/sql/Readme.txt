SHRINE 2020-1326 Support all 3 db types

(1) Create a database called "AUTHZ", containing the AUTHZ_USER table, by (as user 'root') running the provided script, authz.sql

(2) Insert records into AUTHZ_USER, using their SSO id, e.g., as sent from the SP via the REMOTE_USER header. See provided sample data, authz-data.sql

(3) Check that your copy of shrine.conf on Shrine's tomcat has a data-source configuration on for this database as part of the configuration for the WhiteBlackListAttrProvider

(4) Check that tomcat's context.xml has a stanza for this data Resource

(6) It might be necessary, as root, to explicitly allow shrine to use this database:
        GRANT ALL PRIVILEGES ON AUTHZ.* TO 'shrine'@'localhost';