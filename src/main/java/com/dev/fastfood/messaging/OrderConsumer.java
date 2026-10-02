package com.dev.fastfood.messaging;

import com.dev.fastfood.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import static com.dev.fastfood.config.KafkaTopicConfig.ORDER_CREATED_TOPIC;

@Component
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    private final OrderService orderService;

    public OrderConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    // @RetryableTopic is Spring Kafka's non-blocking retry mechanism.
    // If the method below throws, instead of retrying in place (which
    // would block this partition), Spring publishes the message to an
    // auto-created "order.created-retry-0" topic with a delay, tries
    // again, and repeats with increasing delays. attempts = "4" means
    // 1 original try + 3 retries (2s, 4s, 8s backoff). After all of
    // those are exhausted, the message goes to "order.created-dlt" —
    // the Dead Letter Topic — instead of vanishing. A DLT is just an
    // ordinary topic reserved for messages that could never be
    // processed, so nothing is silently lost even in the worst case.
    //
    // Note: OrderService.processOrderEvent() deliberately does NOT throw
    // for expected business failures (bad restaurant/menu state) — only
    // genuinely unexpected exceptions (a bug, a DB outage) reach this
    // retry/DLT machinery, since retrying "the restaurant is inactive"
    // forever would never succeed.
    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delay = 2000, multiplier = 2.0),
            kafkaTemplate = "orderEventKafkaTemplate",
            listenerContainerFactory = "orderEventKafkaListenerContainerFactory"
    )
    @KafkaListener(
            topics = ORDER_CREATED_TOPIC,
            groupId = "order-processing",
            containerFactory = "orderEventKafkaListenerContainerFactory"
    )
    public void handleOrderCreated(OrderCreatedEvent event, Acknowledgment ack) {
        log.info("Processing order-created event for order {}", event.getOrderId());
        orderService.processOrderEvent(event);
        // Manual acknowledgement: only tell Kafka we're done with this
        // message once processOrderEvent has actually finished (including
        // its own DB commit). If the app crashed partway through, this
        // line never runs, and the message gets redelivered on restart —
        // which is exactly why processOrderEvent has to be idempotent.
        ack.acknowledge();
    }

    // Runs once a message has failed all 4 attempts above and has landed
    // in the DLT. We still know which order this was for, so instead of
    // leaving it stuck in PENDING forever, we record the terminal outcome.
    @DltHandler
    public void handleDlt(OrderCreatedEvent event,
                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                          Acknowledgment ack) {
        log.error("Order {} exhausted all retries and landed in the DLT: {}",
                event.getOrderId(), exceptionMessage);
        orderService.markOrderFailed(event.getOrderId(),
                "Processing failed after retries: " + exceptionMessage);
        ack.acknowledge();
    }
}
