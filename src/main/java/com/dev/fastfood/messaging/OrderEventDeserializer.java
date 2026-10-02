package com.dev.fastfood.messaging;

import org.apache.kafka.common.serialization.Deserializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// Mirrors OrderEventSerializer. This class only ever reads OrderCreatedEvent
// messages, so unlike the generic-purpose serializer in CacheConfig, there's
// no need for polymorphic type info embedded in the JSON — the target type
// is always known up front.
public class OrderEventDeserializer implements Deserializer<OrderCreatedEvent> {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Override
    public OrderCreatedEvent deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        return mapper.readValue(data, OrderCreatedEvent.class);
    }
}
