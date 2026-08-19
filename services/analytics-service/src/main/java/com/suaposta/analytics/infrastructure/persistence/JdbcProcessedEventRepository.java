package com.suaposta.analytics.infrastructure.persistence;

import com.suaposta.analytics.application.model.ProcessedEvent;
import com.suaposta.analytics.application.port.out.ProcessedEventRepository;
import com.suaposta.messaging.contract.EventType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcProcessedEventRepository implements ProcessedEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcProcessedEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ProcessedEvent> findByEventId(UUID eventId) {
        return jdbcTemplate.query(
                        "select event_id, event_type, processed_at from processed_events where event_id = ?",
                        (resultSet, ignoredRowNumber) -> new ProcessedEvent(
                                resultSet.getObject("event_id", UUID.class),
                                EventType.valueOf(resultSet.getString("event_type")),
                                resultSet.getObject("processed_at", OffsetDateTime.class).toInstant()),
                        eventId)
                .stream()
                .findFirst();
    }

    @Override
    public ProcessedEvent claim(ProcessedEvent processedEvent) {
        var inserted = jdbcTemplate.update("""
                        insert into processed_events(event_id, event_type, processed_at)
                        values (?, ?, ?)
                        on conflict (event_id) do nothing
                        """,
                processedEvent.eventId(), processedEvent.eventType().name(),
                processedEvent.processedAt().atOffset(ZoneOffset.UTC));
        return inserted == 1 ? processedEvent : null;
    }
}
