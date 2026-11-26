package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

// MockMvc doesn't go through Spring Boot's error handling, so this starts the real server.
// Asks for HTML like a browser does; other clients get Spring Boot's JSON error instead
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorPageTest {

    @LocalServerPort
    private int port;

    private ResponseEntity<String> request(HttpMethod method, String path) {
        return RestClient.create("http://localhost:" + port).method(method)
                .uri(path)
                .accept(MediaType.TEXT_HTML)
                .retrieve()
                // Error statuses are what's being tested, so don't throw on them
                .onStatus(status -> true, (req, res) -> { })
                .toEntity(String.class);
    }

    private static void assertFriendly(ResponseEntity<String> response) {
        assertThat(response.getBody())
                .contains("Course Vision")
                .contains("Go to home")
                .doesNotContain("Whitelabel")
                .doesNotContain("Exception")
                .doesNotContain("at org.springframework");
    }

    @Test
    void addressThatDoesntExistShowsTheFriendlyPage() {
        ResponseEntity<String> response = request(HttpMethod.GET, "/no-such-page");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("We couldn't find that page").contains("Error 404");
        assertFriendly(response);
    }

    // Sign out only takes a form post, so typing /logout in the address bar is a GET it doesn't accept
    @Test
    void wrongKindOfRequestShowsTheFriendlyPage() {
        ResponseEntity<String> response = request(HttpMethod.GET, "/logout");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).contains("That link doesn't work like that").contains("Error 405");
        assertFriendly(response);
    }

    @Test
    void homeLinkGoesToTheLoginPageWhichSendsSignedInUsersHome() {
        assertThat(request(HttpMethod.GET, "/no-such-page").getBody()).contains("href=\"/login\"");
    }
}
