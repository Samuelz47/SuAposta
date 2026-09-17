package com.suaposta.betting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.suaposta.betting.application.port.out.BetEventPublisher;
import com.suaposta.betting.application.port.out.BetRepository;
import com.suaposta.betting.application.service.CreateBetService;
import com.suaposta.betting.application.service.GetBetService;
import com.suaposta.betting.application.service.ListBetsService;
import com.suaposta.betting.application.service.SettleBetService;
import com.suaposta.betting.application.service.UpdateBetService;
import com.suaposta.betting.domain.model.Bet;
import com.suaposta.betting.presentation.controller.BetController;
import com.suaposta.betting.presentation.exception.BetExceptionHandler;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CreateBetValidationControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Set<String> REQUIRED_FIELDS = Set.of(
            "sport", "league", "homeTeam", "awayTeam", "market", "selection",
            "odds", "stake", "placedAt");
    private static final Set<String> REQUIRED_TEXT_FIELDS = Set.of(
            "sport", "league", "homeTeam", "awayTeam", "market", "selection");

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var repository = mock(BetRepository.class);
        var eventPublisher = mock(BetEventPublisher.class);
        when(repository.save(any(Bet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var controller = new BetController(
                new CreateBetService(repository, eventPublisher),
                new ListBetsService(repository),
                new GetBetService(repository),
                new UpdateBetService(repository, Clock.systemUTC(), eventPublisher),
                new SettleBetService(repository, Clock.systemUTC(), eventPublisher));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new BetExceptionHandler())
                .build();
    }

    @Test
    void should_return_validation_error_for_request_without_required_fields() throws Exception {
        var request = BetTestSupport.JSON.createObjectNode();

        var result = mockMvc.perform(post("/bets")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertValidationError(result.getResponse().getContentAsString(), REQUIRED_FIELDS);
    }

    @ParameterizedTest(name = "null {0}")
    @MethodSource("requiredFields")
    void should_return_validation_error_when_required_field_is_null(String field) throws Exception {
        var request = validRequest();
        request.putNull(field);

        var result = mockMvc.perform(post("/bets")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertValidationError(result.getResponse().getContentAsString(), Set.of(field));
    }

    @ParameterizedTest(name = "blank {0} as {1}")
    @MethodSource("blankTextFields")
    void should_return_validation_error_when_required_text_field_is_blank(
            String field, String blankValue) throws Exception {
        var request = validRequest();
        request.put(field, blankValue);

        var result = mockMvc.perform(post("/bets")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertValidationError(result.getResponse().getContentAsString(), Set.of(field));
    }

    @Test
    void should_accept_valid_request_without_optional_notes() throws Exception {
        var request = validRequest();
        request.remove("notes");

        var result = mockMvc.perform(post("/bets")
                        .header("X-User-Id", USER_ID.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isCreated())
                .andReturn();

        var body = BetTestSupport.JSON.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("notes").isNull()).isTrue();
    }

    private static ObjectNode validRequest() {
        return BetTestSupport.validCreateRequest();
    }

    private static Stream<Arguments> requiredFields() {
        return REQUIRED_FIELDS.stream().map(Arguments::of);
    }

    private static Stream<Arguments> blankTextFields() {
        return REQUIRED_TEXT_FIELDS.stream()
                .flatMap(field -> Stream.of(
                        Arguments.of(field, ""),
                        Arguments.of(field, "   ")));
    }

    private static void assertValidationError(String responseBody, Set<String> expectedFields)
            throws Exception {
        JsonNode body = BetTestSupport.JSON.readTree(responseBody);

        assertThat(BetTestSupport.fieldNames(body)).containsExactlyInAnyOrder(
                "timestamp", "status", "error", "message", "path", "fieldErrors");
        assertThat(body.get("timestamp").isTextual()).isTrue();
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("error").asText()).isEqualTo("Validation Error");
        assertThat(body.get("message").asText()).isEqualTo("Invalid request fields");
        assertThat(body.get("path").asText()).isEqualTo("/bets");
        assertThat(body.get("fieldErrors").isArray()).isTrue();

        var fieldErrors = body.get("fieldErrors");
        var actualFields = java.util.stream.StreamSupport.stream(fieldErrors.spliterator(), false)
                .map(error -> {
                    assertThat(BetTestSupport.fieldNames(error))
                            .containsExactlyInAnyOrder("field", "message");
                    assertThat(error.get("message").asText()).isNotBlank();
                    return error.get("field").asText();
                })
                .collect(java.util.stream.Collectors.toSet());
        assertThat(actualFields).containsExactlyInAnyOrderElementsOf(expectedFields);
    }
}
