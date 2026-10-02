package com.dev.fastfood.service;

import com.dev.fastfood.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublishAckRecorderTest {

    @Mock private OrderRepository orderRepository;

    @InjectMocks
    private PublishAckRecorder recorder;

    @Test
    void flush_writesQueuedIdsInBatchesOfAtMost1000() {
        for (int i = 0; i < 2_500; i++) {
            recorder.record(UUID.randomUUID());
        }

        recorder.flush();

        // 2,500 IDs → three UPDATE statements: 1,000 + 1,000 + 500,
        // instead of 2,500 separate ones.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> batches = ArgumentCaptor.forClass(Collection.class);
        verify(orderRepository, times(3)).markPublished(batches.capture(), any(OffsetDateTime.class));
        assertThat(batches.getAllValues()).extracting(Collection::size).containsExactly(1_000, 1_000, 500);
    }

    @Test
    void flush_withNothingQueued_doesNotTouchTheDatabase() {
        recorder.flush();

        verify(orderRepository, never()).markPublished(any(), any());
    }
}
