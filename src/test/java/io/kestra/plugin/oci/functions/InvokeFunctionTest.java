package io.kestra.plugin.oci.functions;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class InvokeFunctionTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testOutputBuilder() {
        InvokeFunction.Output output = InvokeFunction.Output.builder()
            .statusCode(200)
            .body("{\"status\":\"success\",\"recordsProcessed\":42}")
            .opcRequestId("req-12345-67890")
            .build();

        assertThat(output.getStatusCode(), is(200));
        assertThat(output.getBody(), containsString("recordsProcessed"));
        assertThat(output.getOpcRequestId(), is("req-12345-67890"));
    }

    @Test
    void testPropertyRendering() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "testFnId", "ocid1.fnfunc.oc1.fra.samplefunction",
            "testEndpoint", "https://sample.functions.eu-frankfurt-1.oci.oraclecloud.com",
            "testRegion", "eu-frankfurt-1"
        ));

        InvokeFunction task = InvokeFunction.builder()
            .region(Property.ofExpression("{{ testRegion }}"))
            .tenancyOcid(Property.of("ocid1.tenancy.oc1..sampletenancy"))
            .userId(Property.of("ocid1.user.oc1..sampleuser"))
            .fingerprint(Property.of("20:3b:97:13:55:1c:5b:0d:d3:37:d0:02:ee:3a:3c:22"))
            .privateKey(Property.of("-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----"))
            .functionId(Property.ofExpression("{{ testFnId }}"))
            .invokeEndpoint(Property.ofExpression("{{ testEndpoint }}"))
            .body(Property.of(Map.of("action", "sync")))
            .build();

        assertThat(runContext.render(task.getRegion()).as(String.class).orElse(null), is("eu-frankfurt-1"));
        assertThat(runContext.render(task.getFunctionId()).as(String.class).orElse(null), is("ocid1.fnfunc.oc1.fra.samplefunction"));
        assertThat(runContext.render(task.getInvokeEndpoint()).as(String.class).orElse(null), is("https://sample.functions.eu-frankfurt-1.oci.oraclecloud.com"));
    }

    @Test
    void testMissingCredentialsThrows() {
        RunContext runContext = runContextFactory.of();

        InvokeFunction task = InvokeFunction.builder()
            .functionId(Property.of("ocid1.fnfunc.oc1.fra.samplefunction"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContext));
    }
}
