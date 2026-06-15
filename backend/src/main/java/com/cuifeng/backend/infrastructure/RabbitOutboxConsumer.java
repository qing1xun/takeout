package com.cuifeng.backend.infrastructure;

import com.cuifeng.backend.takeout.TakeoutService;
import com.cuifeng.backend.takeout.TakeoutService.OutboxEvent;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RabbitOutboxConsumer {
    private static final Logger log = LoggerFactory.getLogger(RabbitOutboxConsumer.class);

    private final boolean enabled;
    private final int consumers;
    private final int prefetch;
    private final long reconnectDelayMs;
    private final ConnectionFactory factory;
    private final String exchange;
    private final String queue;
    private final String dlq;
    private final TakeoutService takeoutService;
    private final List<Connection> activeConnections = new ArrayList<>();
    private ExecutorService executor;
    private volatile boolean running;

    public RabbitOutboxConsumer(@Value("${app.rabbitmq.enabled:false}") boolean rabbitEnabled,
                                @Value("${app.rabbitmq.consumer-enabled:true}") boolean consumerEnabled,
                                @Value("${app.rabbitmq.consumers:2}") int consumers,
                                @Value("${app.rabbitmq.prefetch:50}") int prefetch,
                                @Value("${app.rabbitmq.reconnect-delay-ms:3000}") long reconnectDelayMs,
                                @Value("${app.rabbitmq.host:localhost}") String host,
                                @Value("${app.rabbitmq.port:5672}") int port,
                                @Value("${app.rabbitmq.username:guest}") String username,
                                @Value("${app.rabbitmq.password:guest}") String password,
                                @Value("${app.rabbitmq.exchange:takeout.events}") String exchange,
                                @Value("${app.rabbitmq.queue:takeout.events.main}") String queue,
                                @Value("${app.rabbitmq.dlq:takeout.events.dlq}") String dlq,
                                TakeoutService takeoutService) {
        this.enabled = rabbitEnabled && consumerEnabled;
        this.consumers = Math.max(1, consumers);
        this.prefetch = Math.max(1, prefetch);
        this.reconnectDelayMs = Math.max(500, reconnectDelayMs);
        this.exchange = exchange;
        this.queue = queue;
        this.dlq = dlq;
        this.takeoutService = takeoutService;
        this.factory = new ConnectionFactory();
        this.factory.setHost(host);
        this.factory.setPort(port);
        this.factory.setUsername(username);
        this.factory.setPassword(password);
        this.factory.setAutomaticRecoveryEnabled(true);
        this.factory.setNetworkRecoveryInterval(this.reconnectDelayMs);
    }

    @PostConstruct
    public void start() {
        if (!enabled) {
            return;
        }
        running = true;
        AtomicInteger counter = new AtomicInteger();
        executor = Executors.newFixedThreadPool(consumers, task -> {
            Thread thread = new Thread(task, "takeout-rabbit-outbox-consumer-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        for (int i = 1; i <= consumers; i++) {
            final int consumerIndex = i;
            executor.submit(() -> consumeLoop(consumerIndex));
        }
        log.info("Started {} RabbitMQ outbox consumer(s), queue={}, prefetch={}", consumers, queue, prefetch);
    }

    @PreDestroy
    public void stop() {
        running = false;
        synchronized (activeConnections) {
            activeConnections.forEach(connection -> {
                try {
                    connection.close();
                } catch (Exception ignored) {
                    // Closing during shutdown is best-effort.
                }
            });
            activeConnections.clear();
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void consumeLoop(int consumerIndex) {
        while (running) {
            try (Connection connection = factory.newConnection("takeout-outbox-consumer-" + consumerIndex);
                 Channel channel = connection.createChannel()) {
                registerConnection(connection);
                declareTopology(channel);
                channel.basicQos(prefetch);
                DeliverCallback callback = (consumerTag, delivery) -> {
                    long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                    try {
                        OutboxEvent event = parseEvent(delivery.getBody());
                        takeoutService.consumePublishedOutboxEvent(event);
                        channel.basicAck(deliveryTag, false);
                    } catch (IllegalArgumentException ex) {
                        log.warn("Rejected malformed RabbitMQ outbox message: {}", ex.getMessage());
                        channel.basicReject(deliveryTag, false);
                    } catch (Exception ex) {
                        boolean requeue = !delivery.getEnvelope().isRedeliver();
                        log.warn("RabbitMQ outbox consumer failed, event will {}",
                                requeue ? "be retried once" : "go to DLQ", ex);
                        channel.basicNack(deliveryTag, false, requeue);
                    }
                };
                channel.basicConsume(queue, false, callback, consumerTag -> {
                    if (running) {
                        log.warn("RabbitMQ outbox consumer {} was cancelled", consumerTag);
                    }
                });
                while (running && connection.isOpen() && channel.isOpen()) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                if (running) {
                    log.warn("RabbitMQ outbox consumer reconnecting in {} ms", reconnectDelayMs, ex);
                    sleepBeforeReconnect();
                }
            }
        }
    }

    private void registerConnection(Connection connection) {
        synchronized (activeConnections) {
            activeConnections.add(connection);
            activeConnections.removeIf(existing -> !existing.isOpen());
        }
    }

    private void declareTopology(Channel channel) throws Exception {
        channel.exchangeDeclare(exchange, BuiltinExchangeType.TOPIC, true);
        channel.exchangeDeclare(exchange + ".dlx", BuiltinExchangeType.TOPIC, true);
        channel.queueDeclare(dlq, true, false, false, Map.of());
        channel.queueBind(dlq, exchange + ".dlx", "#");
        channel.queueDeclare(queue, true, false, false, Map.of("x-dead-letter-exchange", exchange + ".dlx"));
        channel.queueBind(queue, exchange, "#");
    }

    private OutboxEvent parseEvent(byte[] body) {
        String json = new String(body, StandardCharsets.UTF_8);
        try {
            return new OutboxEvent(
                    requiredLong(json, "id"),
                    requiredText(json, "eventType"),
                    requiredText(json, "aggregateType"),
                    requiredLong(json, "aggregateId"),
                    optionalText(json, "payload"),
                    "PUBLISHED",
                    0,
                    LocalDateTime.parse(requiredText(json, "createdAt")));
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid outbox payload", ex);
        }
    }

    private long requiredLong(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing field " + field);
        }
        return Long.parseLong(matcher.group(1));
    }

    private String requiredText(String json, String field) {
        String value = optionalText(json, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException("missing field " + field);
        }
        return value;
    }

    private String optionalText(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(json);
        return matcher.find() ? unescapeJson(matcher.group(1)) : "";
    }

    private String unescapeJson(String value) {
        StringBuilder result = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!escaped) {
                if (current == '\\') {
                    escaped = true;
                } else {
                    result.append(current);
                }
                continue;
            }
            switch (current) {
                case '"', '\\', '/' -> result.append(current);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                default -> result.append(current);
            }
            escaped = false;
        }
        if (escaped) {
            result.append('\\');
        }
        return result.toString();
    }

    private void sleepBeforeReconnect() {
        try {
            Thread.sleep(reconnectDelayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
