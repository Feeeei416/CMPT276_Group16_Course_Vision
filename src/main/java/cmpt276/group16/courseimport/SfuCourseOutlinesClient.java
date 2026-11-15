package cmpt276.group16.courseimport;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import cmpt276.group16.models.Season;

// The only class that calls the SFU Course Outlines API (https://www.sfu.ca/outlines/help/api.html).
// It returns the JSON as text and doesn't read it, so tests can replace this class with saved responses.
// A 404 means the thing asked for doesn't exist (for example a course not offered that term) and comes back
// as an empty Optional. Anything else that goes wrong throws SfuApiException
@Component 
public class SfuCourseOutlinesClient {
    private static final Logger log = LoggerFactory.getLogger(SfuCourseOutlinesClient.class);

    // Departments, course numbers and sections are only letters and digits, like "cmpt", "105w" or "d100".
    // Checked before building the URL, so a value can't add extra parts to the query
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9]+");

    private final RestClient restClient;
    private final String baseUrl;

    public SfuCourseOutlinesClient(RestClient sfuRestClient, @Value("${app.sfu-api.base-url}") String baseUrl) {
        this.restClient = sfuRestClient;
        this.baseUrl = baseUrl;
    }

    // The courses a department offers in a term, for example ?2027/spring/cmpt
    public Optional<String> listCourses(int year, Season season, String dept) {
        return get(term(year, season) + "/" + segment(dept));
    }

    // The sections of one course in a term, for example ?2027/spring/cmpt/225
    public Optional<String> listSections(int year, Season season, String dept, String number) {
        return get(term(year, season) + "/" + segment(dept) + "/" + segment(number));
    }

    // The outline of one section, with its course details and weekly schedule, for example ?2027/spring/cmpt/225/d100
    public Optional<String> getOutline(int year, Season season, String dept, String number, String section) {
        return get(term(year, season) + "/" + segment(dept) + "/" + segment(number) + "/" + segment(section));
    }
 
    private Optional<String> get(String query) {
        // The API puts the whole path in the query string, for example ...course-outlines?2027/spring/cmpt
        URI uri = URI.create(baseUrl + "?" + query);
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            log.info("GET {} ok", uri);
            return Optional.ofNullable(body);
        } catch (HttpClientErrorException.NotFound e) {
            log.info("GET {} not found", uri);
            return Optional.empty();
        } catch (RestClientException e) {
            // Server errors, other client errors, timeouts and connection failures
            log.warn("GET {} failed: {}", uri, e.getMessage());
            throw new SfuApiException("Could not get " + uri + " from the SFU Course Outlines API", e);
        }
    }
 
    // "2027/spring": the API writes terms in lowercase
    private static String term(int year, Season season) {
        return year + "/" + season.name().toLowerCase(Locale.ROOT);
    }
 
    // "CMPT" -> "cmpt", "376W" -> "376w"; anything that isn't only letters and digits is turned down
    private static String segment(String value) {
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (!SEGMENT.matcher(lower).matches()) {
            throw new IllegalArgumentException("Not a valid department, course number or section: " + value);
        }
        return lower;
    }

}
