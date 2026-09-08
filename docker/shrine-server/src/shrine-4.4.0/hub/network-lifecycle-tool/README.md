Use this tool to set up the initial SHRINE network and nodes, and to add nodes to the network.

# Configuring the shrineLifecycle tool:

Download and unzip the tool in your home directory on SHRINE's tomcat's server.

Do all the steps to modify SHRINE's shrine.conf file and database to be ready to use, but do not start tomcat with shrine-api.war installed.

cd to that unzipped directory.

Copy your database driver .jar into lib/ . (This tool includes a mysql driver.)

If your shrine.conf is not at /opt/shrine/tomcat/lib/shrine.conf, export SHRINE_CONF=/path/to/your/shrine.conf

Modify conf/override.conf with your database specifics. (This tool does not use tomcat's database pool.)

Modify conf/password.conf with your MOM bonafides.

# Using the shrineLifecycle tool:

`./shrineLifecycle help`

`./shrineLifecycle help createNetwork`

After creating the network your SHRINE database and MOM queues will be ready to use.