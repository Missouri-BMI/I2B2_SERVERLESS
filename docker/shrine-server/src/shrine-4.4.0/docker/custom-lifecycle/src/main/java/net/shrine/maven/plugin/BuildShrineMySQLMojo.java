package net.shrine.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Goal for building the SHRINE MySQL image
 * More details on the below annotations are available here: https://maven.apache.org/developers/mojo-api-specification.html
 */
@Mojo( name = "buildShrineMySQL" )
@Execute( goal = "buildShrineMySQL",
        customPhase = "buildShrineMySQL")
public class BuildShrineMySQLMojo extends AbstractMojo
{
    public void execute()
    {
        getLog().info("Built SHRINE MySQL Image...");
    }
}
