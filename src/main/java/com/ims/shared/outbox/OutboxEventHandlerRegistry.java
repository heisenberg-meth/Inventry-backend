package com.ims.shared.outbox;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventHandlerRegistry {

  private final Map<String, OutboxEventHandler> handlers;

  public OutboxEventHandlerRegistry(List<OutboxEventHandler> handlerList) {
    this.handlers = handlerList.stream()
        .collect(Collectors.toMap(OutboxEventHandler::getSupportedEventType, Function.identity()));
  }

  public Optional<OutboxEventHandler> getHandler(String eventType) {
    return Optional.ofNullable(handlers.get(eventType));
  }
}
