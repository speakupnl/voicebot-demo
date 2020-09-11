package nl.speakup.voice.voicebotdemo.config;

import com.google.api.gax.core.FixedExecutorProvider;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechSettings;
import java.io.IOException;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures a Google Speech API client.
 */
@Configuration
public class SpeechConfig {

  /**
   * Creates a SpeechClient for the Google speech APIs.
   *
   * @return A new {@link SpeechClient} instance.
   * @throws IOException
   */
  @Bean
  public SpeechClient speechClient() throws IOException {
    // Provide an executor explicitly, due to:
    // https://github.com/googleapis/google-cloud-java/issues/4727
    return SpeechClient.create(SpeechSettings
        .newBuilder()
        .setExecutorProvider(FixedExecutorProvider.create(
            Executors.newScheduledThreadPool(
                Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, 4))
            )
        ))
        .build());
  }
}
