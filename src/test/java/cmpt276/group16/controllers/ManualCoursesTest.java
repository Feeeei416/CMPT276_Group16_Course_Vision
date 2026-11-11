package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

// Adding (#45), editing and removing (#46) courses on the My courses page
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

    // A second copy of a course would count its units twice
    @Test
    void addingACourseAlreadyInTheRecordSavesNothing() throws Exception {
        saveCourse(jack, "2024 Summer", "225", "B");

        mockMvc.perform(courseForm(post("/student/courses"), "subject", "cmpt", "number", "225").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("student/view-courses"))
                .andExpect(content().string(containsString("CMPT 225 in 2024 Summer is already in your record.")));

        assertThat(coursesOf(jack)).hasSize(1);
    }

    // A retake is in a later term, so it's a different course in the record
    @Test
    void sameCourseInAnotherTermCanBeAdded() throws Exception {
        saveCourse(jack, "2024 Spring", "225", "F");

        mockMvc.perform(courseForm(post("/student/courses"), "subject", "CMPT", "number", "225").session(session))
                .andExpect(redirectedUrl("/student/courses"));

        assertThat(coursesOf(jack)).extracting(TranscriptCourse::getTerm).containsExactly("2024 Spring", "2024 Summer");
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

    // Edit or remove a course in my record

    @Test
    void eachCourseHasEditAndRemove() throws Exception {
        TranscriptCourse course = saveCourse(jack, "2025 Fall", "120", "A-");

        mockMvc.perform(get("/student/courses").session(session))
                .andExpect(content().string(containsString("href=\"/student/courses/" + course.getId() + "/edit\"")))
                .andExpect(content().string(containsString("action=\"/student/courses/" + course.getId() + "/remove\"")))
                .andExpect(content().string(containsString("data-confirm=\"Remove CMPT 120 (2025 Fall) from your record?\"")))
                .andExpect(content().string(containsString("onsubmit=\"return confirm(this.dataset.confirm)\"")));
    }

    // Only My courses can change courses. The home page and transcript review show the table without them
    @Test
    void homePageHasNoEditButtons() throws Exception {
        saveCourse(jack, "2025 Fall", "120", "A-");

        mockMvc.perform(get("/student/home").session(session))
                .andExpect(content().string(not(containsString("/edit\""))));
    }

    @Test
    void editFormStartsWithTheCoursesValues() throws Exception {
        TranscriptCourse course = saveCourse(jack, "2025 Fall", "120", "A-");

        mockMvc.perform(get("/student/courses/" + course.getId() + "/edit").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("student/edit-course"))
                .andExpect(content().string(containsString("value=\"2025\"")))
                .andExpect(content().string(containsString("<option value=\"Fall\" selected=\"selected\">")))
                .andExpect(content().string(containsString("<option value=\"A-\" selected=\"selected\">")))
                .andExpect(content().string(containsString("value=\"Some Course\"")));
    }

    // An edit is the student's fix, so it becomes MANUAL and a newer transcript won't undo it
    @Test
    void editingSavesTheChangesAsManual() throws Exception {
        TranscriptCourse course = saveCourse(jack, "2025 Fall", "120", "A-");

        mockMvc.perform(courseForm(post("/student/courses/" + course.getId() + "/edit"), "grade", "A")
                        .session(session))
                .andExpect(redirectedUrl("/student/courses"))
                .andExpect(flash().attribute("message", "Saved MATH 151."));

        TranscriptCourse saved = transcriptCourseRepository.findById(course.getId()).orElseThrow();
        assertThat(saved.getTerm()).isEqualTo("2024 Summer");
        assertThat(saved.getNumber()).isEqualTo("151");
        assertThat(saved.getGrade()).isEqualTo("A");
        assertThat(saved.getSource()).isEqualTo(TranscriptCourse.Source.MANUAL);
    }

    // editing uses the same checks as adding
    @Test
    void invalidEditShowsErrorsAndChangesNothing() throws Exception {
        TranscriptCourse course = saveCourse(jack, "2025 Fall", "120", "A-");

        mockMvc.perform(courseForm(post("/student/courses/" + course.getId() + "/edit"), "units", "-3")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("student/edit-course"))
                .andExpect(content().string(containsString("Units must be a number")));

        TranscriptCourse unchanged = transcriptCourseRepository.findById(course.getId()).orElseThrow();
        assertThat(unchanged.getNumber()).isEqualTo("120");
        assertThat(unchanged.getSource()).isEqualTo(TranscriptCourse.Source.TRANSCRIPT);
    }

    @Test
    void editingIntoAnotherCourseInTheRecordChangesNothing() throws Exception {
        saveCourse(jack, "2025 Fall", "120", "A-");
        TranscriptCourse course = saveCourse(jack, "2025 Fall", "125", "B");

        mockMvc.perform(courseForm(post("/student/courses/" + course.getId() + "/edit"),
                        "year", "2025", "season", "Fall", "subject", "CMPT", "number", "120").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("student/edit-course"))
                .andExpect(content().string(containsString("CMPT 120 in 2025 Fall is already in your record.")));

        assertThat(transcriptCourseRepository.findById(course.getId()).orElseThrow().getNumber()).isEqualTo("125");
    }

    // The course being edited isn't a duplicate of itself
    @Test
    void editingOnlyTheGradeStillSaves() throws Exception {
        TranscriptCourse course = saveCourse(jack, "2025 Fall", "120", "A-");

        mockMvc.perform(courseForm(post("/student/courses/" + course.getId() + "/edit"),
                        "year", "2025", "season", "Fall", "subject", "CMPT", "number", "120", "grade", "A")
                        .session(session))
                .andExpect(redirectedUrl("/student/courses"));

        assertThat(transcriptCourseRepository.findById(course.getId()).orElseThrow().getGrade()).isEqualTo("A");
    }

    @Test
    void removeDeletesTheCourse() throws Exception {
        TranscriptCourse course = saveCourse(jack, "2025 Fall", "120", "A-");
        TranscriptCourse other = saveCourse(jack, "2025 Fall", "125", "B");

        mockMvc.perform(post("/student/courses/" + course.getId() + "/remove").session(session))
                .andExpect(redirectedUrl("/student/courses"))
                .andExpect(flash().attribute("message", "Removed CMPT 120 from your record."));

        assertThat(coursesOf(jack)).extracting(TranscriptCourse::getId).containsExactly(other.getId());
    }

    // a student can't edit or remove another student's course, even by changing the course id in the request
    @Test
    void cantEditOrRemoveAnotherStudentsCourse() throws Exception {
        StudentProfile sam = saveStudent("sam@example.com", "Sam");
        TranscriptCourse samsCourse = saveCourse(sam, "2025 Fall", "120", "A-");
        String base = "/student/courses/" + samsCourse.getId();

        mockMvc.perform(get(base + "/edit").session(session))
                .andExpect(redirectedUrl("/student/courses"))
                .andExpect(flash().attribute("error", EditCourseController.NOT_FOUND));
        mockMvc.perform(courseForm(post(base + "/edit"), "grade", "F").session(session))
                .andExpect(redirectedUrl("/student/courses"))
                .andExpect(flash().attribute("error", EditCourseController.NOT_FOUND));
        mockMvc.perform(post(base + "/remove").session(session))
                .andExpect(redirectedUrl("/student/courses"))
                .andExpect(flash().attribute("error", EditCourseController.NOT_FOUND));

        TranscriptCourse unchanged = transcriptCourseRepository.findById(samsCourse.getId()).orElseThrow();
        assertThat(unchanged.getGrade()).isEqualTo("A-");
        assertThat(unchanged.getNumber()).isEqualTo("120");
    }

    // A missing course should get the same answer as someone else's so ids can't be probed
    @Test
    void missingCourseGetsTheSameAnswer() throws Exception {
        mockMvc.perform(post("/student/courses/999999/remove").session(session))
                .andExpect(redirectedUrl("/student/courses"))
                .andExpect(flash().attribute("error", EditCourseController.NOT_FOUND));
    }

    @Test
    void editAndRemoveNeedAStudentLogin() throws Exception {
        TranscriptCourse course = saveCourse(jack, "2025 Fall", "120", "A-");

        mockMvc.perform(get("/student/courses/" + course.getId() + "/edit")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/student/courses/" + course.getId() + "/remove")).andExpect(redirectedUrl("/login"));
        assertThat(coursesOf(jack)).hasSize(1);
    }
}
