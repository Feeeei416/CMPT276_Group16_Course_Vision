package cmpt276.group16.courseimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import cmpt276.group16.models.Season;

// No Spring context and no real API: MockRestServiceServer answers the requests instead of SFU
class SfuCourseOutlinesClientTest {
    
    private static final String BASE = "https://www.sfu.ca/bin/wcm/course-outlines";
 
    private MockRestServiceServer server;
    private SfuCourseOutlinesClient client;
 
    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SfuCourseOutlinesClient(builder.build(), BASE);
    }

        // A 200 answer comes back as its JSON text, from the URL the API expects
    @Test
    void returnsBodyForFoundSections() {
        String json = "[{\"text\":\"D100\",\"value\":\"d100\"}]";
        server.expect(requestTo(BASE + "?2027/spring/cmpt/225"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
 
        assertThat(client.listSections(2027, Season.SPRING, "cmpt", "225")).contains(json);
        server.verify();
    }
 
    // Codes from our database are uppercase, but the API wants lowercase
    @Test
    void lowercasesEveryPartOfTheUrl() {
        server.expect(requestTo(BASE + "?2027/fall/cmpt/376w/d200"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
 
        assertThat(client.getOutline(2027, Season.FALL, "CMPT", "376W", " D200 ")).contains("{}");
        server.verify();
    }
 
    // The course list of a department uses the shortest URL
    @Test
    void listsCoursesOfADepartment() {
        server.expect(requestTo(BASE + "?2027/summer/math"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
 
        assertThat(client.listCourses(2027, Season.SUMMER, "MATH")).contains("[]");
        server.verify();
    }
 
    // 404 means it doesn't exist, which the import skips, so it isn't an error
    @Test
    void notFoundIsEmpty() {
        server.expect(requestTo(BASE + "?2027/spring/cmpt/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorMessage\":\"Invalid Query String or Object does not exist.\"}"));
 
        assertThat(client.listSections(2027, Season.SPRING, "cmpt", "999")).isEmpty();
        server.verify();
    }
 
    // A server error stops the import
    @Test
    void serverErrorThrows() {
        server.expect(requestTo(BASE + "?2027/spring/cmpt"))
                .andRespond(withServerError());
 
        assertThatThrownBy(() -> client.listCourses(2027, Season.SPRING, "cmpt"))
                .isInstanceOf(SfuApiException.class);
    }
 
    // So does a network failure, for example a timeout or no connection
    @Test
    void networkFailureThrows() {
        server.expect(requestTo(BASE + "?2027/spring/cmpt"))
                .andRespond(withException(new IOException("Connection refused")));
 
        assertThatThrownBy(() -> client.listCourses(2027, Season.SPRING, "cmpt"))
                .isInstanceOf(SfuApiException.class);
    }
 
    // A value that isn't only letters and digits is turned down before any request is sent
    @Test
    void rejectsUnsafeParts() {
        assertThatThrownBy(() -> client.listSections(2027, Season.SPRING, "cmpt", "../x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.listCourses(2027, Season.SPRING, "cm pt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getOutline(2027, Season.SPRING, "cmpt", "225", "d100&x=1"))
                .isInstanceOf(IllegalArgumentException.class);
        // No expectations were set, so verify fails if any request was sent
        server.verify();
    }
}
