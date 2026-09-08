package net.shrine.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Goal for building the SHRINE MySQL image
 * More details on the below annotations are available here: https://maven.apache.org/developers/mojo-api-specification.html
 */
@Mojo( name = "pushShrineMySQL" )
@Execute( goal = "pushShrineMySQL",
        customPhase = "pushShrineMySQL"
)
public class PushShrineMySQLImageMojo extends AbstractMojo
{
    public void execute()
    {
        getLog().info("Pushed SHRINE MySQL Image...");
    }
}
