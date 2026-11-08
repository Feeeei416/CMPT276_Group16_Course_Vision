package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

// Adding (#45) courses on the My courses page
// Similar setup as the other controller tests: a MockHttpSession + each test's changes are rolled back
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class ManualCoursesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private TranscriptCourseRepository transcriptCourseRepository;

    private StudentProfile jack;
    private MockHttpSession session;

    @BeforeEach
    void logInAsJack() {
        jack = saveStudent("Jack@example.com", "Jack");
        session = sessionFor(jack.getUser());
    }

    private StudentProfile saveStudent(String email, String name) {
        User user = userRepository.save(new User(email, "hash", name, Role.STUDENT));
        return studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private MockHttpSession sessionFor(User user) {
        MockHttpSession s = new MockHttpSession();
        s.setAttribute(LoginController.SESSION_USER, user);
        return s;
    }

    private TranscriptCourse saveCourse(StudentProfile student, String term, String number, String grade) {
        return transcriptCourseRepository.save(new TranscriptCourse(student, term, "CMPT", number, "Some Course",
                new BigDecimal("3.00"), grade));
    }

    // filled-in Add/Edit course form, overrides are field/value pairs that replace the defaults
    // (calling .param() afterwards would add a second value instead of replacing the first)
    private static MockHttpServletRequestBuilder courseForm(MockHttpServletRequestBuilder request, String... overrides) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("year", "2024");
        form.put("season", "Summer");
        form.put("subject", "MATH");
        form.put("number", "151");
        form.put("title", "Calculus I");
        form.put("units", "3");
        form.put("grade", "B+");
        for (int i = 0; i < overrides.length; i += 2) {
            form.put(overrides[i], overrides[i + 1]);
        }
        form.forEach(request::param);
        return request;
    }

    private List<TranscriptCourse> coursesOf(StudentProfile student) {
        return transcriptCourseRepository.findByStudentOrderByIdAsc(student);
    }

    // add a course to my record by hand

    @Test
    void myCoursesHasAnAddCourseForm() throws Exception {
        mockMvc.perform(get("/student/courses").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/student/courses\"")))
                .andExpect(content().string(containsString("name=\"season\"")))
                .andExpect(content().string(containsString("name=\"grade\"")))
                .andExpect(content().string(containsString(">Transfer credit</option>")));
    }

    @Test
    void addingSavesAManualCourse() throws Exception {
        mockMvc.perform(courseForm(post("/student/courses")).session(session))
                .andExpect(redirectedUrl("/student/courses"))
                .andExpect(flash().attribute("message", "Added MATH 151 to 2024 Summer."));

        TranscriptCourse saved = coursesOf(jack).get(0);
        assertThat(saved.getTerm()).isEqualTo("2024 Summer");
        assertThat(saved.getSubject()).isEqualTo("MATH");
        assertThat(saved.getNumber()).isEqualTo("151");
        assertThat(saved.getTitle()).isEqualTo("Calculus I");
        assertThat(saved.getUnits()).isEqualByComparingTo("3.00");
        assertThat(saved.getGrade()).isEqualTo("B+");
        assertThat(saved.getSource()).isEqualTo(TranscriptCourse.Source.MANUAL);
    }

    // the grade can be left empty, or marked as transfer credit
    @Test
    void gradeCanBeInProgressOrTransferCredit() throws Exception {
        mockMvc.perform(courseForm(post("/student/courses"), "grade", "").session(session));
        mockMvc.perform(courseForm(post("/student/courses"), "number", "1XX", "grade", "TR")
                .session(session));

        assertThat(coursesOf(jack)).extracting(TranscriptCourse::getGrade).containsExactly(null, "TR");

        String page = mockMvc.perform(get("/student/courses").session(session))
                .andReturn().getResponse().getContentAsString();
        assertThat(CourseTableRows.tableRows(page)).extracting(row -> row.get(5))
                .containsExactly("In progress", "Transfer credit");
    }

    // the new course shows up under the right term, even when it's older than the courses already saved
    @Test
    void addedCourseShowsUnderItsTermInOrder() throws Exception {
        saveCourse(jack, "2025 Fall", "120", "A-");

        mockMvc.perform(courseForm(post("/student/courses")).session(session));

        String page = mockMvc.perform(get("/student/courses").session(session))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).containsSubsequence(
                "<h2 class=\"section-label\">2024 Summer</h2>", "MATH",
                "<h2 class=\"section-label\">2025 Fall</h2>", "CMPT");
    }

    // bad input shows an error and saves nothing
    @Test
    void invalidFormShowsErrorsAndSavesNothing() throws Exception {
        mockMvc.perform(courseForm(post("/student/courses"), "units", "three", "grade", "E", "title", "")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("student/view-courses"))
                .andExpect(content().string(containsString("Title is required.")))
                .andExpect(content().string(containsString("Units must be a number")))
                .andExpect(content().string(containsString("Choose a grade from the list.")))
                // The form stays open with what the student typed
                .andExpect(content().string(containsString("<details class=\"collapsible\" open")))
                .andExpect(content().string(containsString("value=\"three\"")));

        assertThat(coursesOf(jack)).isEmpty();
    }

    // students can only add courses to their own record
    @Test
    void addAlwaysGoesToTheSignedInStudent() throws Exception {
        StudentProfile jake = saveStudent("jake@example.com", "Jake");

        mockMvc.perform(courseForm(post("/student/courses")).param("student", jake.getUserId().toString())
                .param("studentId", jake.getUserId().toString()).session(session));

        assertThat(coursesOf(jack)).hasSize(1);
        assertThat(coursesOf(jake)).isEmpty();
    }

    // A student with no courses yet still needs a way to the Add course form
    @Test
    void homeLinksToMyCoursesEvenWithNoCourses() throws Exception {
        mockMvc.perform(get("/student/home").session(session))
                .andExpect(content().string(containsString("No courses yet")))
                .andExpect(content().string(containsString("href=\"/student/courses\"")));
    }

    @Test
    void addingNeedsAStudentLogin() throws Exception {
        mockMvc.perform(courseForm(post("/student/courses"))).andExpect(redirectedUrl("/login"));

        User advisor = userRepository.save(new User("advisor@example.com", "hash", "Advisor", Role.ADVISOR));
        mockMvc.perform(courseForm(post("/student/courses")).session(sessionFor(advisor)))
                .andExpect(redirectedUrl("/advisor/home"));
    }
}
