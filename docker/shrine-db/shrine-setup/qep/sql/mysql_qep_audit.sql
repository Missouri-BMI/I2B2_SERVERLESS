SELECT shrineNodeId, userName, networkQueryId, queryName, queryTopicId, queryTopicName, FROM_UNIXTIME(timeQuerySent/1000) FROM queriesSent;