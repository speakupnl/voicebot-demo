package nl.speakup.voice.voicebotdemo.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base class for events that are reported on a channel resource.
 */
public final class ChannelEvent extends Event {

  @JsonCreator
  public ChannelEvent(
      final @JsonProperty("resource") ResourceReference resource,
      final @JsonProperty("type") String type) {
    super(resource, type);
  }
}
