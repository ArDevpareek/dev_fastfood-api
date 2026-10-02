package com.dev.fastfood.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

// Spring Boot auto-configures a KafkaAdmin bean once spring-kafka is on the
// classpath. On startup, it looks at every NewTopic bean here and creates
// whichever ones don't already exist on the broker.
@Configuration
public class KafkaTopicConfig {

    public static final String ORDER_CREATED_TOPIC = "order.created";

    // A topic is really a set of independent, ordered logs called
    // partitions. Kafka only lets ONE consumer per partition, per consumer
    // group, read from it at a time — so partition count is the hard cap
    // on how many consumer instances can usefully work in parallel.
    //
    // 3 partitions, chosen for this project because:
    //   - It's a single-broker local setup on a 16GB laptop, not a
    //     production cluster — more partitions means more open file
    //     handles and bookkeeping overhead for no real benefit here.
    //   - It's enough to clearly demonstrate horizontal scaling: with 2
    //     consumer instances in the same group, one gets 2 partitions and
    //     the other gets 1 — visibly uneven, provably shared.
    //   - It leaves room to go to a 3rd consumer instance later without
    //     any broker reconfiguration, since 3 consumers exactly matches
    //     3 partitions (a 4th instance would just sit idle).
    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(ORDER_CREATED_TOPIC)
                .partitions(3)
                .replicas(1) // single broker in this dev setup — no room to replicate
                .build();
    }
}
