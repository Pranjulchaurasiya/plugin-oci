package io.kestra.plugin.oci.ons;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.ons.NotificationControlPlaneClient;
import com.oracle.bmc.ons.model.NotificationTopicSummary;
import com.oracle.bmc.ons.requests.ListTopicsRequest;
import com.oracle.bmc.ons.responses.ListTopicsResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.oci.AbstractOciTask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List OCI Notification Service (ONS) topics",
    description = "Retrieves all notification topics in a given compartment."
)
@Plugin(
    examples = {
        @Example(
            title = "List all ONS topics in a compartment",
            full = true,
            code = """
                id: list_ons_topics
                namespace: company.ops

                tasks:
                  - id: list_topics
                    type: io.kestra.plugin.oci.ons.ListTopics
                    region: eu-frankfurt-1
                    tenancyOcid: "{{ secret('OCI_TENANCY_OCID') }}"
                    userId: "{{ secret('OCI_USER_OCID') }}"
                    fingerprint: "{{ secret('OCI_FINGERPRINT') }}"
                    privateKey: "{{ secret('OCI_PRIVATE_KEY') }}"
                    compartmentId: "{{ secret('OCI_COMPARTMENT_OCID') }}"

                  - id: log_count
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.list_topics.count }} ONS topics"
                """
        )
    }
)
public class ListTopics extends AbstractOciTask implements RunnableTask<ListTopics.Output> {
    @Schema(title = "The OCID of the compartment to list topics from")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "main")
    private Property<String> compartmentId;

    @Schema(title = "Maximum number of topics to retrieve")
    @io.kestra.core.models.annotations.PluginProperty(group = "advanced")
    private Property<Integer> limit;

    @Override
    public Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();
        AuthenticationDetailsProvider authProvider = this.getAuthenticationDetailsProvider(runContext);

        String rCompartmentId = runContext.render(this.compartmentId).as(String.class).orElseThrow();
        Integer rLimit = this.limit != null ? runContext.render(this.limit).as(Integer.class).orElse(null) : null;

        logger.debug("Listing OCI ONS topics in compartment {}", rCompartmentId);

        List<TopicSummary> topics = new ArrayList<>();
        String page = null;

        try (NotificationControlPlaneClient client = this.createControlPlaneClient(authProvider)) {
            do {
                ListTopicsRequest.Builder requestBuilder = ListTopicsRequest.builder()
                    .compartmentId(rCompartmentId);

                if (page != null) {
                    requestBuilder.page(page);
                }

                ListTopicsResponse response = client.listTopics(requestBuilder.build());

                if (response.getItems() != null) {
                    for (NotificationTopicSummary summary : response.getItems()) {
                        topics.add(TopicSummary.builder()
                            .topicId(summary.getTopicId())
                            .name(summary.getName())
                            .compartmentId(summary.getCompartmentId())
                            .description(summary.getDescription())
                            .lifecycleState(summary.getLifecycleState() != null ? summary.getLifecycleState().getValue() : null)
                            .timeCreated(summary.getTimeCreated() != null ? summary.getTimeCreated().toInstant() : null)
                            .build());

                        if (rLimit != null && topics.size() >= rLimit) {
                            break;
                        }
                    }
                }

                if (rLimit != null && topics.size() >= rLimit) {
                    break;
                }

                page = response.getOpcNextPage();
            } while (page != null && !page.isBlank());

            logger.info("Found {} OCI ONS topics in compartment {}", topics.size(), rCompartmentId);

            return Output.builder()
                .topics(topics)
                .count(topics.size())
                .build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "List of topic summaries retrieved")
        private final List<TopicSummary> topics;

        @Schema(title = "Total number of topics found")
        private final int count;
    }

    @Builder
    @Getter
    public static class TopicSummary {
        @Schema(title = "The OCID of the topic")
        private final String topicId;

        @Schema(title = "The name of the topic")
        private final String name;

        @Schema(title = "The compartment OCID")
        private final String compartmentId;

        @Schema(title = "The description of the topic")
        private final String description;

        @Schema(title = "The lifecycle state")
        private final String lifecycleState;

        @Schema(title = "The timestamp when the topic was created")
        private final Instant timeCreated;
    }
}
