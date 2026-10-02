package com.dev.fastfood.config;

import com.dev.fastfood.messaging.OrderCreatedEvent;
import com.dev.fastfood.messaging.OrderEventDeserializer;
import com.dev.fastfood.messaging.OrderEventSerializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.util.HashMap;
import java.util.Map;

// We build the producer/consumer factories by hand here, passing our own
// OrderEventSerializer/OrderEventDeserializer instances directly, instead
// of pointing at them by class name in application.properties. Kafka would
// otherwise have to construct those classes itself via reflection (which
// needs a public no-arg constructor and can't accept injected beans) — this
// way is just as simple and keeps everything visible in one place.
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, OrderCreatedEvent> orderEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // "all" = wait for the message to be fully written to disk on the
        // broker before considering it sent. Part of the "never lose an
        // order" requirement — slower than the default, but this is exactly
        // the kind of message where that trade-off is worth it.
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(configProps, new StringSerializer(), orderEventValueSerializer());
    }

    // Normally we only ever send OrderCreatedEvent objects. The one exception:
    // when a message on order.created is unreadable (a "poison pill"), the
    // retry machinery forwards its ORIGINAL raw bytes to the DLT using this
    // same template — and OrderEventSerializer alone can't write a byte[].
    // DelegatingByTypeSerializer picks a serializer by the value's actual type.
    // The cast is needed only because it's declared as Serializer<Object>,
    // while the template is typed to OrderCreatedEvent for everyone else.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Serializer<OrderCreatedEvent> orderEventValueSerializer() {
        return (Serializer) new DelegatingByTypeSerializer(Map.of(
                byte[].class, new ByteArraySerializer(),
                OrderCreatedEvent.class, new OrderEventSerializer()));
    }

    @Bean
    public KafkaTemplate<String, OrderCreatedEvent> orderEventKafkaTemplate(
            ProducerFactory<String, OrderCreatedEvent> orderEventProducerFactory) {
        return new KafkaTemplate<>(orderEventProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, OrderCreatedEvent> orderEventConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "order-processing");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // ErrorHandlingDeserializer wraps ours so that a message which can't
        // be parsed doesn't blow up the whole poll. Without it, one bad
        // message makes the consumer re-read that same offset forever, and
        // since one consumer thread reads all 3 partitions, EVERY order stops
        // being processed (verified live during Milestone 3 testing). With it,
        // the failure is handed to Spring's error handling instead, which
        // sends the record straight to the DLT — no retries, since retrying
        // unreadable bytes can never succeed — and the partition moves on.
        return new DefaultKafkaConsumerFactory<>(configProps, new StringDeserializer(),
                new ErrorHandlingDeserializer<>(new OrderEventDeserializer()));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> orderEventKafkaListenerContainerFactory(
            ConsumerFactory<String, OrderCreatedEvent> orderEventConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderEventConsumerFactory);
        // MANUAL ack mode: we only tell Kafka "done with this message" once
        // our own processing (the DB save) has actually finished. If the
        // app crashes mid-processing, the message was never acknowledged,
        // so it gets redelivered instead of silently disappearing.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
