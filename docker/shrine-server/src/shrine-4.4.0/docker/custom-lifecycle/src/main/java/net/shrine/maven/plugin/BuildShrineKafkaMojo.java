package net.shrine.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Goal for building the Kafka image
 * More details on the below annotations are available here: https://maven.apache.org/developers/mojo-api-specification.html
 */
@Mojo( name = "buildKafka" )
@Execute( goal = "buildKafka",
        customPhase = "buildKafka")
public class BuildShrineKafkaMojo extends AbstractMojo
{
    public void execute()
    {
        getLog().info("Built Kafka Images...");
    }
}
