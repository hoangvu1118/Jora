package com.sefujo.ingestion.kafka;

import com.sefujo.ingestion.JobDiscoveredPersistService;
import com.sefujo.ingestion.dto.DiscoveredJobResult;
import com.sefujo.ingestion.dto.JobCreatedEvent;
import com.sefujo.ingestion.dto.JobDiscoveredEvent;
import com.sefujo.recommendation.JobRecommendationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit tests for the two listeners around job.created.
class KafkaListenersTest {

    @Test
    void jobDiscovered_shouldPublish_sendsJobCreatedWithJobIdAsString() {
        JobDiscoveredPersistService persistService = mock(JobDiscoveredPersistService.class);
        JobCreatedPublisher publisher = mock(JobCreatedPublisher.class);
        JobDiscoveredListener listener = new JobDiscoveredListener(persistService, publisher);
        JobDiscoveredEvent event = new JobDiscoveredEvent("greenhouse", null);
        when(persistService.handleDiscoveredJob(event)).thenReturn(new DiscoveredJobResult(true, 42L));

        listener.onJobRequest(event);

        ArgumentCaptor<JobCreatedEvent> captor = ArgumentCaptor.forClass(JobCreatedEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().jobId()).isEqualTo("42");
    }

    @Test
    void jobDiscovered_shouldNotPublish_sendsNothing() {
        JobDiscoveredPersistService persistService = mock(JobDiscoveredPersistService.class);
        JobCreatedPublisher publisher = mock(JobCreatedPublisher.class);
        JobDiscoveredListener listener = new JobDiscoveredListener(persistService, publisher);
        JobDiscoveredEvent event = new JobDiscoveredEvent("greenhouse", null);
        when(persistService.handleDiscoveredJob(event)).thenReturn(new DiscoveredJobResult(false, null));

        listener.onJobRequest(event);

        verify(publisher, never()).publish(any());
    }

    @Test
    void jobCreated_callsRecommendForNewJob_withTheJobId() {
        JobRecommendationService recommendationService = mock(JobRecommendationService.class);
        JobCreatedListener listener = new JobCreatedListener(recommendationService);

        listener.onJobCreated(new JobCreatedEvent("42"));

        verify(recommendationService).recommendForNewJob(42L);
    }
}
