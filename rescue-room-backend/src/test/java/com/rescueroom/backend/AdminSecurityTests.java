package com.rescueroom.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class AdminSecurityTestsFailClosed {

    @Test
    void missingUsernameOrPasswordFailsStartup() {
        assertThatThrownBy(() -> {
            SpringApplication.run(RescueRoomBackendApplication.class,
                    "--spring.datasource.url=jdbc:h2:mem:failclosed-test;DB_CLOSE_DELAY=-1",
                    "--RESCUE_ADMIN_PASSWORD=somepass"
            );
        }).hasCauseInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> {
            SpringApplication.run(RescueRoomBackendApplication.class,
                    "--spring.datasource.url=jdbc:h2:mem:failclosed-test2;DB_CLOSE_DELAY=-1",
                    "--RESCUE_ADMIN_USERNAME=adminuser"
            );
        }).hasCauseInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> {
            SpringApplication.run(RescueRoomBackendApplication.class,
                    "--spring.datasource.url=jdbc:h2:mem:failclosed-test3;DB_CLOSE_DELAY=-1",
                    "--RESCUE_ADMIN_USERNAME=",
                    "--RESCUE_ADMIN_PASSWORD=   "
            );
        }).hasCauseInstanceOf(IllegalStateException.class);
    }
}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:security-tests;DB_CLOSE_DELAY=-1",
        "RESCUE_ADMIN_USERNAME=adminuser",
        "RESCUE_ADMIN_PASSWORD=adminpass"
})
class AdminSecurityTests {
    @LocalServerPort int port;
    final HttpClient http = HttpClient.newHttpClient();

    @Test
    void publicEndpointsAreAccessibleAnonymously() throws Exception {
        var resRoot = http.send(HttpRequest.newBuilder(URI.create(base() + "/")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(resRoot.statusCode()).isEqualTo(200);

        var resStart = http.send(HttpRequest.newBuilder(URI.create(base() + "/api/sessions/start")).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(resStart.statusCode()).isEqualTo(201);
    }

    @Test
    void adminEndpointsAreSecured() throws Exception {
        // Anonymous request should be unauthorized (401)
        var resAdmin = http.send(HttpRequest.newBuilder(URI.create(base() + "/admin")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(resAdmin.statusCode()).isEqualTo(401);

        var resStats = http.send(HttpRequest.newBuilder(URI.create(base() + "/api/admin/statistics")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(resStats.statusCode()).isEqualTo(401);

        // Wrong credentials should be unauthorized (401)
        var wrongReq = HttpRequest.newBuilder(URI.create(base() + "/api/admin/statistics"))
                .header("Authorization", basic("adminuser", "wrongpass"))
                .GET().build();
        assertThat(http.send(wrongReq, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);

        // Correct credentials should succeed (200)
        var correctReq = HttpRequest.newBuilder(URI.create(base() + "/api/admin/statistics"))
                .header("Authorization", basic("adminuser", "adminpass"))
                .GET().build();
        assertThat(http.send(correctReq, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
    }

    String base() { return "http://127.0.0.1:" + port; }
    String basic(String user, String pass) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
    }
}
