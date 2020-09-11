package nl.speakup.voice.voicebotdemo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import nl.speakup.voice.voicebotdemo.services.NluService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for the Rasa NLU component.
 */
@Configuration
public class NluConfig {

  /**
   * Creates a Rasa NLU client service with the given configuration.
   *
   * @param endpoint The URL of the Rasa endpoint.
   * @param webClient The Spring WebClient to use when making connection.
   * @param objectMapper An {@link ObjectMapper} used for JSON (de-)serilization by the service.
   * @return A Rasa NLU client.
   */
  @Bean
  public NluService nluService(
      final @Value("${nlu.endpoint}") String endpoint,
      final WebClient webClient,
      final ObjectMapper objectMapper) {
    return new NluService(URI.create(endpoint), webClient, objectMapper);
  }
}
