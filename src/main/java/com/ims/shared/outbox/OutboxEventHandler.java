package com.ims.shared.outbox;

public interface OutboxEventHandler {
  /**
   * Handles a specific type of outbox event.
   * Implementation should be idempotent.
   */
  void handle(OutboxEvent event);

  /**
   * The type of event this handler can process (e.g. "ORDER_CREATED").
   */
  String getSupportedEventType();
}
