package com.cuifeng.backend.infrastructure;

import com.cuifeng.backend.takeout.TakeoutService.OutboxEvent;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class RabbitOutboxPublisher {
    private final boolean enabled;
    private final ConnectionFactory factory;
    private final String exchange;
    private final String queue;
    private final String dlq;

    public RabbitOutboxPublisher(@Value("${app.rabbitmq.enabled:false}") boolean enabled,
                                 @Value("${app.rabbitmq.host:localhost}") String host,
                                 @Value("${app.rabbitmq.port:5672}") int port,
                                 @Value("${app.rabbitmq.username:guest}") String username,
                                 @Value("${app.rabbitmq.password:guest}") String password,
                                 @Value("${app.rabbitmq.exchange:takeout.events}") String exchange,
                                 @Value("${app.rabbitmq.queue:takeout.events.main}") String queue,
                                 @Value("${app.rabbitmq.dlq:takeout.events.dlq}") String dlq) {
        this.enabled = enabled;
        this.exchange = exchange;
        this.queue = queue;
        this.dlq = dlq;
        this.factory = new ConnectionFactory();
        this.factory.setHost(host);
        this.factory.setPort(port);
        this.factory.setUsername(username);
        this.factory.setPassword(password);
        this.factory.setAutomaticRecoveryEnabled(true);
        this.factory.setNetworkRecoveryInterval(3000);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean publish(OutboxEvent event) {
        if (!enabled) {
            return true;
        }
        try (Connection connection = factory.newConnection("takeout-outbox-publisher");
             Channel channel = connection.createChannel()) {
            declareTopology(channel);
            channel.confirmSelect();
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .deliveryMode(2)
                    .messageId(String.valueOf(event.id))
                    .type(event.eventType)
                    .timestamp(new java.util.Date())
                    .build();
            channel.basicPublish(exchange, event.eventType, true, properties, payload(event).getBytes(StandardCharsets.UTF_8));
            return channel.waitForConfirms(5000);
        } catch (Exception ex) {
            return false;
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

    private String payload(OutboxEvent event) {
        return """
                {"id":%d,"eventType":"%s","aggregateType":"%s","aggregateId":%d,"payload":"%s","createdAt":"%s"}
                """.formatted(
                event.id,
                escape(event.eventType),
                escape(event.aggregateType),
                event.aggregateId,
                escape(event.payload),
                event.createdAt);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
