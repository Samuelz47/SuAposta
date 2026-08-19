package com.suaposta.analytics.application.port.out;

import com.suaposta.analytics.application.model.ProcessedEvent;
import java.util.Optional;
import java.util.UUID;

public interface ProcessedEventRepository {

    Optional<ProcessedEvent> findByEventId(UUID eventId);

    ProcessedEvent claim(ProcessedEvent processedEvent);
}
