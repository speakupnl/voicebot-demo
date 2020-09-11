FROM ubuntu:18.04

RUN apt-get update \
    && apt-get install -y \
        python3.7 \
        python3-pip \
        python3-dev \
    && rm -rf /var/lib/apt/lists/*

# Install Rasa:
RUN pip3 install -U pip \
    && pip install rasa spacy \
    && python3 -m spacy download nl_core_news_md \
    && python3 -m spacy link nl_core_news_md nl


# Make sure that Python uses UTF-8 by default:
ENV PYTHONIOENCODING=utf8

# Copy the Rasa project:
COPY rasaproject /opt/rasaproject

# Train the Rasa project:
RUN cd /opt/rasaproject \
    && rasa train

WORKDIR /opt/rasaproject

# The Rasa server will listen on port 5005:
EXPOSE 5005

ENTRYPOINT ["rasa", "run", "--enable-api"]