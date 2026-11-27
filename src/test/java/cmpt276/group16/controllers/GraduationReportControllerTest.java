package cmpt276.group16.controllers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.Plan;
import cmpt276.group16.models.PlanRepository;
import cmpt276.group16.models.PlanTerm;
import cmpt276.group16.models.PlanTermRepository;
import cmpt276.group16.models.PlannedCourse;
import cmpt276.group16.models.PlannedCourseRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.Term;
import cmpt276.group16.models.TermRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.transcript.ParsedCourse;
import cmpt276.group16.transcript.TranscriptService;

// #69: the Graduation Report page, with the real requirements file that's loaded at startup.
// RequirementCheckTest covers each kind of rule on its own.
// Filters are off so the test's MockHttpSession is used, and @Transactional undoes each test's changes
@SpringBootTest(properties = "app.demo-password=")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class GraduationReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private TranscriptService transcriptService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanTermRepository planTermRepository;

    @Autowired
    private PlannedCourseRepository plannedCourseRepository;

    private MockHttpSession sessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        SessionUser.signIn(session, user);
        return session;
    }

    private StudentProfile saveStudent(String email, Program program) {
        User user = userRepository.save(new User(email, "hash", "Student", Role.STUDENT));
        return studentProfileRepository.save(new StudentProfile(user, program, 2024));
    }

    private void record(StudentProfile student, ParsedCourse... courses) {
        transcriptService.replaceCourses(student, List.of(courses));
    }

    // grade null means the course is in progress
    private static ParsedCourse took(String term, String subject, String number, String grade) {
        return new ParsedCourse(term, subject, number, "Title", new BigDecimal("3.00"), grade);
    }

    // Puts the course in the student's main plan for 2027 Fall
    private void plan(StudentProfile student, String subject, String number) {
        Course course = courseRepository.findBySubjectAndNumber(subject, number)
                .orElseGet(() -> courseRepository.save(
                        new Course(subject, number, "Title", new BigDecimal("3.00"), null, null, null)));
        Term term = termRepository.findByTermYearAndSeason(2027, Season.FALL)
                .orElseGet(() -> termRepository.save(new Term(2027, Season.FALL)));
        Plan plan = planRepository.findByStudentAndMainTrue(student)
                .orElseGet(() -> planRepository.save(new Plan(student, "My plan", true)));
        PlanTerm planTerm = planTermRepository.findByPlan(plan).stream().findFirst()
                .orElseGet(() -> planTermRepository.save(new PlanTerm(plan, term)));
        plannedCourseRepository.save(new PlannedCourse(planTerm, course));
    }

    private String page(StudentProfile student) throws Exception {
        return mockMvc.perform(get("/student/graduation-report").session(sessionFor(student.getUser())))
                .andExpect(status().isOk())
                .andExpect(view().name("student/graduation-report"))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void needsALogin() throws Exception {
        mockMvc.perform(get("/student/graduation-report")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void advisorsAreSentToTheirOwnHome() throws Exception {
        User advisor = userRepository.save(new User("report.advisor@example.com", "hash", "Advisor", Role.ADVISOR));
        mockMvc.perform(get("/student/graduation-report").session(sessionFor(advisor)))
                .andExpect(redirectedUrl("/advisor/home"));
    }

    @Test
    void homePageLinksToTheReport() throws Exception {
        StudentProfile student = saveStudent("report.home@example.com", Program.COMPUTING_SCIENCE);
        mockMvc.perform(get("/student/home").session(sessionFor(student.getUser())))
                .andExpect(content().string(containsString("href=\"/student/graduation-report\"")));
    }

    @Test
    void showsEachCategoryOfTheStudentsProgram() throws Exception {
        String html = page(saveStudent("report.empty@example.com", Program.COMPUTING_SCIENCE));

        assertThat(html)
                .contains("Computing Science", "entry 2024")
                .contains("Lower division", "Upper division", "Breadth", "Depth", "BSc", "WQB")
                .contains("CMPT 225", "Calculus I", "Remaining", "Check with an advisor")
                .contains("of 120 units.");
    }

    @Test
    void computerEngineeringStudentsSeeTheirOwnRequirements() throws Exception {
        String html = page(saveStudent("report.ce@example.com", Program.COMPUTER_ENGINEERING));

        assertThat(html).contains("ENSC 251").doesNotContain("CMPT 105W");
    }

    @Test
    void showsCompletedInProgressAndPlannedCourses() throws Exception {
        StudentProfile student = saveStudent("report.courses@example.com", Program.COMPUTING_SCIENCE);
        record(student,
                took("2024 Fall", "CMPT", "225", "A"),
                took("2026 Fall", "CMPT", "276", null));
        plan(student, "CMPT", "295");

        String html = page(student);

        assertThat(html)
                .contains("CMPT 225 (A)", "CMPT 276 (in progress)", "CMPT 295 (planned)")
                .contains("3 completed", "3 in progress", "3 planned", "111 still needed.");
    }

    @Test
    void failedCourseDoesntCount() throws Exception {
        StudentProfile student = saveStudent("report.failed@example.com", Program.COMPUTING_SCIENCE);
        record(student, took("2024 Fall", "CMPT", "225", "F"));

        assertThat(page(student)).doesNotContain("CMPT 225 (F)")
                .contains("0 completed");
    }

    @Test
    void latestPassingAttemptCountsOnce() throws Exception {
        StudentProfile student = saveStudent("report.retake@example.com", Program.COMPUTING_SCIENCE);
        record(student,
                took("2024 Fall", "CMPT", "225", "F"),
                took("2025 Spring", "CMPT", "225", "B"));

        assertThat(page(student)).contains("CMPT 225 (B)").contains("3 completed");
    }

    @Test
    void writingCountsCoursesNumberedWithAW() throws Exception {
        StudentProfile student = saveStudent("report.writing@example.com", Program.COMPUTING_SCIENCE);
        record(student,
                took("2024 Fall", "CMPT", "105W", "B"),
                took("2026 Fall", "CMPT", "376W", null));

        assertThat(page(student))
                .contains("Counted: CMPT 105W (B), CMPT 376W (in progress)");
    }

    @Test
    void studentOnlySeesTheirOwnCourses() throws Exception {
        StudentProfile priya = saveStudent("report.priya@example.com", Program.COMPUTING_SCIENCE);
        StudentProfile marcus = saveStudent("report.marcus@example.com", Program.COMPUTING_SCIENCE);
        record(marcus, took("2024 Fall", "CMPT", "225", "A"));

        assertThat(page(priya)).doesNotContain("CMPT 225 (A)");
    }
}
