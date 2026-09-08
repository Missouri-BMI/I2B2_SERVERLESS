package net.shrine.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Goal for building the shrine i2b2 wildfly image
 * Forks a parallel process that runs tasks in the buildShrineI2b2Wildfly phase
 * More details on the below annotations are available here: https://maven.apache.org/developers/mojo-api-specification.html
 */
@Mojo( name = "buildShrineI2b2Wildfly", requiresProject = false )
@Execute( goal = "buildShrineI2b2Wildfly",
        customPhase = "buildShrineI2b2Wildfly"
)
public class BuildI2b2WildflyMojo extends AbstractMojo {

    public void execute()
    {
        getLog().info("Built SHRINE i2b2 Wildfly Image...");
    }
}

