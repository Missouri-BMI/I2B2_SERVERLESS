package net.shrine.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Goal for building the shrine crts image
 * More details on the below annotations are available here: https://maven.apache.org/developers/mojo-api-specification.html
 */
@Mojo( name = "buildShrineCerts" )
@Execute( goal = "buildShrineCerts",
        customPhase = "buildShrineCerts"
)

public class BuildShrineCertsMojo extends AbstractMojo
{
    public void execute()
    {
        getLog().info("Built SHRINE Certs Image...");
    }
}