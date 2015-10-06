package com.englishtown.vertx.promises.integration;


import com.englishtown.promises.Promise;
import com.englishtown.promises.When;
import com.englishtown.promises.WhenFactory;
import com.englishtown.vertx.promises.RequestOptions;
import com.englishtown.vertx.promises.WhenHttpClient;
import com.englishtown.vertx.promises.WhenVertx;
import com.englishtown.vertx.promises.impl.DefaultWhenHttpClient;
import com.englishtown.vertx.promises.impl.DefaultWhenVertx;
import com.englishtown.vertx.promises.impl.VertxExecutor;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import io.vertx.test.core.VertxTestBase;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.vertx.core.http.HttpMethod.GET;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;

@RunWith(Parameterized.class)
public class DefaultWhenHttpClientIntegrationTest extends VertxTestBase {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {aResponse().withStatus(500), 500},
                {aResponse().withFixedDelay(2000), "timeout"},
                {aResponse().withFault(Fault.EMPTY_RESPONSE), "Connection was closed"},
                {aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE), "Connection was closed"},
                {aResponse().withStatus(400), 400}
        });
    }

    private ResponseDefinitionBuilder responseDefinitionBuilder;

    private Object expectedOutput;

    public DefaultWhenHttpClientIntegrationTest(ResponseDefinitionBuilder input, Object expected) {
        responseDefinitionBuilder = input;
        expectedOutput = expected;
    }

    @ClassRule
    public static WireMockClassRule wireMockClassRule = new WireMockClassRule(8081);

    @Before
    public void setup() {
        VertxExecutor executor = new VertxExecutor(vertx);
        when = WhenFactory.createFor(() -> executor);
        whenVertx = new DefaultWhenVertx(vertx, when);
        whenHttpClient = new DefaultWhenHttpClient(vertx, when);
    }

    protected When when;
    protected WhenVertx whenVertx;
    protected WhenHttpClient whenHttpClient;

    protected Promise<Void> onRejected(Throwable t) {
        t.printStackTrace();
        assertThat(t.getMessage(), containsString(expectedOutput.toString()));
        testComplete();
        return null;
    }

    @Test
    public void testHttpRequest() {
        stubFor(get(urlEqualTo("/my/resource"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(responseDefinitionBuilder));

        RequestOptions options = new RequestOptions()
                .setSetupHandler(request -> {
                    assertNotNull(request);
                    return null;
                })
                .setTimeout(1000)
                .addHeader("Accept", "application/json");

        whenHttpClient.requestAbs(GET, "http://localhost:8081/my/resource", options)
                .then(response -> {
                    assertThat(expectedOutput, is(response.statusCode()));
                    testComplete();
                    return null;
                })
                .otherwise(this::onRejected);

        await();
    }
}

