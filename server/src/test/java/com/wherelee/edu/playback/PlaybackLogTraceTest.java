package com.wherelee.edu.playback;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaybackLogTraceTest {

    @Test
    void capturesTraceIdWhenBackendUrlRequestIsRecorded() {
        PlaybackLogRepository repository = mock(PlaybackLogRepository.class);
        PlaybackLogQueue queue = mock(PlaybackLogQueue.class);
        when(queue.offer(any(PlaybackLogEvent.class))).thenReturn(true);
        PlaybackLogService service = new PlaybackLogService(repository, queue);
        MDC.put("traceId", "trace-123");

        try {
            service.recordUrlRequest(1L, 2L, 3L, true, "test-agent");
        } finally {
            MDC.remove("traceId");
        }

        ArgumentCaptor<PlaybackLogEvent> event = ArgumentCaptor.forClass(PlaybackLogEvent.class);
        verify(queue).offer(event.capture());
        assertThat(event.getValue().getTraceId()).isEqualTo("trace-123");
    }
}
