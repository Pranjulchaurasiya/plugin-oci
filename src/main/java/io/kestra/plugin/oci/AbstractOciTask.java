package io.kestra.plugin.oci;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.StringPrivateKeySupplier;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
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
    protected Property<String> region;

    @Schema(title = "The OCID of the tenancy")
    @NotNull
    protected Property<String> tenancyOcid;

    @Schema(title = "The OCID of the calling user")
    @NotNull
    protected Property<String> userId;

    @Schema(title = "Fingerprint for the key pair being used")
    @NotNull
    protected Property<String> fingerprint;

    @Schema(title = "The private key in PEM format (or private key content)")
    @NotNull
    @io.kestra.core.models.annotations.PluginProperty(group = "connection", secret = true)
    protected Property<String> privateKey;

    @Schema(title = "Passphrase used to decrypt the private key, if encrypted")
    @io.kestra.core.models.annotations.PluginProperty(group = "connection", secret = true)
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
}
