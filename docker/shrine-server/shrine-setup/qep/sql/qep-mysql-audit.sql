-- Query-entry-point audit table --

SELECT SHRINE_NODE_ID,
       USER_NAME,
       NETWORK_QUERY_ID,
       QUERY_NAME,
       from_unixtime(TIME_QUERY_SENT/1000)
FROM QUERY_SENT;
