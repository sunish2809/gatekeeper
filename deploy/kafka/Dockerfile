FROM apache/kafka:3.9.1

# Railway volumes are mounted as root, so the broker must run as root to write its log directory.
USER root

ENV KAFKA_NODE_ID=1 \
    KAFKA_PROCESS_ROLES=broker,controller \
    KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
    KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
    KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
    KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
    KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
    KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
    KAFKA_NUM_PARTITIONS=3 \
    KAFKA_LOG_RETENTION_HOURS=72 \
    KAFKA_LOG_DIRS=/var/lib/kafka/data \
    KAFKA_HEAP_OPTS="-Xmx256m -Xms256m"

EXPOSE 9092

# Clients must reach the broker through the address it advertises, which is the Railway private domain.
CMD ["bash", "-c", "export KAFKA_ADVERTISED_LISTENERS=\"${KAFKA_ADVERTISED_LISTENERS:-PLAINTEXT://${RAILWAY_PRIVATE_DOMAIN:-localhost}:9092}\" && mkdir -p \"$KAFKA_LOG_DIRS\" && exec /etc/kafka/docker/run"]
