package nl.speakup.voice.voicebotdemo.services;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.SynthesisInput;
import com.google.cloud.texttospeech.v1.SynthesizeSpeechResponse;
import com.google.cloud.texttospeech.v1.TextToSpeechClient;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A small service that provides convenience methods when accessing the Google Speech APIS
 * (text-to-speech and speech-to-text). Especially deals with translating API input and responses
 * to and from {@link Mono}s and {@link Flux}es.
 */
@Service
public class SpeechService {
  private static final Logger log = LoggerFactory.getLogger(SpeechService.class);

  private final SpeechClient speechClient;

  /**
   * Creates a new {@link SpeechService}.
   *
   * @param speechClient The Google speech client to use.
   */
  public SpeechService(final SpeechClient speechClient) {
    this.speechClient = Objects.requireNonNull(speechClient, "speechClient cannot be null");
  }

  /**
   * Translates the given sentence into audio data, which is returned in the form of a
   * {@link Resource}. Audio is requested as Dutch (nl-NL) and encoded as 16 kHz 16-bit PCM in the
   * response. The same format that is accepted by the Speakup voice API.
   *
   * @param sentence The Dutch sentence to transform to speech.
   * @return The audio data as a {@link Resource}.
   */
  public Resource say(final String sentence) {
    log.debug("Saying: " + sentence);

    try (TextToSpeechClient textToSpeechClient = TextToSpeechClient.create()) {
      SynthesisInput input = SynthesisInput.newBuilder()
          .setText(sentence)
          .build();

      VoiceSelectionParams voice =
          VoiceSelectionParams.newBuilder()
              .setLanguageCode("nl-NL")
              .setSsmlGender(SsmlVoiceGender.NEUTRAL)
              .setName("nl-NL-Wavenet-A")
              .build();

      AudioConfig audioConfig = AudioConfig.newBuilder()
          .setAudioEncoding(AudioEncoding.LINEAR16)
          .setSampleRateHertz(16000)
          .build();

      SynthesizeSpeechResponse response =
          textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);

      // Get the audio contents from the response
      ByteString audioContents = response.getAudioContent();

      return new ByteArrayResource(audioContents.toByteArray());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Uses the Google speech-to-text API to transcribe a single audio utterance to text (Dutch).
   * Uses the "single utterance" mode of the Google API, which effectively lets Google determine
   * when the utterance ends. Note that Google is typically very good at predicting the end of
   * an utterance and mostly returns the result with a short delay. However for single word
   * utterances Google is typically unable to ascertain when the utterance ends other than waiting
   * if the input stays silent. Therefore single word utterances are often returned with a small
   * delay.
   *
   * @param audio A {@link Flux} of {@link DataBuffer}s containing 16-bit PCM encoded audio samples.
   *              This is the format that is natively returned by the Speakup voice API.
   * @return A {@link Mono} that emits the spoken utterance as text when successful.
   * @see <a href="https://cloud.google.com/speech-to-text/docs/streaming-recognize">https://cloud.google.com/speech-to-text/docs/streaming-recognize</a>
   */
  public Mono<String> transcribeUtterance(final Flux<DataBuffer> audio) {
    final CompletableFuture<String> utteranceCompleted = new CompletableFuture<>();
    final CompletableFuture<String> result = new CompletableFuture<>();

    // Transcribe dutch text:
    final RecognitionConfig recConfig = RecognitionConfig.newBuilder()
        .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
        .setLanguageCode("nl-NL")
        .setSampleRateHertz(16000)
        .setModel("command_and_search")
        .build();

    // Single utterance, no interim results. Stop transcribing when Google has detected an
    // utterance:
    final StreamingRecognitionConfig config = StreamingRecognitionConfig
        .newBuilder()
        .setConfig(recConfig)
        .setSingleUtterance(true)
        .setInterimResults(false)
        .build();

    var responseObserver = new ResponseObserver<StreamingRecognizeResponse>() {
      @Override
      public void onStart(final StreamController controller) {

      }

      @Override
      public void onResponse(final StreamingRecognizeResponse response) {
        var results = response.getResultsList();
        if (results.isEmpty()) {
          return;
        }

        var result = response.getResultsList().get(0);
        var alternatives = result.getAlternativesList();

        if (alternatives.isEmpty()) {
          utteranceCompleted.complete("");
          return;
        }

        utteranceCompleted.complete(alternatives.get(0).getTranscript());
      }

      @Override
      public void onError(final Throwable t) {
        log.error("Audio transcribe error", t);

        utteranceCompleted.completeExceptionally(t);
      }

      @Override
      public void onComplete() {
        // TODO: This method doesn't seem to get invoked by the Google Speech API? Investigate.
      }
    };

    log.debug("Creating client stream");
    final ClientStream<StreamingRecognizeRequest> clientStream =
        speechClient.streamingRecognizeCallable().splitCall(responseObserver);

    final StreamingRecognizeRequest request =
        StreamingRecognizeRequest.newBuilder().setStreamingConfig(config).build();

    // Send the config:
    log.debug("Configuring client stream");
    clientStream.send(request);

    final Disposable streamDisposable = audio
        .doOnNext((DataBuffer dataBuffer) -> {
          var req = StreamingRecognizeRequest
              .newBuilder()
              .setAudioContent(ByteString.copyFrom(dataBuffer.asByteBuffer()))
              .build();

          log.debug("Sending audio fragment to client stream");
          clientStream.send(req);
        })
        .subscribe();

    // Stop streaming when an utterance has been received:
    utteranceCompleted
        .handle((utterance, throwable) -> {
          // Stop streaming audio:
          streamDisposable.dispose();
          clientStream.closeSend();

          if (throwable != null) {
            result.completeExceptionally(throwable);
          } else {
            result.complete(utterance);
          }

          return null;
        });

    return Mono.fromFuture(result);
  }
}
