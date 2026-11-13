package com.spymail.crypto.persistence;

import com.spymail.crypto.crypto.EncryptedMessage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Database access for encrypted messages. */
@Repository
public class MessageRepository {

  private final JdbcClient jdbc;

  public MessageRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(UUID id, EncryptedMessage message) {
    jdbc.sql("INSERT INTO message (id, iv, ciphertext) VALUES (:id, :iv, :ciphertext)")
        .param("id", id)
        .param("iv", message.iv())
        .param("ciphertext", message.ciphertext())
        .update();
  }

  /**
   * Loads a message and locks its row until the current transaction ends.
   *
   * <p>A second transaction asking for the same row waits here. If the first one deleted the
   * row, the second one finds nothing. If the first one raised the try counter, the second one
   * sees the new value. This is what keeps both service instances consistent.
   */
  public Optional<StoredMessage> findByIdForUpdate(UUID id) {
    return jdbc.sql(
            "SELECT id, iv, ciphertext, failed_attempts FROM message WHERE id = :id FOR UPDATE")
        .param("id", id)
        .query(
            (rs, rowNum) ->
                new StoredMessage(
                    rs.getObject("id", UUID.class),
                    new EncryptedMessage(rs.getBytes("iv"), rs.getBytes("ciphertext")),
                    rs.getInt("failed_attempts")))
        .optional();
  }

  public void updateFailedAttempts(UUID id, int failedAttempts) {
    jdbc.sql("UPDATE message SET failed_attempts = :failedAttempts WHERE id = :id")
        .param("id", id)
        .param("failedAttempts", failedAttempts)
        .update();
  }

  public void delete(UUID id) {
    jdbc.sql("DELETE FROM message WHERE id = :id").param("id", id).update();
  }
}
