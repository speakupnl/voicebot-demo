package nl.speakup.voice.voicebotdemo.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * A small client for convenience when accessing a Rasa NLU service.
 */
public class NluService {
  private static final Logger log = LoggerFactory.getLogger(NluService.class);

  private final URI endpoint;
  private final WebClient webClient;
  private final ObjectMapper objectMapper;

  /**
   * Creates a new {@link NluService}.
   *
   * @param endpoint The base URI of the Rasa NLY endpoint.
   * @param webClient The {@link WebClient} to use when connecting to Rasa.
   * @param objectMapper An {@link ObjectMapper} to use for (de-)serializing the JSON bodies that
   *                     are sent to and received from the Rasa endpoints.
   */
  public NluService(
      final URI endpoint,
      final WebClient webClient,
      final ObjectMapper objectMapper) {
    this.endpoint = Objects.requireNonNull(endpoint, "endpoint cannot be null");
    this.webClient = Objects.requireNonNull(webClient, "webClient cannot be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
  }

  /**
   * Invokes the Rasa NLU endpoint to determine the intent of the given utterance.
   *
   * @param utterance The utterance for which to determine the intent.
   * @return A {@link Mono} that emits the intent of the given utterance when it completes
   *     successfully.
   */
  public Mono<String> determineIntent(final String utterance) {
    final ObjectNode body = objectMapper.createObjectNode();

    body.put("text", utterance);

    return webClient
        .post()
        .uri(endpoint)
        .contentType(MediaType.APPLICATION_JSON)
        .body(BodyInserters.fromValue(body))
        .exchange()
        .flatMap(response -> response.bodyToMono(JsonNode.class))
        .doOnNext(node -> log.debug("Intent parse result: " + node))
        .map(node -> node.path("intent").path("name").asText());
  }

}
