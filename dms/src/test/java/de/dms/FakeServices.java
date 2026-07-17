package de.dms;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-process stand-ins for the two Python document services (JDK HttpServer,
 * zero extra dependencies), so {@code mvn verify} stays hermetic while still
 * exercising the real HTTP clients — multipart encoding, bearer header,
 * response mapping. {@code /convert} always answers with a minimal valid PDF
 * plus a fixed text; {@code /extract} answers {@code status=unconfigured}
 * unless a test scripts the next response.
 */
public final class FakeServices {

    public static final String TOKEN = "test-service-token";
    public static final String CONVERSION_TEXT = "Rechnung Nr. 4711";
    public static final byte[] MINIMAL_PDF =
            "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    private final HttpServer server;
    private final Deque<String> extractResponses = new ConcurrentLinkedDeque<>();
    private volatile String lastConvertContentType;

    private FakeServices(HttpServer server) {
        this.server = server;
    }

    public static FakeServices start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            FakeServices fake = new FakeServices(server);
            server.createContext("/convert", fake::handleConvert);
            server.createContext("/extract", fake::handleExtract);
            server.start();
            return fake;
        } catch (IOException e) {
            throw new IllegalStateException("cannot start fake services", e);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    /** The next /extract call answers with this body instead of {@code unconfigured}. */
    public void enqueueExtractResponse(String body) {
        extractResponses.add(body);
    }

    /** Content type of the most recent /convert request — multipart proof. */
    public String lastConvertContentType() {
        return lastConvertContentType;
    }

    private void handleConvert(HttpExchange exchange) throws IOException {
        lastConvertContentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (unauthorized(exchange)) {
            return;
        }
        respond(exchange, 200, "{\"pdfBase64\":\"" + Base64.getEncoder().encodeToString(MINIMAL_PDF)
                + "\",\"text\":\"" + CONVERSION_TEXT + "\",\"producer\":\"passthrough\",\"ocrApplied\":false}");
    }

    private void handleExtract(HttpExchange exchange) throws IOException {
        if (unauthorized(exchange)) {
            return;
        }
        String scripted = extractResponses.poll();
        respond(exchange, 200, scripted != null ? scripted : "{\"status\":\"unconfigured\"}");
    }

    private boolean unauthorized(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes(); // drain the request
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (!("Bearer " + TOKEN).equals(authorization)) {
            respond(exchange, 401, "{\"error\":{\"code\":\"unauthorized\",\"message\":\"bad token\"}}");
            return true;
        }
        return false;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
