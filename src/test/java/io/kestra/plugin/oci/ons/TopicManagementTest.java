package io.kestra.plugin.oci.ons;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class TopicManagementTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testCreateTopicOutput() {
        Instant now = Instant.now();
        CreateTopic.Output output = CreateTopic.Output.builder()
            .topicId("ocid1.onstopic.oc1.fra.xxx")
            .name("alerts-topic")
            .compartmentId("ocid1.compartment.oc1..xxx")
            .lifecycleState("ACTIVE")
            .timeCreated(now)
            .build();

        assertThat(output.getTopicId(), is("ocid1.onstopic.oc1.fra.xxx"));
        assertThat(output.getName(), is("alerts-topic"));
        assertThat(output.getLifecycleState(), is("ACTIVE"));
        assertThat(output.getTimeCreated(), is(now));
    }

    @Test
    void testListTopicsOutput() {
        ListTopics.TopicSummary summary = ListTopics.TopicSummary.builder()
            .topicId("ocid1.onstopic.oc1.fra.xxx")
            .name("test-topic")
            .compartmentId("ocid1.compartment.oc1..xxx")
            .description("Test description")
            .lifecycleState("ACTIVE")
            .build();

        ListTopics.Output output = ListTopics.Output.builder()
            .topics(List.of(summary))
            .count(1)
            .build();

        assertThat(output.getCount(), is(1));
        assertThat(output.getTopics().get(0).getName(), is("test-topic"));
    }

    @Test
    void testDeleteTopicOutput() {
        DeleteTopic.Output output = DeleteTopic.Output.builder()
            .topicId("ocid1.onstopic.oc1.fra.xxx")
            .opcRequestId("req-12345")
            .build();

        assertThat(output.getTopicId(), is("ocid1.onstopic.oc1.fra.xxx"));
        assertThat(output.getOpcRequestId(), is("req-12345"));
    }

    @Test
    void testSubscriptionOutputs() {
        CreateSubscription.Output subOutput = CreateSubscription.Output.builder()
            .subscriptionId("ocid1.onssubscription.oc1.fra.xxx")
            .topicId("ocid1.onstopic.oc1.fra.xxx")
            .protocol("HTTPS")
            .lifecycleState("ACTIVE")
            .build();

        assertThat(subOutput.getSubscriptionId(), is("ocid1.onssubscription.oc1.fra.xxx"));
        assertThat(subOutput.getProtocol(), is("HTTPS"));

        DeleteSubscription.Output delOutput = DeleteSubscription.Output.builder()
            .subscriptionId("ocid1.onssubscription.oc1.fra.xxx")
            .opcRequestId("req-67890")
            .build();

        assertThat(delOutput.getSubscriptionId(), is("ocid1.onssubscription.oc1.fra.xxx"));
    }

    @Test
    void testCreateTopicRun() throws Exception {
        RunContext runContext = runContextFactory.of();
        com.oracle.bmc.ons.NotificationControlPlaneClient mockClient = org.mockito.Mockito.mock(com.oracle.bmc.ons.NotificationControlPlaneClient.class);

        com.oracle.bmc.ons.responses.CreateTopicResponse mockResponse = com.oracle.bmc.ons.responses.CreateTopicResponse.builder()
            .notificationTopic(com.oracle.bmc.ons.model.NotificationTopic.builder()
                .topicId("ocid1.onstopic.oc1.fra.topic123")
                .name("alerts-topic")
                .compartmentId("ocid1.compartment.oc1..comp123")
                .lifecycleState(com.oracle.bmc.ons.model.NotificationTopic.LifecycleState.Active)
                .timeCreated(java.util.Date.from(Instant.parse("2026-10-13T12:00:00Z")))
                .build())
            .build();

        org.mockito.Mockito.when(mockClient.createTopic(org.mockito.ArgumentMatchers.any(com.oracle.bmc.ons.requests.CreateTopicRequest.class)))
            .thenReturn(mockResponse);

        CreateTopic task = CreateTopic.builder()
            .customControlPlaneClient(mockClient)
            .compartmentId(Property.of("ocid1.compartment.oc1..comp123"))
            .name(Property.of("alerts-topic"))
            .topicDescription(Property.of("Test Description"))
            .build();

        CreateTopic.Output output = task.run(runContext);

        assertThat(output.getTopicId(), is("ocid1.onstopic.oc1.fra.topic123"));
        assertThat(output.getName(), is("alerts-topic"));
        assertThat(output.getLifecycleState(), is("ACTIVE"));
    }

    @Test
    void testListTopicsPaginationRun() throws Exception {
        RunContext runContext = runContextFactory.of();
        com.oracle.bmc.ons.NotificationControlPlaneClient mockClient = org.mockito.Mockito.mock(com.oracle.bmc.ons.NotificationControlPlaneClient.class);

        // Page 1 returns 1 item and next page token
        com.oracle.bmc.ons.responses.ListTopicsResponse page1 = com.oracle.bmc.ons.responses.ListTopicsResponse.builder()
            .items(List.of(
                com.oracle.bmc.ons.model.NotificationTopicSummary.builder()
                    .topicId("topic-1")
                    .name("Topic 1")
                    .compartmentId("comp-1")
                    .build()
            ))
            .opcNextPage("page-2-token")
            .build();

        // Page 2 returns 1 item and null next page token
        com.oracle.bmc.ons.responses.ListTopicsResponse page2 = com.oracle.bmc.ons.responses.ListTopicsResponse.builder()
            .items(List.of(
                com.oracle.bmc.ons.model.NotificationTopicSummary.builder()
                    .topicId("topic-2")
                    .name("Topic 2")
                    .compartmentId("comp-1")
                    .build()
            ))
            .opcNextPage(null)
            .build();

        org.mockito.Mockito.when(mockClient.listTopics(org.mockito.ArgumentMatchers.argThat(req -> req != null && req.getPage() == null)))
            .thenReturn(page1);
        org.mockito.Mockito.when(mockClient.listTopics(org.mockito.ArgumentMatchers.argThat(req -> req != null && "page-2-token".equals(req.getPage()))))
            .thenReturn(page2);

        ListTopics task = ListTopics.builder()
            .customControlPlaneClient(mockClient)
            .compartmentId(Property.of("comp-1"))
            .build();

        ListTopics.Output output = task.run(runContext);

        // Asserts pagination accumulated BOTH pages!
        assertThat(output.getCount(), is(2));
        assertThat(output.getTopics().size(), is(2));
        assertThat(output.getTopics().get(0).getTopicId(), is("topic-1"));
        assertThat(output.getTopics().get(1).getTopicId(), is("topic-2"));
    }

    @Test
    void testDeleteTopicRun() throws Exception {
        RunContext runContext = runContextFactory.of();
        com.oracle.bmc.ons.NotificationControlPlaneClient mockClient = org.mockito.Mockito.mock(com.oracle.bmc.ons.NotificationControlPlaneClient.class);

        com.oracle.bmc.ons.responses.DeleteTopicResponse mockResponse = com.oracle.bmc.ons.responses.DeleteTopicResponse.builder()
            .opcRequestId("req-del-123")
            .build();

        org.mockito.Mockito.when(mockClient.deleteTopic(org.mockito.ArgumentMatchers.any(com.oracle.bmc.ons.requests.DeleteTopicRequest.class)))
            .thenReturn(mockResponse);

        DeleteTopic task = DeleteTopic.builder()
            .customControlPlaneClient(mockClient)
            .topicId(Property.of("topic-to-del"))
            .build();

        DeleteTopic.Output output = task.run(runContext);

        assertThat(output.getTopicId(), is("topic-to-del"));
        assertThat(output.getOpcRequestId(), is("req-del-123"));
    }

    @Test
    void testCreateSubscriptionRun() throws Exception {
        RunContext runContext = runContextFactory.of();
        com.oracle.bmc.ons.NotificationDataPlaneClient mockClient = org.mockito.Mockito.mock(com.oracle.bmc.ons.NotificationDataPlaneClient.class);

        com.oracle.bmc.ons.responses.CreateSubscriptionResponse mockResponse = com.oracle.bmc.ons.responses.CreateSubscriptionResponse.builder()
            .subscription(com.oracle.bmc.ons.model.Subscription.builder()
                .id("sub-12345")
                .lifecycleState(com.oracle.bmc.ons.model.Subscription.LifecycleState.Active)
                .build())
            .build();

        org.mockito.Mockito.when(mockClient.createSubscription(org.mockito.ArgumentMatchers.any(com.oracle.bmc.ons.requests.CreateSubscriptionRequest.class)))
            .thenReturn(mockResponse);

        CreateSubscription task = CreateSubscription.builder()
            .customDataPlaneClient(mockClient)
            .topicId(Property.of("topic-1"))
            .compartmentId(Property.of("comp-1"))
            .protocol(Property.of("HTTPS"))
            .endpoint(Property.of("https://hooks.slack.com/services/secret/token"))
            .build();

        CreateSubscription.Output output = task.run(runContext);

        assertThat(output.getSubscriptionId(), is("sub-12345"));
        assertThat(output.getProtocol(), is("HTTPS"));
        assertThat(output.getLifecycleState(), is("ACTIVE"));
    }
}

