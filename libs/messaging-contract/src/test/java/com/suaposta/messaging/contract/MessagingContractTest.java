package com.suaposta.messaging.contract;

import static com.suaposta.messaging.contract.MessagingContractTestSupport.BET_ID;
import static com.suaposta.messaging.contract.MessagingContractTestSupport.EVENT_ID;
import static com.suaposta.messaging.contract.MessagingContractTestSupport.JSON;
import static com.suaposta.messaging.contract.MessagingContractTestSupport.OCCURRED_AT;
import static com.suaposta.messaging.contract.MessagingContractTestSupport.PLACED_AT;
import static com.suaposta.messaging.contract.MessagingContractTestSupport.SETTLED_AT;
import static com.suaposta.messaging.contract.MessagingContractTestSupport.UPDATED_AT;
import static com.suaposta.messaging.contract.MessagingContractTestSupport.USER_ID;
import static com.suaposta.messaging.contract.MessagingContractTestSupport.assertDecimal;
import static com.suaposta.messaging.contract.MessagingContractTestSupport.assertExactFields;
import static com.suaposta.messaging.contract.MessagingContractTestSupport.assertPropertyType;
import static com.suaposta.messaging.contract.MessagingContractTestSupport.envelopeJson;
import static com.suaposta.messaging.contract.MessagingContractTestSupport.publicConstants;
import static com.suaposta.messaging.contract.MessagingContractTestSupport.roundTrip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessagingContractTest {

    private static final String CREATED_PAYLOAD = """
            {
              "betId": "%s",
              "userId": "%s",
              "sport": "FOOTBALL",
              "league": "Brasileirão Série A",
              "homeTeam": "Fortaleza",
              "awayTeam": "Bahia",
              "market": "MATCH_RESULT",
              "selection": "Fortaleza",
              "odds": 2.1256,
              "stake": 120.13,
              "status": "PENDING",
              "placedAt": "%s"
            }
            """.formatted(BET_ID, USER_ID, PLACED_AT);

    @Test
    void should_expose_the_stable_version_one_messaging_constants() {
        var expected = Set.<Object>of(
                "betting.events",
                "analytics.betting-events.queue",
                "bet.created",
                "bet.updated",
                "bet.settled",
                "betting-service",
                1);
        var constants = publicConstants(MessagingContractTestSupport.productionTypeContainingConstants(expected));

        assertThat(constants).containsAll(expected);
    }

    @Test
    void should_round_trip_a_valid_version_one_envelope_with_only_documented_fields() throws Exception {
        var envelopeType = envelopeType();
        var json = roundTrip(envelopeJson(
                "BET_CREATED", 1, "betting-service", EVENT_ID.toString(), OCCURRED_AT.toString(), CREATED_PAYLOAD),
                envelopeType);

        assertExactFields(json, "eventId", "eventType", "occurredAt", "version", "producer", "payload");
        assertThat(UUID.fromString(json.path("eventId").asText())).isEqualTo(EVENT_ID);
        assertThat(json.path("eventType").asText()).isEqualTo("BET_CREATED");
        assertThat(Instant.parse(json.path("occurredAt").asText())).isEqualTo(OCCURRED_AT);
        assertThat(json.path("version").asInt()).isEqualTo(1);
        assertThat(json.path("producer").asText()).isEqualTo("betting-service");
        assertThat(json.path("payload").isObject()).isTrue();
        assertPropertyType(envelopeType, "eventId", UUID.class);
        assertPropertyType(envelopeType, "occurredAt", Instant.class);
        assertThat(MessagingContractTestSupport.propertyType(envelopeType, "payload")).isNotEqualTo(String.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEnvelopes")
    void should_reject_invalid_version_one_envelopes(String scenario, String source) {
        var envelopeType = envelopeType();

        assertThatThrownBy(() -> JSON.readValue(source, envelopeType))
                .as(scenario)
                .isInstanceOf(Exception.class);
    }

    static Stream<Arguments> invalidEnvelopes() {
        return Stream.of(
                Arguments.of("null eventId", envelopeJson(
                        "BET_CREATED", 1, "betting-service", null, OCCURRED_AT.toString(), CREATED_PAYLOAD)),
                Arguments.of("null eventType", envelopeJson(
                        null, 1, "betting-service", EVENT_ID.toString(), OCCURRED_AT.toString(), CREATED_PAYLOAD)),
                Arguments.of("unknown eventType", envelopeJson(
                        "BET_DELETED", 1, "betting-service", EVENT_ID.toString(), OCCURRED_AT.toString(), CREATED_PAYLOAD)),
                Arguments.of("null occurredAt", envelopeJson(
                        "BET_CREATED", 1, "betting-service", EVENT_ID.toString(), null, CREATED_PAYLOAD)),
                Arguments.of("null payload", envelopeJson(
                        "BET_CREATED", 1, "betting-service", EVENT_ID.toString(), OCCURRED_AT.toString(), "null")),
                Arguments.of("unsupported version", envelopeJson(
                        "BET_CREATED", 2, "betting-service", EVENT_ID.toString(), OCCURRED_AT.toString(), CREATED_PAYLOAD)),
                Arguments.of("invalid producer", envelopeJson(
                        "BET_CREATED", 1, "another-service", EVENT_ID.toString(), OCCURRED_AT.toString(), CREATED_PAYLOAD)));
    }

    @Test
    void should_round_trip_the_created_payload_with_uuid_instant_and_exact_decimals() throws Exception {
        var type = MessagingContractTestSupport.productionTypeWithExactProperties(
                "BET_CREATED payload", "betId", "userId", "sport", "league", "homeTeam", "awayTeam",
                "market", "selection", "odds", "stake", "status", "placedAt");
        var json = roundTrip(CREATED_PAYLOAD, type);

        assertExactFields(json, "betId", "userId", "sport", "league", "homeTeam", "awayTeam", "market",
                "selection", "odds", "stake", "status", "placedAt");
        assertCommonSnapshot(json, type);
        assertDecimal(json, "odds", "2.1256");
        assertDecimal(json, "stake", "120.13");
        assertThat(Instant.parse(json.path("placedAt").asText())).isEqualTo(PLACED_AT);
        assertPropertyType(type, "odds", BigDecimal.class);
        assertPropertyType(type, "stake", BigDecimal.class);
        assertPropertyType(type, "placedAt", Instant.class);
    }

    @Test
    void should_round_trip_the_updated_payload_with_uuid_instants_and_exact_decimals() throws Exception {
        var type = MessagingContractTestSupport.productionTypeWithExactProperties(
                "BET_UPDATED payload", "betId", "userId", "sport", "league", "homeTeam", "awayTeam",
                "market", "selection", "odds", "stake", "status", "placedAt", "updatedAt");
        var source = JSON.readTree(CREATED_PAYLOAD).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) source).put("updatedAt", UPDATED_AT.toString());
        var json = roundTrip(source.toString(), type);

        assertExactFields(json, "betId", "userId", "sport", "league", "homeTeam", "awayTeam", "market",
                "selection", "odds", "stake", "status", "placedAt", "updatedAt");
        assertCommonSnapshot(json, type);
        assertDecimal(json, "odds", "2.1256");
        assertDecimal(json, "stake", "120.13");
        assertThat(Instant.parse(json.path("placedAt").asText())).isEqualTo(PLACED_AT);
        assertThat(Instant.parse(json.path("updatedAt").asText())).isEqualTo(UPDATED_AT);
        assertPropertyType(type, "odds", BigDecimal.class);
        assertPropertyType(type, "stake", BigDecimal.class);
        assertPropertyType(type, "placedAt", Instant.class);
        assertPropertyType(type, "updatedAt", Instant.class);
    }

    @Test
    void should_round_trip_the_settled_payload_without_recalculating_financial_values() throws Exception {
        var type = MessagingContractTestSupport.productionTypeWithExactProperties(
                "BET_SETTLED payload", "betId", "userId", "status", "odds", "stake", "profit",
                "returnAmount", "settledAt");
        var source = """
                {
                  "betId": "%s",
                  "userId": "%s",
                  "status": "WON",
                  "odds": 2.1256,
                  "stake": 120.13,
                  "profit": -19.87,
                  "returnAmount": 80.13,
                  "settledAt": "%s"
                }
                """.formatted(BET_ID, USER_ID, SETTLED_AT);
        var json = roundTrip(source, type);

        assertExactFields(json, "betId", "userId", "status", "odds", "stake", "profit", "returnAmount",
                "settledAt");
        assertThat(UUID.fromString(json.path("betId").asText())).isEqualTo(BET_ID);
        assertThat(UUID.fromString(json.path("userId").asText())).isEqualTo(USER_ID);
        assertThat(json.path("status").asText()).isEqualTo("WON");
        assertDecimal(json, "odds", "2.1256");
        assertDecimal(json, "stake", "120.13");
        assertDecimal(json, "profit", "-19.87");
        assertDecimal(json, "returnAmount", "80.13");
        assertThat(Instant.parse(json.path("settledAt").asText())).isEqualTo(SETTLED_AT);
        assertPropertyType(type, "betId", UUID.class);
        assertPropertyType(type, "userId", UUID.class);
        assertPropertyType(type, "odds", BigDecimal.class);
        assertPropertyType(type, "stake", BigDecimal.class);
        assertPropertyType(type, "profit", BigDecimal.class);
        assertPropertyType(type, "returnAmount", BigDecimal.class);
        assertPropertyType(type, "settledAt", Instant.class);
    }

    @Test
    void should_fail_safely_when_event_json_is_malformed() {
        var envelopeType = envelopeType();

        assertThatThrownBy(() -> JSON.readValue("{\"eventId\":", envelopeType))
                .isInstanceOf(Exception.class);
    }

    @Test
    void should_limit_event_types_to_the_three_initial_values() {
        var eventType = MessagingContractTestSupport.propertyType(envelopeType(), "eventType");
        var candidates = eventType.isEnum()
                ? Stream.of(eventType.getEnumConstants())
                : publicConstants(eventType).stream();
        var values = candidates.map(value -> JSON.valueToTree(value).asText())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(values).containsExactlyInAnyOrder("BET_CREATED", "BET_UPDATED", "BET_SETTLED");
    }

    private static void assertCommonSnapshot(com.fasterxml.jackson.databind.JsonNode json, Class<?> type) {
        assertThat(UUID.fromString(json.path("betId").asText())).isEqualTo(BET_ID);
        assertThat(UUID.fromString(json.path("userId").asText())).isEqualTo(USER_ID);
        assertThat(json.path("status").asText()).isEqualTo("PENDING");
        assertPropertyType(type, "betId", UUID.class);
        assertPropertyType(type, "userId", UUID.class);
    }

    private static Class<?> envelopeType() {
        return MessagingContractTestSupport.productionTypeWithExactProperties(
                "version-one envelope", "eventId", "eventType", "occurredAt", "version", "producer", "payload");
    }
}
