package nl.speakup.voice.voicebotdemo.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base class for events received from the Speakup voice API.
 */
public abstract class Event {
  private final ResourceReference resource;
  private final String type;

  @JsonCreator
  public Event(
      final @JsonProperty("resource") ResourceReference resource,
      final @JsonProperty("type") String type) {
    this.resource = resource;
    this.type = type;
  }

  /**
   * Each even is triggered by a resource. The resource reference can be used to identify the
   * resource, perform commands on it or determine the resource type.
   *
   * @return A reference to the resource that triggered the event.
   */
  public ResourceReference resource() {
    return resource;
  }

  /**
   * Each event has a type that is specific to the {@link Event#resource}.
   *
   * @return The event type.
   */
  public String type() {
    return type;
  }

  @Override
  public String toString() {
    return "Event{" +
        "resource=" + resource +
        ", type='" + type + '\'' +
        '}';
  }
}
