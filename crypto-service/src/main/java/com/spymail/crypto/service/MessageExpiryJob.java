package com.spymail.crypto.service;

import com.spymail.crypto.config.SpyMailProperties;
import com.spymail.crypto.persistence.MessageRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes messages older than {@code MAX_AGE}.
 *
 * <p>The check in {@link MessageService#receive} already hides an expired message from a reader,
 * even before this job runs. This job only reclaims the storage of messages nobody ever read.
 */
@Component
public class MessageExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(MessageExpiryJob.class);

  private final MessageRepository repository;
  private final SpyMailProperties properties;

  public MessageExpiryJob(MessageRepository repository, SpyMailProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
  void deleteExpiredMessages() {
    int deleted = repository.deleteOlderThan(Instant.now().minus(properties.maxAge()));
    if (deleted > 0) {
      log.info("Deleted {} expired message(s)", deleted);
    }
  }
}
