package com.spymail.crypto.nats;

import com.spymail.crypto.config.SpyMailProperties;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the connection to the NATS server. */
@Configuration
class NatsConfig {

  @Bean(destroyMethod = "close")
  Connection natsConnection(SpyMailProperties properties)
      throws IOException, InterruptedException {
    Options options =
        new Options.Builder()
            .server(properties.natsUrl())
            .connectionName("crypto-service")
            .maxReconnects(-1)
            .build();
    return Nats.connect(options);
  }
}
