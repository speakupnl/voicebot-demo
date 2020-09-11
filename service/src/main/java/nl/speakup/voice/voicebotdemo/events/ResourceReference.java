package nl.speakup.voice.voicebotdemo.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Describes a reference to an API resource.
 */
public final class ResourceReference {
  private final String type;
  private final String id;
  private final String reference;

  @JsonCreator
  public ResourceReference(
      final @JsonProperty("type") String type,
      final @JsonProperty("id") String id,
      final @JsonProperty("reference") String reference
  ) {
    this.type = type;
    this.id = id;
    this.reference = reference;
  }

  /**
   * Each resource belongs to a type. The type can typically be derived from the URL reference as
   * well, but it is added as a separate attribute for convenience. Example resources are
   * "channel" or "playback".
   *
   * @return The resource type.
   */
  public String type() {
    return type;
  }

  /**
   * Each resource has a unique ID. The ID can typically be derived from the URL reference as
   * wll, but it is added as a separate attribute for concenience.
   *
   * @return
   */
  public String id() {
    return id;
  }

  /**
   * Each resource is identified by a URL. This is also the URL that accepts API commands for that
   * resource. For example: if you have a {@link ResourceReference} that points to a channel,
   * performing a DELETE request on the reference URL will hang up / complete the channel.
   *
   * @return The URL reference to the resource.
   */
  public String reference() {
    return reference;
  }

  @Override
  public String toString() {
    return "ResourceReference{" +
        "type='" + type + '\'' +
        ", id='" + id + '\'' +
        ", reference='" + reference + '\'' +
        '}';
  }
}
