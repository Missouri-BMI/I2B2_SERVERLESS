@echo off

set CONFIG_HOME=.

rem Set up the classpath
call %CONFIG_HOME%\setup-classpath.cmd

@set JVM_ARGS=-Xmx1024m -Xms128m

@java %JVM_ARGS% -classpath %CONFIG_CP% net.shrine.hub.metrics.NetworkMetrics %*