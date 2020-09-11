Prerequisites
=============

To build and run the service locally are you will need the following:

 - JDK 14 or higher. For example [OpenJDK](https://openjdk.java.net/).
 - A Linux machine running a recent version of Docker. The Docker API should be configured so that
   it is accessible locally on ``/var/run/docker.sock`` (the default on most systems).
 - You will need to have [Docker Compose](https://docs.docker.com/compose/) installed and available
   in your ``PATH``.
 - A **Client ID** and **Client Secret** for access to the Speakup programmable voice preview API.
   If you don't have these and feel that you should: please contact your friendly neighbourhood
   developer at Speakup.
- An external phone number to dial to access your bot. This number belongs to your account and has
  been supplied by Speakup as well. 
- A credentials file for the Google text-to-speech and speech-to-text API.
- A way to make your service accessible from the Speakup infrastructure over HTTP, we use
  [ngrok](https://ngrok.com/) to accomplish this.
- A basic understanding of [Spring Boot](https://spring.io/projects/spring-boot) and 
  [Project Reactor](https://projectreactor.io/) will be of great help!
    
Getting started
===============

Use the following steps to build the voicebot demo from source and run it locally.

Build the project by running the following command from the root:

```sh
./gradlew build
```

This will build the voicebot service from source, create a Docker image for it and build the NLU
component ([Rasa](https://rasa.com/)) using Docker.

Now that you have built the NLU component you can start it on your local Docker daemon. To make this
convenient a ``docker-compose`` file is included in the project. Start the component by running the
following from the root of the project:

```sh
docker-compose up -d
```

You will now have a Rasa service running for which the API is exposed on: http://localhost:5005/.

Set up an [ngrok](https://ngrok.com) tunnel to access your service running locally. We're assuming
that you keep the default configuration for now and will run it on port ``8080`` later:

```sh
ngrok http 8080
```

Now you will see output along these lines:

```
ngrok by @inconshreveable                                                                                                                                                                                                                                                                      (Ctrl+C to quit)
                                                                                                                                                                                                                                                                                                               
Session Status                online                                                                                                                                                                                                                                                                           
Session Expires               7 hours, 59 minutes                                                                                                                                                                                                                                                              
Version                       2.3.35                                                                                                                                                                                                                                                                           
Region                        United States (us)                                                                                                                                                                                                                                                               
Web Interface                 http://127.0.0.1:4040                                                                                                                                                                                                                                                            
Forwarding                    http://0c5faf894640.ngrok.io -> http://localhost:8080                                                                                                                                                                                                                            
Forwarding                    https://0c5faf894640.ngrok.io -> http://localhost:8080       
```

Keep the HTTPS URL from the last line handy, we will be using it later. Make sure you have your
OAuth2 **Client ID** and **Client secret** as well.

Before we move on, we need to set up our Google credentials if you haven't already. Credentials
for the Google APIs can be created [here](https://console.cloud.google.com/apis/credentials/serviceaccountkey?_ga=2.201739473.1140857505.1599741060-1183107303.1589120096).
You will need a service account that has access to the text-to-speech and speech-to-text APIs. Use
the ``JSON`` key type, which will result in a file that you store locally.

Now let's start the service by running:

```sh
GOOGLE_APPLICATION_CREDENTIALS=<path to your Google credentials file> \
java -jar service/build/libs/service-0.1.0-SNAPSHOT.jar \
    --speakup.voice.endpoint.external-uri=<your ngrok HTTPS URI goes here ...> \
    --speakup.voice.api.application-id=<your OAuth2 Client ID goes here ...> \
    --speakup.voice.api.application-secret=<your OAuth2 Client Secret goes here ...>
```

At this point, if everything went well you should be able to access your bot through the phone
number that corresponds with your API account.

Now that we have the nitty gritty details out of the way you can experiment away by modifying the
NLU training data or tinker with the logic of the voice bot. See the following sections on how to
get started!
   
Submodules
==========

The Voicebot project is structured into two subprojects:

 - ``service``: contains the voice bot itself, a 
   [Spring Boot](https://spring.io/projects/spring-boot) service.
 - ``nlu``: contains a Docker build to set up a very basic Rasa NLU model.
 
Voicebot service
----------------

The voice bot service is a small and rather straight forward 
[Spring Boot](https://spring.io/projects/spring-boot) service. It has a few notable components:

 - ``nl.speakup.voice.voicebotdemo.Bot``: the bot itself, contains the logic for handling a call
   listening for utterances, you name it. This is where you apply your creativity!
 - ``nl.speakup.voice.voicebotdemo.NluService``: a small service client to access the Rasa NLU 
   service using a Spring WebClient.
 - ``nl.speakup.voice.voicebotdemo.SpeechService``: a small service to that provides convenient
   accessors the Google Speech APIs.
 - ``nl.speakup.voice.voicebotdemo.VoiceApiClient``: a client component to access the Speakup
   programmable voice API. It listens for voice events over a websocket connection and allows you
   to issue commands. Note that the client is incomplete: it doesn't handle all events, nor does it
   expose the full extent of commands that can be issued. However the service is easy to extend
   should you need more functionality.
   
As with any Spring Boot service there are several 
[mechanisms](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config)
to present configuration settings. See ``src/main/resources/application.yaml`` for the defaults.

Rasa NLU
--------

This project comes with a very basic configuration for [Rasa](https://rasa.com) that gets you 
started with a Dutch NLU model that has a few intents with some training data.  

The ``nlu`` subproject contains a Docker build procedure (``nlu/src/main/docker/Dockerfile``) that
sets up a basic Rasa instance and trains the model. The Rasa configuration files as well as the
training data can be found under ``nlu/src/main/rasa``.

Explaining how to configure a Rasa model is well beyond the scope of this READMe. Furthermore, the
Rasa project has excellent [documentation](https://rasa.com/docs/) to help you. This project only
uses a fraction of the potential of Rasa.

The Docker build for Rasa is performed from the main Gradle build. Therefore building and training
Rasa is as easy as running ``./gradlew build`` or ``./gradlew :nlu:build`` if you want to be more
specific.

The included Docker Compose file starts it locally:

```sh
docker-compose up -d
```

Now for example you can access the NLU component as follows:

```sh
curl -s --header "Content-Type: application/json" --request POST --data '{"text":"Hello!"}' http://localhost:5005/model/parse | jq
```