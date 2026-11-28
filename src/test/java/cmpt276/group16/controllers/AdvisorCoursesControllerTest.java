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

import cmpt276.group16.models.AdvisorAssignment;
import cmpt276.group16.models.AdvisorAssignmentRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;

// #78: an advisor adds, edits and removes courses in an assigned student's record, on the same My courses and
// Edit course pages the student uses, under /advisor/students/{id}/courses.
// Uses the whole app like AdvisorPlanControllerTest, so AuthInterceptor and the real database are part of every test.
// addFilters = false: Spring Session's filter would swap the test's session for a database one
@SpringBootTest(properties = "app.demo-password=")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class AdvisorCoursesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private AdvisorAssignmentRepository advisorAssignmentRepository;

    @Autowired
    private TranscriptCourseRepository transcriptCourseRepository;

    private User advisor;
    // Assigned to the advisor
    private StudentProfile jack;
    // Not assigned to the advisor
    private StudentProfile finn;
    private String jackPages;
    private String finnPages;

    @BeforeEach
    void setUp() {
        advisor = userRepository.save(new User("advisor@example.com", "hash", "Test Advisor", Role.ADVISOR));
        jack = saveStudent("jack@example.com", "Jack");
        finn = saveStudent("finn@example.com", "Finn");
        advisorAssignmentRepository.save(new AdvisorAssignment(advisor, jack));
        jackPages = "/advisor/students/" + jack.getUserId();
        finnPages = "/advisor/students/" + finn.getUserId();
    }

    private StudentProfile saveStudent(String email, String name) {
        User user = userRepository.save(new User(email, "hash", name, Role.STUDENT));
        return studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    private MockHttpSession sessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        SessionUser.signIn(session, user);
        return session;
    }

    private TranscriptCourse saveCourse(StudentProfile student, String number, String grade) {
        return transcriptCourseRepository.save(new TranscriptCourse(student, "2025 Fall", "CMPT", number,
                "Title of CMPT " + number, new BigDecimal("3.00"), grade));
    }

    private List<TranscriptCourse> coursesOf(StudentProfile student) {
        return transcriptCourseRepository.findByStudentOrderByIdAsc(student);
    }

    // A filled-in Add/Edit course form; overrides are field/value pairs that replace the defaults
    private static MockHttpServletRequestBuilder courseForm(MockHttpServletRequestBuilder request, String... overrides) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("year", "2025");
        form.put("season", "Fall");
        form.put("subject", "CMPT");
        form.put("number", "120");
        form.put("title", "Intro.Cmpt.Sci/Programming I");
        form.put("units", "3");
        form.put("grade", "B+");
        for (int i = 0; i < overrides.length; i += 2) {
            form.put(overrides[i], overrides[i + 1]);
        }
        form.forEach(request::param);
        return request;
    }

    // -- The advisor's version of My courses --

    @Test
    void overviewLinksToTheStudentsCourses() throws Exception {
        mockMvc.perform(get(jackPages).session(sessionFor(advisor)))
               .andExpect(content().string(containsString("href=\"" + jackPages + "/courses\"")))
               .andExpect(content().string(containsString("Edit their courses")));
    }

    // The same page the student sees, but every link and form points at this student's record
    @Test
    void advisorOpensTheStudentsCourses() throws Exception {
        TranscriptCourse course = saveCourse(jack, "225", "B");

        mockMvc.perform(get(jackPages + "/courses").session(sessionFor(advisor)))
               .andExpect(status().isOk())
               .andExpect(view().name("student/view-courses"))
               .andExpect(content().string(containsString("Courses for Jack")))
               .andExpect(content().string(containsString("Editing the record of Jack (jack@example.com).")))
               .andExpect(content().string(containsString("Title of CMPT 225")))
               .andExpect(content().string(containsString("href=\"" + jackPages + "/courses/" + course.getId() + "/edit\"")))
               .andExpect(content().string(containsString("action=\"" + jackPages + "/courses/" + course.getId() + "/remove\"")))
               .andExpect(content().string(containsString("from their record?")))
               .andExpect(content().string(containsString("action=\"" + jackPages + "/courses\"")))
               .andExpect(content().string(containsString("href=\"" + jackPages + "\" class=\"link\">Back to Jack</a>")))
               .andExpect(content().string(not(containsString("/student/courses"))));
    }

    @Test
    void emptyRecordSaysTheirRecord() throws Exception {
        mockMvc.perform(get(jackPages + "/courses").session(sessionFor(advisor)))
               .andExpect(content().string(containsString("No courses in their record yet.")))
               .andExpect(content().string(not(containsString("You don't have any saved courses yet"))));
    }

    // Acceptance criteria: the course is saved to the student's record, and the student sees it on My courses
    @Test
    void advisorAddsACourseTheStudentSees() throws Exception {
        mockMvc.perform(courseForm(post(jackPages + "/courses")).session(sessionFor(advisor)))
               .andExpect(redirectedUrl(jackPages + "/courses"))
               .andExpect(flash().attribute("message", "Added CMPT 120 to 2025 Fall."));

        assertThat(coursesOf(jack)).singleElement().satisfies(course -> {
            assertThat(course.getNumber()).isEqualTo("120");
            assertThat(course.getSource()).isEqualTo(TranscriptCourse.Source.MANUAL);
        });
        mockMvc.perform(get("/student/courses").session(sessionFor(jack.getUser())))
               .andExpect(content().string(containsString("Intro.Cmpt.Sci/Programming I")));
    }

    @Test
    void invalidCourseShowsTheErrorsOnTheAdvisorsPage() throws Exception {
        mockMvc.perform(courseForm(post(jackPages + "/courses"), "units", "-3").session(sessionFor(advisor)))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Courses for Jack")))
               .andExpect(content().string(containsString("Units must be a whole number")));

        assertThat(coursesOf(jack)).isEmpty();
    }

    @Test
    void addingACourseAlreadyInTheRecordSaysTheirRecord() throws Exception {
        saveCourse(jack, "120", "A");

        mockMvc.perform(courseForm(post(jackPages + "/courses")).session(sessionFor(advisor)))
               .andExpect(content().string(containsString("CMPT 120 in 2025 Fall is already in their record.")));

        assertThat(coursesOf(jack)).hasSize(1);
    }

    @Test
    void advisorOpensTheEditForm() throws Exception {
        TranscriptCourse course = saveCourse(jack, "225", "B");

        mockMvc.perform(get(jackPages + "/courses/" + course.getId() + "/edit").session(sessionFor(advisor)))
               .andExpect(status().isOk())
               .andExpect(view().name("student/edit-course"))
               .andExpect(content().string(containsString("Editing a course for Jack (jack@example.com).")))
               .andExpect(content().string(containsString("action=\"" + jackPages + "/courses/" + course.getId() + "/edit\"")))
               .andExpect(content().string(containsString("href=\"" + jackPages + "/courses\" class=\"link\">Cancel</a>")));
    }

    @Test
    void advisorEditsACourse() throws Exception {
        TranscriptCourse course = saveCourse(jack, "225", "B");

        mockMvc.perform(courseForm(post(jackPages + "/courses/" + course.getId() + "/edit"),
                        "number", "225", "title", "Data Structures", "grade", "A-").session(sessionFor(advisor)))
               .andExpect(redirectedUrl(jackPages + "/courses"))
               .andExpect(flash().attribute("message", "Saved CMPT 225."));

        TranscriptCourse saved = transcriptCourseRepository.findById(course.getId()).orElseThrow();
        assertThat(saved.getGrade()).isEqualTo("A-");
        assertThat(saved.getSource()).isEqualTo(TranscriptCourse.Source.MANUAL);
    }

    @Test
    void advisorRemovesACourse() throws Exception {
        TranscriptCourse course = saveCourse(jack, "225", "B");

        mockMvc.perform(post(jackPages + "/courses/" + course.getId() + "/remove").session(sessionFor(advisor)))
               .andExpect(redirectedUrl(jackPages + "/courses"))
               .andExpect(flash().attribute("message", "Removed CMPT 225 from their record."));

        assertThat(coursesOf(jack)).isEmpty();
    }

    // -- Only assigned students --

    // Acceptance criteria: an advisor can't change the record of a student who isn't assigned to them
    @Test
    void unassignedStudentsCoursesAreRefused() throws Exception {
        TranscriptCourse course = saveCourse(finn, "225", "B");
        String finnCourse = finnPages + "/courses/" + course.getId();

        mockMvc.perform(get(finnPages + "/courses").session(sessionFor(advisor)))
               .andExpect(status().isForbidden());
        mockMvc.perform(get(finnCourse + "/edit").session(sessionFor(advisor)))
               .andExpect(status().isForbidden());
        mockMvc.perform(courseForm(post(finnPages + "/courses")).session(sessionFor(advisor)))
               .andExpect(status().isForbidden());
        mockMvc.perform(courseForm(post(finnCourse + "/edit"), "number", "225", "grade", "F").session(sessionFor(advisor)))
               .andExpect(status().isForbidden());
        mockMvc.perform(post(finnCourse + "/remove").session(sessionFor(advisor)))
               .andExpect(status().isForbidden());

        assertThat(coursesOf(finn)).singleElement().satisfies(saved -> assertThat(saved.getGrade()).isEqualTo("B"));
    }

    // The assigned student's address can't be used on another student's course:
    // the course is looked up in the assigned student's record only
    @Test
    void assignedStudentsPagesCantReachAnotherStudentsCourse() throws Exception {
        TranscriptCourse finnsCourse = saveCourse(finn, "225", "B");
        String throughJack = jackPages + "/courses/" + finnsCourse.getId();

        mockMvc.perform(get(throughJack + "/edit").session(sessionFor(advisor)))
               .andExpect(redirectedUrl(jackPages + "/courses"))
               .andExpect(flash().attribute("error", "That course isn't in their record."));
        mockMvc.perform(courseForm(post(throughJack + "/edit"), "number", "225", "grade", "F").session(sessionFor(advisor)))
               .andExpect(flash().attribute("error", "That course isn't in their record."));
        mockMvc.perform(post(throughJack + "/remove").session(sessionFor(advisor)))
               .andExpect(flash().attribute("error", "That course isn't in their record."));

        assertThat(coursesOf(finn)).singleElement().satisfies(saved -> assertThat(saved.getGrade()).isEqualTo("B"));
    }

    // AuthInterceptor sends a student to their own home, even for their own advisor pages
    @Test
    void studentCantUseTheAdvisorPages() throws Exception {
        mockMvc.perform(get(jackPages + "/courses").session(sessionFor(jack.getUser())))
               .andExpect(redirectedUrl("/student/home"));
        mockMvc.perform(courseForm(post(jackPages + "/courses")).session(sessionFor(jack.getUser())))
               .andExpect(redirectedUrl("/student/home"));

        assertThat(coursesOf(jack)).isEmpty();
    }

    @Test
    void signedOutVisitorGoesToLogin() throws Exception {
        mockMvc.perform(get(jackPages + "/courses"))
               .andExpect(redirectedUrl("/login"));
    }

    // -- The student's own pages are unchanged --

    @Test
    void studentsOwnPagesKeepTheirWordingAndLinks() throws Exception {
        TranscriptCourse course = saveCourse(jack, "225", "B");

        mockMvc.perform(get("/student/courses").session(sessionFor(jack.getUser())))
               .andExpect(content().string(containsString("<h1 class=\"page-title\">My courses</h1>")))
               .andExpect(content().string(containsString("href=\"/student/courses/" + course.getId() + "/edit\"")))
               .andExpect(content().string(containsString("from your record?")))
               .andExpect(content().string(containsString("href=\"/student/home\" class=\"link\">Back to home</a>")))
               .andExpect(content().string(not(containsString("/advisor/"))));
        mockMvc.perform(get("/student/courses/" + course.getId() + "/edit").session(sessionFor(jack.getUser())))
               .andExpect(content().string(containsString("Your changes are kept when you upload a new transcript.")))
               .andExpect(content().string(containsString("href=\"/student/courses\" class=\"link\">Cancel</a>")));
    }
}
