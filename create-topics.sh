#!/bin/sh

set -eu

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}"

create_topic() {
  topic="$1"

  /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions 3 \
    --replication-factor 1
}

create_topic "place-order-topic"
create_topic "place-order-retry-topic"
create_topic "place-order-dlq-topic"
