package nl.speakup.voice.voicebotdemo.services;

import java.net.URI;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.util.Objects;
import nl.speakup.voice.voicebotdemo.events.ChannelEvent;
import nl.speakup.voice.voicebotdemo.events.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * The voice bot service. This is where the interaction between the caller and the bot/NLU system is
 * handled. Listens for events on the voice API and issues commands to the
 * voice API as well as the Google speech API and the Rasa NLU API. By combining these APIs complex
 * speech-driven interactions can be realized.
 */
@Service
public class Bot {
  private static final Logger log = LoggerFactory.getLogger(Bot.class);

  private final VoiceApiClient voiceClient;
  private final SpeechService speechService;
  private final NluService nluService;

  /**
   * Creates a new Bot.
   *
   * @param voiceClient The Speakup voice API client to use for this bot.
   * @param speechService The Google speech client to use for this bot.
   * @param nluService The Rasa NLU service to use for this bot.
   */
  public Bot(
      final VoiceApiClient voiceClient,
      final SpeechService speechService,
      final NluService nluService) {
    this.voiceClient = Objects.requireNonNull(voiceClient, "voiceClient cannot be null");
    this.speechService = Objects.requireNonNull(speechService, "speechService cannot be null");
    this.nluService = Objects.requireNonNull(nluService, "nluService cannot be null");

    // Subscribe to voice events:
    voiceClient.subscribe(this::eventHandler);
  }

  private void eventHandler(final Event event) {
    log.debug("Receiving voice event: " + event);

    if (event instanceof ChannelEvent) {
      handleChannelEvent((ChannelEvent) event);
    }
  }

  private void handleChannelEvent(final ChannelEvent event) {
    if ("created".equals(event.type())) {
      log.debug("Audio channel created, performing playback");

      final URI channel = URI.create(event.resource().reference());

      voiceClient
          .playback(channel, speechService.say(opening() + ", ik ben Sia. De digitale assistente van Speakup."))
          .then(voiceClient
              .playback(channel, speechService.say("Waar kan ik je mee helpen?"))
              .then(speechService.transcribeUtterance(voiceClient.snoop(channel))
                  .doOnNext(utterance -> log.debug("Utterance received: " + utterance))
                  .flatMap(nluService::determineIntent)
                  .doOnNext(intent -> log.debug("Intent: " + intent))
                  .flatMap(this::response)
                  .flatMap(response -> voiceClient
                      .playback(channel, speechService.say(response))))
          )
          .onErrorContinue((e, value) -> log.error("Playback failed: " + e))
          .doOnTerminate(() -> log.debug("Playbacks have completed"))
          .subscribe();
    }
  }

  private String opening() {
    OffsetTime now = OffsetTime.now(ZoneId.of("Europe/Amsterdam"));

    if (now.getHour() < 12) {
      return "Goedemorgen";
    } else if (now.getHour() < 18) {
      return "Goedemiddag";
    } else {
      return "Goedenavond";
    }
  }

  private Mono<String> response(final String intent) {
    return Mono.just(switch (intent) {
      case "inquire_outage" ->
          "Er zijn op dit moment geen actieve storingen bij ons bekend. Ik verbind je door met een collega om je verder te helpen. Een moment geduld alsjeblieft.";
      case "inquire_invoice" ->
          "Ik verbind je door met een collega van finance die je verder kan helpen met vragen over de factuur. Een moment geduld alsjeblieft.";
      case "human_handover" ->
          "Ik verbind je door met een collega die je verder kan helpen.";
      default ->
          "Ik heb je niet helemaal begrepen, ik verbind je door met een collega die je verder kan helpen. Een moment geduld alsjeblieft.";
    });
  }
}
