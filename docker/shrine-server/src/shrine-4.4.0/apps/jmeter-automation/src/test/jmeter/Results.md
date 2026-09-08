3.1 scale-up testing:

Tested using SHRINE2020_Scaleup_Node_1_configurable.jmx .

Simulated a surge of 120 queries in 60 seconds, with and without demographics. No systemic fatal failures, all results back, no duplicate results.

Time to process the surge was about 22 minutes with and without demographics. Without demographics all nodes but the QEP issuing the query were complete after about 18 minutes. With demographics completing the queries generally took longer at the node issuing the queries, the distinct boundary between it and other nodes was less stark.

During the surge polling response times rose to 8 seconds without demographics, 12 seconds with demographics. (This jmeter script simulates 120 users on one QEP, each polling the QEP every 6 seconds for new results. I'm not concerned about the sluggish response.)
