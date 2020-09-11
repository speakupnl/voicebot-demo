package nl.speakup.voice.voicebotdemo.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import nl.speakup.voice.voicebotdemo.events.ChannelEvent;
import nl.speakup.voice.voicebotdemo.events.Event;
import nl.speakup.voice.voicebotdemo.events.PlaybackEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.Disposable;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

/**
 * A client component that provides access to the Speakup voice API. It performs the following
 * functions:
 *
 * <ul>
 *   <li>Listens for events on the Voice API event websocket and allows you to subscibe
 *     to those events from your code.</li>
 *   <li>Allows you to issue some of the commands supported for channels, playbacks and audio
 *     streams.</li>
 *   <li>Accepts the return audio when capturing an audio stream and exposes the result as a
 *     {@link Flux} for convenient processing.</li>
 * </ul>
 *
 * <p>Notes:</p>
 *
 * <ul>
 *   <li>this client currently only implements a subset of the API, however it is easily
 *     extendable by following the patterns.</li>
 *   <li>There is limited support for error handling at the moment.</li>
 *   <li>Channel hangups are not handled properly when a playback or audio stream was in
 *     progress at the time.</li>
 * </ul>
 */
public class VoiceApiClient implements RouterFunction<ServerResponse> {
  private static final Logger log = LoggerFactory.getLogger(VoiceApiClient.class);

  private final URI apiEndpoint;
  private final URI eventEndpoint;
  private final URI tokenEndpoint;
  private final URI callbackEndpoint;
  private final String applicationId;
  private final String applicationSecret;
  private final WebClient webClient;
  private final WebSocketClient websocketClient;
  private final ObjectMapper objectMapper;

  private final EmitterProcessor<WebsocketEvent> websocketEventEmitter = EmitterProcessor.create();
  private final FluxSink<WebsocketEvent> websocketEventSink = websocketEventEmitter.sink();

  private final EmitterProcessor<Event> eventEmitter = EmitterProcessor.create();
  private final FluxSink<Event> eventSink = eventEmitter.sink();

  private final EmitterProcessor<AudioFragment> audioEmitter = EmitterProcessor.create();
  final FluxSink<AudioFragment> audioSink = audioEmitter.sink();

  // Subscribe once on the audio emitter. It seems that an EmitterProcessor gets closed when the
  // last subscriber completes. By always keeping at least one no-op subscription open we prevent
  // the emitter from being closed altogether.
  // TODO: Find a better solution, keeping a subscription alive when there are no subscribers
  //  seems wasteful.
  private final Disposable audioSubscription = audioEmitter.subscribe();

  /**
   * Creates a new {@link VoiceApiClient}.
   *
   * @param apiEndpoint The URL of the main API REST endpoint.
   * @param eventEndpoint The URL of the API event websocket.
   * @param tokenEndpoint The OAuth2 token endpoint to use when authenticating.
   * @param callbackEndpoint The external URL on which this service is accessible to receive audio
   *                         fragments.
   * @param applicationId The OAuth2 application / client ID.
   * @param applicationSecret The OAuth2 application / client secret.
   * @param webClient The {@link WebClient} to use when connecting to REST endpoints.
   * @param websocketClient The WebSocketClient to use when connecting to the event websocket.
   * @param objectMapper The ObjectMapper to use for (de-)serializing requests and responses.
   */
  public VoiceApiClient(
      final URI apiEndpoint,
      final URI eventEndpoint,
      final URI tokenEndpoint,
      final URI callbackEndpoint,
      final String applicationId,
      final String applicationSecret,
      final WebClient webClient,
      final WebSocketClient websocketClient,
      final ObjectMapper objectMapper) {
    log.debug("Creating voice API client for " + apiEndpoint);

    this.apiEndpoint = Objects.requireNonNull(apiEndpoint, "apiEndpoint cannot be null");
    this.eventEndpoint = Objects.requireNonNull(eventEndpoint, "eventEndpoint cannot be null");
    this.tokenEndpoint = Objects.requireNonNull(tokenEndpoint, "tokenEndpoint cannot be null");
    this.callbackEndpoint = Objects.requireNonNull(callbackEndpoint, "callbackEndpoint cannot be null");
    this.applicationId = Objects.requireNonNull(applicationId, "applicationId cannot be null");
    this.applicationSecret = Objects.requireNonNull(applicationSecret, "applicationSecret cannot be null");
    this.webClient = webClient;
    this.websocketClient = Objects.requireNonNull(websocketClient, "websocketClient cannot be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");

    // Get the initial access tokens:
    final Tokens tokens = getTokens().block();

    log.debug("Using initial access token: " + tokens.accessToken());
    log.debug("Using initial refresh token: " + tokens.refreshToken());

    // Subscribe to events on the websocket event emitter. In response to a connect or reconnect
    // event a new websocket connection should be created:
    websocketEventEmitter.subscribe(this::createWebsocketSession);

    // Bootstrap the websocket connection by sending an initial "connect":
    websocketEventSink.next(WebsocketEvent.CONNECT);
  }

