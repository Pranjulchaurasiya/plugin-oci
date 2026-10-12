package io.kestra.plugin.oci.ons;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class PublishMessageTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testOutputBuilder() {
        Instant now = Instant.now();
        PublishMessage.Output output = PublishMessage.Output.builder()
            .messageId("ocid1.onstopicmessage.oc1.fra.xxx")
            .timestamp(now)
            .build();

        assertThat(output.getMessageId(), is("ocid1.onstopicmessage.oc1.fra.xxx"));
        assertThat(output.getTimestamp(), is(now));
    }

    @Test
    void testPropertyRendering() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "testTopic", "ocid1.onstopic.oc1.fra.sampletopic",
            "testTitle", "Critical Alert",
            "testBody", "Service latency spiked above 500ms"
        ));

        PublishMessage task = PublishMessage.builder()
            .region(Property.of("eu-frankfurt-1"))
            .tenancyOcid(Property.of("ocid1.tenancy.oc1..sampletenancy"))
            .userId(Property.of("ocid1.user.oc1..sampleuser"))
            .fingerprint(Property.of("20:3b:97:13:55:1c:5b:0d:d3:37:d0:02:ee:3a:3c:22"))
            .privateKey(Property.of("-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----"))
            .topicId(Property.ofExpression("{{ testTopic }}"))
            .title(Property.ofExpression("{{ testTitle }}"))
            .body(Property.ofExpression("{{ testBody }}"))
            .build();

        assertThat(runContext.render(task.getTopicId()).as(String.class).orElse(null), is("ocid1.onstopic.oc1.fra.sampletopic"));
        assertThat(runContext.render(task.getTitle()).as(String.class).orElse(null), is("Critical Alert"));
        assertThat(runContext.render(task.getBody()).as(String.class).orElse(null), is("Service latency spiked above 500ms"));
    }

    @Test
    void testMissingCredentialsThrows() {
        RunContext runContext = runContextFactory.of();

        PublishMessage task = PublishMessage.builder()
            .topicId(Property.of("ocid1.onstopic.oc1.fra.sampletopic"))
            .body(Property.of("Test message"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}
