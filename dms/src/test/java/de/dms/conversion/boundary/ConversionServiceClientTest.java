package de.dms.conversion.boundary;

import de.dms.FakeServices;
import de.dms.crosscutting.platform.control.DmsProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversionServiceClientTest {

    private static FakeServices fake;

    @BeforeAll
    static void start() {
        fake = FakeServices.start();
    }

    @AfterAll
    static void stop() {
        fake.stop();
    }

    private static DmsProperties properties(String url, String token, boolean workerEnabled) {
        return new DmsProperties(null, null, null,
                new DmsProperties.Worker(workerEnabled, 2000, 2, 5, 5000, 300),
                null,
                new DmsProperties.Services(token,
                        new DmsProperties.Services.Endpoint(url, 10, 300),
                        new DmsProperties.Services.Endpoint("", 10, 180)),
                null, null);
    }

    @Test
    void convertSendsMultipartWithBearerAndMapsTheResponse() {
        ConversionServiceClient client =
                new ConversionServiceClient(properties(fake.baseUrl(), FakeServices.TOKEN, true));
        ConversionServiceClient.ConversionResult result =
                client.convert("rechnung.pdf", "application/pdf", new byte[] {1, 2, 3});
        assertThat(result.pdfa()).isEqualTo(FakeServices.MINIMAL_PDF);
        assertThat(result.text()).isEqualTo(FakeServices.CONVERSION_TEXT);
        assertThat(fake.lastConvertContentType()).startsWith("multipart/form-data");
    }

    @Test
    void badTokenSurfacesAsConversionFailure() {
        ConversionServiceClient client =
                new ConversionServiceClient(properties(fake.baseUrl(), "wrong-token", true));
        assertThatThrownBy(() -> client.convert("x.pdf", "application/pdf", new byte[] {1}))
                .isInstanceOf(ConversionServiceClient.ConversionFailedException.class);
    }

    @Test
    void unreachableServiceSurfacesAsConversionFailure() {
        ConversionServiceClient client =
                new ConversionServiceClient(properties("http://127.0.0.1:1", FakeServices.TOKEN, true));
        assertThatThrownBy(() -> client.convert("x.pdf", "application/pdf", new byte[] {1}))
                .isInstanceOf(ConversionServiceClient.ConversionFailedException.class);
    }

    @Test
    void enabledWorkerWithoutServiceUrlFailsFastAtStartup() {
        ConversionServiceClient client = new ConversionServiceClient(properties("", FakeServices.TOKEN, true));
        assertThatThrownBy(client::verifyMandatoryConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DMS_CONVERSION_URL");
    }

    @Test
    void disabledWorkerToleratesMissingServiceUrl() {
        ConversionServiceClient client =
                new ConversionServiceClient(properties("", FakeServices.TOKEN, false));
        client.verifyMandatoryConfiguration(); // no throw: worker disabled
        assertThatThrownBy(() -> client.convert("x.pdf", "application/pdf", new byte[] {1}))
                .isInstanceOf(ConversionServiceClient.ConversionFailedException.class);
    }
}