  /**
   * Creates a new audio playback resource on the given channel. This method only accepts
   * 16-bit PCM encoded audio data at 16 kHz in WAV format.
   *
   * @param id The unique ID of the playback. You provide the ID yourself and need to make sure its
   *           unique. Typically using a UUID will suffice.
   * @param channel The URL reference to the channel to perform a playback on.
   * @param content A {@link Resource} containing the audio data.
   * @return A mono that emits when the playback is successfully created (note: not when it
   *     completes).
   */
  public Mono<Void> createPlayback(final String id, final URI channel, final Resource content) {
    return getTokens()
        .flatMap(tokens -> webClient
            .put()
            .uri(createChannelUri(channel) + "/playbacks/" + id)
            .contentType(MediaType.parseMediaType("audio/wave"))
            .header("Authorization", "Bearer " + tokens.accessToken())
            .body(BodyInserters.fromResource(content))
            .exchange()
            .map(this::handleApiResponse)
            .then());
  }

  /**
   * Convenience method to perform a playback on the given channel and wait for the playback
   * to complete. Takes care of generating a unique ID and waiting for the complete event.
   * 
   * @param channel The URL reference of the channel to perform the playback on.
   * @param content The audio content to play.
   * @return A mono that emits when the audio playback completes successfully.
   * @see VoiceApiClient#createPlayback(String, URI, Resource)
   */
  public Mono<Void> playback(final URI channel, final Resource content) {
    final String id = UUID.randomUUID().toString();

    return createPlayback(id, channel, content)
        .then(eventEmitter
            .filter(event -> event instanceof PlaybackEvent)
            .map(event -> (PlaybackEvent) event)
            .filter(event -> event.resource().id().equals(id))
            .skipUntil(event -> event.state().lifecycle().equals("completed"))
            .next()
            .then());
  }

