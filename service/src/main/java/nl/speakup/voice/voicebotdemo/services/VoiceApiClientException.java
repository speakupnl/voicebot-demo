package nl.speakup.voice.voicebotdemo.services;

/**
 * Exception thrown from the {@link VoiceApiClient}, or returned through
 * {@link reactor.core.publisher.Mono}s or {@link reactor.core.publisher.Flux}s in their failed
 * state.
 */
public class VoiceApiClientException extends RuntimeException {
  public VoiceApiClientException(final String message) {
    super(message);
  }

  public VoiceApiClientException(final Throwable cause) {
    super(cause);
  }
}
