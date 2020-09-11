package nl.speakup.voice.voicebotdemo.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base class for events that are reported on an audio playback resource.
 */
public final class PlaybackEvent extends Event{

  private final PlaybackState state;

  @JsonCreator
  public PlaybackEvent(
      final @JsonProperty("resource") ResourceReference resource,
      final @JsonProperty("type") String type,
      final @JsonProperty("state") PlaybackState state) {
    super(resource, type);

    this.state = state;
  }

  /**
   * Each playback has a "state" that describes various aspects of the playback such as the current
   * step in the lifecycle (created, completed, etc.). Each playback event contains a full copy of
   * the state at the time the event was raised. You will receive an event for each change to the
   * playback state.
   *
   * @return The current state of the playback.
   */
  public PlaybackState state() {
    return state;
  }

  @Override
  public String toString() {
    return "PlaybackEvent{" +
        "state=" + state +
        '}';
  }
}