  /**
   * Creates a new audio stream, which will receive incoming audio fragments for the given channel.
   *
   * @param id The unique ID of the stream to create. You need to generate the ID yourself and make
   *           sure it is uinque. Typically a UUID will suffice and meet the uniqueness constraint.
   * @param channel An URL reference to the channel to "snoop" audio from.
   * @return A {@link Mono} that emits when the stream has been successfully created (note: at this
   *    point you may have already received some audio samples).
   */
  public Mono<Void> createStream(final String id, final URI channel) {
    final String target = callbackEndpoint.toString() + "/stream/" + id;

    log.debug("Creating audio callback stream: " + target);

    return getTokens()
        .flatMap(tokens -> webClient
            .put()
            .uri(createChannelUri(channel) + "/streams/" + id)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + tokens.accessToken())
            .body(BodyInserters.fromValue(new CreateStreamBody(target)))
            .exchange()
            .map(this::handleApiResponse)
            .then());
  }

  /**
   * Stops streaming audio on the given stream.
   *
   * @param id The ID of the stream to stop.
   * @param channel An URL reference to the audio channel that contains the stream.
   * @return A {@link Mono} that emits when the stop command has been accepted. Note that you may
   *     receive a few more audio samples after the stop command has been accepted.
   */
  public Mono<Void> stopStream(final String id, final URI channel) {
    final String target = callbackEndpoint.toString() + "/stream/" + id;

    log.debug("Destroying audio callback stream: " + target);

    return getTokens()
        .flatMap(tokens -> webClient
            .delete()
            .uri(createChannelUri(channel) + "/streams/" + id)
            .header("Authorization", "Bearer " + tokens.accessToken())
            .exchange()
            .map(this::handleApiResponse)
            .then());
  }

  /**
   * Combines {@link VoiceApiClient#createStream(String, URI)}
   * and {@link VoiceApiClient#stopStream(String, URI)} to "snoop audio" from the given channel.
   * An audio stream is created and returns a {@link Flux} that emits each audio fragment in
   * sequence. The audio stream is automatically terminated when the subscription on the returned
   * flux is terminated. Note that the "snoop" operation will receive audio indefinitely (or until
   * the channel completes) unless you explicitly terminate the subscription on the returned flux.
   *
   * @param channel A URL reference to the channel on which to snoop.
   * @return A {@link Flux} that emits audio samples that are received from the voice API.
   */
  public Flux<DataBuffer> snoop(final URI channel) {
    final String id = UUID.randomUUID().toString();

    return createStream(id, channel)
        .thenMany(audioEmitter
            .filter(fragment -> fragment.id().equals(id))
            .log()
            .map(AudioFragment::data)
            .doOnCancel(() -> {
              log.debug("Audio snoop " + id + " has been cancelled, stopping audio input.");
              stopStream(id, channel).subscribe();
            })
            .doOnTerminate(() -> {
              log.debug("Audio snoop " + id + " has terminated, stopping audio input.");
            }));
  }

  private String createChannelUri(final URI channel) {
    // Get the last segment from  the channel URI:
    final String channelString = channel.toString();
    int offset = channelString.lastIndexOf('/');
    final String channelId = channelString.substring(offset + 1);

    return apiEndpoint.toString().concat("/channels/" + channelId);
  }

  /**
   * Subscribe on this voice API client to receive events.
   *
   * @param eventHandler A {@link Consumer} that receives the event from the client.
   * @return A {@link Disposable} that can be used to terminate the event subscription.
   */
  public Disposable subscribe(final Consumer<Event> eventHandler) {
    return eventEmitter.subscribe(eventHandler);
  }

  private Boolean handleApiResponse(final ClientResponse response) {
    log.debug("Voice API response: " + response.statusCode());

    if (response.statusCode().is2xxSuccessful()) {
      return true;
    }

    throw new VoiceApiClientException("API call failed: " + response.statusCode());
  }

  private void createWebsocketSession(final WebsocketEvent event) {
    log.debug("Creating new websocket connection in response to " + event);

    // final WebSocketClient client = new ReactorNettyWebSocketClient();
    // final Mono<Void> sessionMono = client.execute(eventEndpoint, this::handleWebsocketSession);

    websocketClient
        .execute(eventEndpoint, this::handleWebsocketSession)
        .subscribe();
  }

  // https://stackoverflow.com/questions/48598295/how-to-reconnect-reactornettywebsocketclient-connection
  private Mono<Void> handleWebsocketSession(final WebSocketSession session) {
    // Handle websocket messages and publish them to the event emitter. That way, event
    // subscriptions will remain live, even if the websocket connection terminates and restarts.
    return session
        .send(session
            .receive()
            .doOnTerminate(() -> websocketEventSink.next(WebsocketEvent.RECONNECT))
            .map(WebSocketMessage::getPayloadAsText)
            //.log()
            .flatMap(this::handleWebsocketMessage)
            .map(session::textMessage)
        )
        .then();
  }

  private Flux<String> handleWebsocketMessage(final String message) {
    // Parse the text message as JSON:
    final JsonNode payload;
    try {
      payload = objectMapper.readTree(message);
    } catch (Exception e) {
      throw new VoiceApiClientException(e);
    }

    // Respond to authentication challenges:
    if ("authentication-challenge".equals(payload.path("type").asText())) {
      log.debug("Answering websocket authentication challenge");

      return getTokens()
          .map(this::buildAuthenticationChallengeResponse)
          .flux();
    }

    // All other messages are reported to the event sink:
    translateEvent(payload).ifPresent(eventSink::next);

    return Flux.empty();
  }

  private String buildAuthenticationChallengeResponse(final Tokens tokens) {
    final ObjectNode result = objectMapper.createObjectNode();

    result.put("command", "access-token");
    result.put("accessToken", tokens.accessToken());

    try {
      return objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new VoiceApiClientException(e);
    }
  }

  private Optional<Event> translateEvent(final JsonNode event) {
    try {
      return switch (event.path("resource").path("type").asText()) {
        case "channel" -> Optional.of(objectMapper.treeToValue(event, ChannelEvent.class));
        case "channel-audio-playback" ->
            Optional.of(objectMapper.treeToValue(event, PlaybackEvent.class));
        default -> Optional.empty();
      };
    } catch (JsonProcessingException e) {
      throw new VoiceApiClientException(e);
    }
  }

  private Mono<Tokens> getTokens() {
    final String credentials = applicationId + ":" + applicationSecret;
    final String encodedCredentials = Base64
        .getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

    return webClient
        .post()
        .uri(tokenEndpoint)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .header("Authorization", "Basic " + encodedCredentials)
        .bodyValue("grant_type=client_credentials")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .flatMap(response -> response.bodyToMono(ObjectNode.class))
        .map(VoiceApiClient::parseTokens);
  }

  private static Tokens parseTokens(final ObjectNode body) {
    if (!body.path("error").isMissingNode() || body.path("access_token").isMissingNode()) {
      throw new VoiceApiClientException("Failed retrieve access token: " + body.path("error_description").asText());
    }

    return new Tokens(body.path("access_token").asText(), body.path("refresh_token").asText());
  }

  @Override
  public String toString() {
    return "VoiceApiClient{" +
        "apiEndpoint=" + apiEndpoint +
        ", applicationId='" + applicationId + '\'' +
        '}';
  }

  @Override
  public Mono<HandlerFunction<ServerResponse>> route(final ServerRequest request) {
    return RouterFunctions.route()
        .PUT("/stream/{id}", RequestPredicates.accept(MediaType.TEXT_PLAIN),
            this::handleAudioStream)
        .build()
        .route(request);
  }

  private Mono<ServerResponse> handleAudioStream(final ServerRequest request) {
    final String id = request.pathVariable("id");

    return request
        .bodyToFlux(DataBuffer.class)
        .map(dataBuffer -> new AudioFragment(id, dataBuffer))
        .doOnNext(a -> log.debug("Received audio fragment"))
        .doOnNext(audioSink::next)
        .then(ServerResponse.ok().bodyValue(""));
  }

  private static class Tokens {
    private final String accessToken;
    private final String refreshToken;

    Tokens(final String accessToken, final String refreshToken) {
      this.accessToken = accessToken;
      this.refreshToken = refreshToken;
    }

    public String accessToken() {
      return accessToken;
    }

    public String refreshToken() {
      return refreshToken;
    }
  }

  private enum WebsocketEvent {
    CONNECT,
    RECONNECT
  }

  private static class ReconnectionEvent extends ApplicationEvent {

    /**
     * Create a new {@code ApplicationEvent}.
     *
     * @param source the object on which the event initially occurred or with which the event is
     *               associated (never {@code null})
     */
    public ReconnectionEvent(final Object source) {
      super(source);
    }
  }

  private static class CreateStreamBody {
    private final String target;

    CreateStreamBody(final String target) {
      this.target = target;
    }

    public String getTarget() {
      return target;
    }
  }

  private static class AudioFragment {
    private final String id;
    private final DataBuffer data;

    AudioFragment(final String id, final DataBuffer data) {
      this.id = id;
      this.data = data;
    }

    String id() {
      return id;
    }

    DataBuffer data() {
      return data;
    }
  }
}
