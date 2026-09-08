package net.shrine.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Goal for start the SHRINE network
 * More details on the below annotations are available here: https://maven.apache.org/developers/mojo-api-specification.html
 */
@Mojo( name = "startNetwork" )
@Execute( goal = "startNetwork",
        customPhase = "startNetwork"
)
public class StartNetworkMojo extends AbstractMojo {

    public void execute()
    {
        getLog().info("Started the SHRINE Network...");
    }
}

