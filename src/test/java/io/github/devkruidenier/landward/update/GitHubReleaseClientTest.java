package io.github.devkruidenier.landward.update;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class GitHubReleaseClientTest {
    @Test
    void everyRequestGetsFreshMetadataAndNeverFallsBackToAnOldRelease() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        AtomicBoolean revalidate = new AtomicBoolean(true);
        server.createContext("/latest", exchange -> {
            int request = requests.incrementAndGet();
            revalidate.set(revalidate.get() && "no-cache".equals(exchange.getRequestHeaders().getFirst("Cache-Control")));
            String version = request == 1 ? "1.0.1.5" : "1.0.1.6";
            byte[] body = ("{\"tag_name\":\"" + version + "\",\"assets\":["
                    + UpdateCheckerMetadataTest.asset(version, "LandWard-" + version + ".jar") + "]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(request == 3 ? 503 : 200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var client = new GitHubReleaseClient(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/latest"), "LandWard test");
            assertEquals("1.0.1.5", client.latest().join().version());
            var fresh = client.latest().join();
            assertEquals("1.0.1.6", fresh.version());
            assertEquals("LandWard-1.0.1.6.jar", fresh.asset().name());
            assertThrows(java.util.concurrent.CompletionException.class, () -> client.latest().join());
            assertTrue(revalidate.get());
        } finally { server.stop(0); }
    }
}
