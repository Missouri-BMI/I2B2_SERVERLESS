package net.shrine.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Goal for pushing kafka image
 * More details on the below annotations are available here: https://maven.apache.org/developers/mojo-api-specification.html
 */
@Mojo( name = "pushKafka" )
@Execute( goal = "pushKafka",
        customPhase = "pushKafka"
)
public class PushKafkaImageMojo extends AbstractMojo {

    public void execute()
    {
        getLog().info("Pushed Kafka Image...");
    }
}
