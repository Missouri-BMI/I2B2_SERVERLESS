package net.shrine.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Goal for pushing the shrine i2b2 wildlfly image
 * More details on the below annotations are available here: https://maven.apache.org/developers/mojo-api-specification.html
 */
@Mojo( name = "pushShrineI2b2Wildfly" )
@Execute( goal = "pushShrineI2b2Wildfly",
        customPhase = "pushShrineI2b2Wildfly")
public class PushI2b2WildflyImageMojo extends AbstractMojo {

    public void execute()
    {
        getLog().info("Pushed SHRINE i2b2 Wildfly Image...");
    }
}

