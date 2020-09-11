package nl.speakup.voice.voicebotdemo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import nl.speakup.voice.voicebotdemo.services.VoiceApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;

/**
 * Configures components related to the Speakup Voice API.
 */
@Configuration
public class VoiceApiConfig {

  /**
   * Creates and configures a {@link WebSocketClient} for use by the {@link VoiceApiClient}.
   *
   * @return A new {@link WebSocketClient} instance.
   */
  @Bean
  public WebSocketClient websocketClient() {
    return new ReactorNettyWebSocketClient();
  }

  /**
   * Creates and configures a {@link WebClient} for use by the {@link VoiceApiClient}.
   */
  @Bean
  public WebClient webClient() {
    return WebClient.builder()
        .build();
  }

  /**
   * Creates and configures {@link VoiceApiClient} that provides access to the Speakup programmable
   * voice API.
   *
   * @param apiEndpoint The endpoint URL of the voice API.
   * @param eventEndpoint The endpoint URL of the event websocket of the voice API.
   * @param tokenEndpoint The OAuth2 token endpoint to use when authenticating.
   * @param externalUri The external URL on which this service is accessible, for inbound HTTP
   *                    connections.
   * @param applicationId The application / client ID to use for OAuth2 authentication.
   * @param applicationSecret The application / client secret to use for OAuth2 authentication.
   * @param webClient The {@link WebClient} to use when connecting to the API.
   * @param webSocketClient The {@link WebSocketClient} to use when connecting to the event
   *                        websocket.
   * @param objectMapper The {@link ObjectMapper} to use for (de-)serializing JSON from and to the
   *                     voice API.
   * @return A {@link VoiceApiClient} that is configured using the given settings.
   */
  @Bean
  public VoiceApiClient client(
      final @Value("${speakup.voice.api.api-endpoint}") String apiEndpoint,
      final @Value("${speakup.voice.api.event-endpoint}") String eventEndpoint,
      final @Value("${speakup.voice.api.token-endpoint}") String tokenEndpoint,
      final @Value("${speakup.voice.endpoint.external-uri}") String externalUri,
      final @Value("${speakup.voice.api.application-id}") String applicationId,
      final @Value("${speakup.voice.api.application-secret}") String applicationSecret,
      final WebClient webClient,
      final WebSocketClient webSocketClient,
      final ObjectMapper objectMapper
  ) {
    return new VoiceApiClient(
        URI.create(apiEndpoint),
        URI.create(eventEndpoint),
        URI.create(tokenEndpoint),
        URI.create(externalUri),
        applicationId,
        applicationSecret,
        webClient,
        webSocketClient,
        objectMapper
    );
  }
}
