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
            .endpoint("https://webhook.site/sample")
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
}
