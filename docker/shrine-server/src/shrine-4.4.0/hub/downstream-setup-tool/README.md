Use this tool to configure some of the more exacting parts of downsteam nodes. Currently this tool supports adding 
permissions for a downsteam node to use the network's queues.

# Configuring the shrineDownstream tool:

Download and unzip the tool in your home directory on SHRINE's tomcat's server.

Do all the steps to modify SHRINE's shrine.conf file and database to be ready to use, but do not start tomcat with 
shrine-api.war installed.

cd to that unzipped directory.

Copy your shrine.conf from /opt/shrine/tomcat/lib/shrine.conf into conf

Modify conf/password.conf with your MOM credentials.

# Using the shrineDownstream tool:

`./shrineDownstream help`

`./shrineDownstream help setMomUserPolicy`

