package net.shrine.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Goal for pushing the shrine crts image
 * More details on the below annotations are available here: https://maven.apache.org/developers/mojo-api-specification.html
 */
@Mojo( name = "pushShrineCerts" )
@Execute( goal = "pushShrineCerts",
        customPhase = "pushShrineCerts"
)
public class PushShrineCertsImageMojo extends AbstractMojo
{
    public void execute()
    {
        getLog().info("Pushed SHRINE Certs Image...");
    }
}