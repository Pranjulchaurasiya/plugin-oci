package io.kestra.plugin.oci;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.StringPrivateKeySupplier;
import com.oracle.bmc.ons.NotificationControlPlaneClient;
import com.oracle.bmc.ons.NotificationDataPlaneClient;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractOciTask extends Task {
    @Schema(title = "The OCI region identifier (e.g. 'eu-frankfurt-1', 'us-ashburn-1')")
    @NotNull
    @PluginProperty(group = "connection")
    protected Property<String> region;

    @Schema(title = "The OCID of the tenancy")
    @NotNull
    @PluginProperty(group = "connection")
    protected Property<String> tenancyOcid;

    @Schema(title = "The OCID of the calling user")
    @NotNull
    @PluginProperty(group = "connection")
    protected Property<String> userId;

    @Schema(title = "Fingerprint for the key pair being used")
    @NotNull
    @PluginProperty(group = "connection")
    protected Property<String> fingerprint;

    @Schema(title = "The private key in PEM format (or private key content)")
    @NotNull
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @PluginProperty(group = "connection", secret = true)
    protected Property<String> privateKey;

    @Schema(title = "Passphrase used to decrypt the private key, if encrypted")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @PluginProperty(group = "advanced", secret = true)
    protected Property<String> passphrase;

    protected AuthenticationDetailsProvider getAuthenticationDetailsProvider(RunContext runContext) throws IllegalVariableEvaluationException {
        String rTenancy = runContext.render(this.tenancyOcid).as(String.class).orElseThrow();
        String rUser = runContext.render(this.userId).as(String.class).orElseThrow();
        String rFingerprint = runContext.render(this.fingerprint).as(String.class).orElseThrow();
        String rKey = runContext.render(this.privateKey).as(String.class).orElseThrow();
        String rPassphrase = runContext.render(this.passphrase).as(String.class).orElse(null);
        String rRegion = runContext.render(this.region).as(String.class).orElseThrow();

        SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder builder = SimpleAuthenticationDetailsProvider.builder()
            .tenantId(rTenancy)
            .userId(rUser)
            .fingerprint(rFingerprint)
            .privateKeySupplier(new StringPrivateKeySupplier(rKey))
            .region(Region.fromRegionId(rRegion));

        if (rPassphrase != null && !rPassphrase.isBlank()) {
            builder.passphraseCharacters(rPassphrase.toCharArray());
        }

        return builder.build();
    }

    protected NotificationDataPlaneClient createDataPlaneClient(RunContext runContext) throws IllegalVariableEvaluationException {
        return NotificationDataPlaneClient.builder().build(getAuthenticationDetailsProvider(runContext));
    }

    protected NotificationControlPlaneClient createControlPlaneClient(RunContext runContext) throws IllegalVariableEvaluationException {
        return NotificationControlPlaneClient.builder().build(getAuthenticationDetailsProvider(runContext));
    }
}

