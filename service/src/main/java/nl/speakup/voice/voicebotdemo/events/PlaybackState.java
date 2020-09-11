package nl.speakup.voice.voicebotdemo.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes the state of the playback.
 */
public final class PlaybackState {

  private final String lifecycle;

  @JsonCreator
  public PlaybackState(
      final @JsonProperty("lifecycle") String lifecycle
  ) {
    this.lifecycle = lifecycle;
  }

  /**
   * A playback cycles through the following lifecycle steps: created, playing, completing,
   * completed. Always in that order, but the playing or completing steps may be skipped.
   *
   * @return The current lifecycle step of the playback.
   */
  public String lifecycle() {
    return lifecycle;
  }

  @Override
  public String toString() {
    return "PlaybackState{" +
        "lifecycle='" + lifecycle + '\'' +
        '}';
  }
}
