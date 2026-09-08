package net.shrine.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Goal for pushing the SHRINE node image
 * More details on the below annotations are available here: https://maven.apache.org/developers/mojo-api-specification.html
 */
@Mojo( name = "pushShrineNode" )
@Execute( goal = "pushShrineNode",
        customPhase = "pushShrineNode"
)
public class PushShrineNodeImageMojo extends AbstractMojo
{
    public void execute()
    {
        getLog().info("Pushed SHRINE Node Image...");
    }
}