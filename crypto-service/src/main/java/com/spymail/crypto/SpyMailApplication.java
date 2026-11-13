package com.spymail.crypto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Entry point of the crypto service. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SpyMailApplication {

  public static void main(String[] args) {
    SpringApplication.run(SpyMailApplication.class, args);
  }
}
