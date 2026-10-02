package com.dev.fastfood.messaging;

import org.apache.kafka.common.serialization.Serializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// A deliberately small, hand-written serializer instead of Spring Kafka's
// built-in JsonSerializer. This project uses Jackson 3 (tools.jackson),
// and Milestone 2 already lost time to a Jackson-version mismatch with
// Redis caching — writing our own means we know exactly which Jackson
// this uses, instead of hoping a library's internals agree with ours.
public class OrderEventSerializer implements Serializer<OrderCreatedEvent> {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Override
    public byte[] serialize(String topic, OrderCreatedEvent data) {
        if (data == null) {
            return null;
        }
        return mapper.writeValueAsBytes(data);
    }
}
