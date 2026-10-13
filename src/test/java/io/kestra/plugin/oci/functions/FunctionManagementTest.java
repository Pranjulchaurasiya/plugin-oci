package io.kestra.plugin.oci.functions;

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
class FunctionManagementTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void testGetFunctionOutput() {
        Instant now = Instant.now();
        GetFunction.Output output = GetFunction.Output.builder()
            .id("ocid1.fnfunc.oc1.fra.xxx")
            .displayName("payment-worker")
            .applicationId("ocid1.fnapp.oc1.fra.yyy")
            .compartmentId("ocid1.compartment.oc1..zzz")
            .image("fra.ocir.io/tenancy/payment:1.0")
            .memoryInMBs(256L)
            .timeoutInSeconds(60)
            .invokeEndpoint("https://fn.fra.oraclecloud.com")
            .lifecycleState("ACTIVE")
            .timeCreated(now)
            .timeUpdated(now)
            .build();

        assertThat(output.getId(), is("ocid1.fnfunc.oc1.fra.xxx"));
        assertThat(output.getDisplayName(), is("payment-worker"));
        assertThat(output.getMemoryInMBs(), is(256L));
        assertThat(output.getTimeoutInSeconds(), is(60));
        assertThat(output.getLifecycleState(), is("ACTIVE"));
    }

    @Test
    void testListFunctionsOutput() {
        ListFunctions.FunctionSummaryItem item = ListFunctions.FunctionSummaryItem.builder()
            .id("ocid1.fnfunc.oc1.fra.111")
            .displayName("order-hook")
            .applicationId("ocid1.fnapp.oc1.fra.222")
            .image("fra.ocir.io/tenancy/hook:latest")
            .memoryInMBs(128L)
            .timeoutInSeconds(30)
            .lifecycleState("ACTIVE")
            .build();

        ListFunctions.Output output = ListFunctions.Output.builder()
            .functions(List.of(item))
            .count(1)
            .build();

        assertThat(output.getCount(), is(1));
        assertThat(output.getFunctions().getFirst().getDisplayName(), is("order-hook"));
    }

    @Test
    void testCreateFunctionOutput() {
        CreateFunction.Output output = CreateFunction.Output.builder()
            .id("ocid1.fnfunc.oc1.fra.newfn")
            .displayName("new-fn")
            .invokeEndpoint("https://fn.endpoint")
            .lifecycleState("ACTIVE")
            .build();

        assertThat(output.getId(), is("ocid1.fnfunc.oc1.fra.newfn"));
        assertThat(output.getDisplayName(), is("new-fn"));
        assertThat(output.getLifecycleState(), is("ACTIVE"));
    }

    @Test
    void testDeleteFunctionOutput() {
        DeleteFunction.Output output = DeleteFunction.Output.builder()
            .functionId("ocid1.fnfunc.oc1.fra.delfn")
            .opcRequestId("req-del-789")
            .build();

        assertThat(output.getFunctionId(), is("ocid1.fnfunc.oc1.fra.delfn"));
        assertThat(output.getOpcRequestId(), is("req-del-789"));
    }

    @Test
    void testCreateFunctionPropertyRendering() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of(
            "appId", "ocid1.fnapp.oc1.fra.myapp",
            "fnName", "webhook-handler",
            "img", "fra.ocir.io/tenancy/webhook:2.0"
        ));

        CreateFunction task = CreateFunction.builder()
            .region(Property.of("eu-frankfurt-1"))
            .tenancyOcid(Property.of("ocid1.tenancy.oc1..sampletenancy"))
            .userId(Property.of("ocid1.user.oc1..sampleuser"))
            .fingerprint(Property.of("20:3b:97:13:55:1c:5b:0d:d3:37:d0:02:ee:3a:3c:22"))
            .privateKey(Property.of("-----BEGIN RSA PRIVATE KEY-----\nMIIE...\n-----END RSA PRIVATE KEY-----"))
            .applicationId(Property.ofExpression("{{ appId }}"))
            .displayName(Property.ofExpression("{{ fnName }}"))
            .image(Property.ofExpression("{{ img }}"))
            .memoryInMBs(Property.of(512L))
            .timeoutInSeconds(Property.of(120))
            .build();

        assertThat(runContext.render(task.getApplicationId()).as(String.class).orElse(null), is("ocid1.fnapp.oc1.fra.myapp"));
        assertThat(runContext.render(task.getDisplayName()).as(String.class).orElse(null), is("webhook-handler"));
        assertThat(runContext.render(task.getImage()).as(String.class).orElse(null), is("fra.ocir.io/tenancy/webhook:2.0"));
        assertThat(runContext.render(task.getMemoryInMBs()).as(Long.class).orElse(null), is(512L));
        assertThat(runContext.render(task.getTimeoutInSeconds()).as(Integer.class).orElse(null), is(120));
    }
}
