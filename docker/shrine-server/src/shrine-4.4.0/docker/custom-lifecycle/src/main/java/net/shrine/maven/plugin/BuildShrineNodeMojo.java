package net.shrine.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Goal for building the SHRINE node image
 * More details on the below annotations are available here: https://maven.apache.org/developers/mojo-api-specification.html
 */
@Mojo( name = "buildShrineNode" )
@Execute( goal = "buildShrineNode",
        customPhase = "buildShrineNode"
)
public class BuildShrineNodeMojo extends AbstractMojo
{
    public void execute()
    {
        getLog().info("Built SHRINE Node Image...");
    }
}
