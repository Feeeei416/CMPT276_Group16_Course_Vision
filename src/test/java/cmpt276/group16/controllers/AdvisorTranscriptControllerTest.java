package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.unit.DataSize;

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
import cmpt276.group16.transcript.TestTranscripts;

// #78: an advisor uploads and confirms an assigned student's transcript, on the same pages the student uses,
// under /advisor/students/{id}/transcript.
// Uses the whole app like AdvisorPlanControllerTest, so AuthInterceptor and the real database are part of every test.
// addFilters = false: Spring Session's filter would swap the test's session for a database one
@SpringBootTest(properties = "app.demo-password=")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class AdvisorTranscriptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private AdvisorAssignmentRepository advisorAssignmentRepository;

    @Autowired
    private TranscriptCourseRepository transcriptCourseRepository;

    private User advisor;
    private MockHttpSession advisorSession;
    // Both assigned to the advisor
    private StudentProfile jack;
    private StudentProfile maya;
    // Not assigned to the advisor
    private StudentProfile finn;
    private String jackPages;
    private String mayaPages;
    private String finnPages;

    @BeforeEach
    void setUp() {
        advisor = userRepository.save(new User("advisor@example.com", "hash", "Test Advisor", Role.ADVISOR));
        jack = saveStudent("jack@example.com", "Jack");
        maya = saveStudent("maya@example.com", "Maya");
        finn = saveStudent("finn@example.com", "Finn");
        advisorAssignmentRepository.save(new AdvisorAssignment(advisor, jack));
        advisorAssignmentRepository.save(new AdvisorAssignment(advisor, maya));
        advisorSession = sessionFor(advisor);
        jackPages = "/advisor/students/" + jack.getUserId();
        mayaPages = "/advisor/students/" + maya.getUserId();
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

    private static MockMultipartFile samplePdf() throws Exception {
        return new MockMultipartFile("file", "transcript.pdf", "application/pdf", TestTranscripts.samplePdf());
    }

    private void uploadSampleFor(String pages) throws Exception {
        mockMvc.perform(multipart(pages + "/transcript").file(samplePdf()).session(advisorSession))
               .andExpect(view().name("student/transcript-review"));
    }

    private List<TranscriptCourse> coursesOf(StudentProfile student) {
        return transcriptCourseRepository.findByStudentOrderByIdAsc(student);
    }

    // -- Upload, review and confirm --

    @Test
    void overviewLinksToTheUploadPage() throws Exception {
        mockMvc.perform(get(jackPages).session(advisorSession))
               .andExpect(content().string(containsString("href=\"" + jackPages + "/transcript\"")))
               .andExpect(content().string(containsString("Upload their transcript")));
    }

    @Test
    void emptyRecordLinksToTheUploadPage() throws Exception {
        mockMvc.perform(get(jackPages + "/courses").session(advisorSession))
               .andExpect(content().string(containsString("href=\"" + jackPages + "/transcript\"")));
    }

    @Test
    void advisorOpensTheUploadPage() throws Exception {
        mockMvc.perform(get(jackPages + "/transcript").session(advisorSession))
               .andExpect(status().isOk())
               .andExpect(view().name("student/transcript-upload"))
               .andExpect(content().string(containsString("Upload a transcript for Jack")))
               .andExpect(content().string(containsString("action=\"" + jackPages + "/transcript\"")))
               .andExpect(content().string(containsString("href=\"" + jackPages + "\" class=\"link\">Cancel</a>")))
               .andExpect(content().string(not(containsString("/student/"))));
    }

    // Nothing is saved until the advisor confirms, the same as for the student
    @Test
    void uploadShowsTheCoursesWithoutSavingThem() throws Exception {
        mockMvc.perform(multipart(jackPages + "/transcript").file(samplePdf()).session(advisorSession))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Review the courses for Jack")))
               .andExpect(content().string(containsString("Intro.Cmpt.Sci/Programming I")))
               .andExpect(content().string(containsString("action=\"" + jackPages + "/transcript/confirm\"")))
               .andExpect(content().string(containsString("formaction=\"" + jackPages + "/transcript/cancel\"")))
               .andExpect(content().string(not(containsString("/student/"))));

        assertThat(coursesOf(jack)).isEmpty();
        assertThat(advisorSession.getAttribute(TranscriptController.PENDING_COURSES + ":" + jack.getUserId()))
                .isNotNull();
    }

    // Acceptance criteria: the courses are saved to the student's record, and the student sees them on My courses
    @Test
    void confirmSavesTheCoursesTheStudentSees() throws Exception {
        uploadSampleFor(jackPages);

        mockMvc.perform(post(jackPages + "/transcript/confirm").session(advisorSession))
               .andExpect(redirectedUrl(jackPages))
               .andExpect(flash().attribute("message",
                       "Saved " + TestTranscripts.SAMPLE_COURSE_COUNT + " courses from Jack's transcript."));

        assertThat(coursesOf(jack)).hasSize(TestTranscripts.SAMPLE_COURSE_COUNT)
                .allSatisfy(course -> assertThat(course.getSource()).isEqualTo(TranscriptCourse.Source.TRANSCRIPT));
        assertThat(advisorSession.getAttribute(TranscriptController.PENDING_COURSES + ":" + jack.getUserId()))
                .isNull();
        mockMvc.perform(get("/student/courses").session(sessionFor(jack.getUser())))
               .andExpect(content().string(containsString("Intro.Cmpt.Sci/Programming I")));
    }

    // The overview shows the message the confirm and cancel buttons leave
    @Test
    void overviewShowsTheSavedMessage() throws Exception {
        mockMvc.perform(get(jackPages).session(advisorSession)
                        .flashAttr("message", "Saved 8 courses from Jack's transcript."))
               .andExpect(content().string(containsString("Saved 8 courses from Jack&#39;s transcript.")));
    }

    // With two students open in two tabs, confirming on one student's page can't save the other's transcript
    @Test
    void transcriptUploadedForOneStudentCantBeConfirmedForAnother() throws Exception {
        uploadSampleFor(jackPages);

        mockMvc.perform(post(mayaPages + "/transcript/confirm").session(advisorSession))
               .andExpect(redirectedUrl(mayaPages + "/transcript"));
        assertThat(coursesOf(maya)).isEmpty();

        // Jack's upload is still waiting, and confirms into Jack's record
        mockMvc.perform(post(jackPages + "/transcript/confirm").session(advisorSession))
               .andExpect(redirectedUrl(jackPages));
        assertThat(coursesOf(jack)).hasSize(TestTranscripts.SAMPLE_COURSE_COUNT);
        assertThat(coursesOf(maya)).isEmpty();
    }

    @Test
    void cancelSavesNothing() throws Exception {
        uploadSampleFor(jackPages);

        mockMvc.perform(post(jackPages + "/transcript/cancel").session(advisorSession))
               .andExpect(redirectedUrl(jackPages))
               .andExpect(flash().attribute("message", "Nothing was saved from their transcript."));
        mockMvc.perform(post(jackPages + "/transcript/confirm").session(advisorSession))
               .andExpect(redirectedUrl(jackPages + "/transcript"));

        assertThat(coursesOf(jack)).isEmpty();
    }

    @Test
    void replaceWarningTalksAboutTheStudent() throws Exception {
        transcriptCourseRepository.save(new TranscriptCourse(jack, "2025 Fall", "CMPT", "120", "Intro",
                new BigDecimal("3.00"), "A"));

        mockMvc.perform(multipart(jackPages + "/transcript").file(samplePdf()).session(advisorSession))
               .andExpect(content().string(containsString("They already have 1 saved course from a transcript.")))
               .andExpect(content().string(not(containsString("You already have"))));
    }

    @Test
    void emptyUploadAsksForTheirTranscript() throws Exception {
        mockMvc.perform(multipart(jackPages + "/transcript")
                        .file(new MockMultipartFile("file", "transcript.pdf", "application/pdf", new byte[0]))
                        .session(advisorSession))
               .andExpect(view().name("student/transcript-upload"))
               .andExpect(content().string(containsString("Please choose their transcript PDF to upload.")))
               .andExpect(content().string(containsString("Upload a transcript for Jack")));
    }

    // -- Only assigned students --

    // Acceptance criteria: an advisor can't change the record of a student who isn't assigned to them
    @Test
    void unassignedStudentsTranscriptIsRefused() throws Exception {
        mockMvc.perform(get(finnPages + "/transcript").session(advisorSession))
               .andExpect(status().isForbidden());
        mockMvc.perform(multipart(finnPages + "/transcript").file(samplePdf()).session(advisorSession))
               .andExpect(status().isForbidden());
        mockMvc.perform(post(finnPages + "/transcript/confirm").session(advisorSession))
               .andExpect(status().isForbidden());
        mockMvc.perform(post(finnPages + "/transcript/cancel").session(advisorSession))
               .andExpect(status().isForbidden());

        assertThat(coursesOf(finn)).isEmpty();
        assertThat(advisorSession.getAttribute(TranscriptController.PENDING_COURSES + ":" + finn.getUserId()))
                .isNull();
    }

    // AuthInterceptor sends a student to their own home, even for their own advisor pages
    @Test
    void studentCantUseTheAdvisorPages() throws Exception {
        MockHttpSession jackSession = sessionFor(jack.getUser());

        mockMvc.perform(get(jackPages + "/transcript").session(jackSession))
               .andExpect(redirectedUrl("/student/home"));
        mockMvc.perform(multipart(jackPages + "/transcript").file(samplePdf()).session(jackSession))
               .andExpect(redirectedUrl("/student/home"));
    }

    // -- The student's own pages are unchanged --

    @Test
    void studentsOwnPagesKeepTheirWordingAndLinks() throws Exception {
        MockHttpSession jackSession = sessionFor(jack.getUser());

        mockMvc.perform(get("/student/transcript").session(jackSession))
               .andExpect(content().string(containsString("Upload your transcript")))
               .andExpect(content().string(containsString("action=\"/student/transcript\"")))
               .andExpect(content().string(not(containsString("/advisor/"))));
        mockMvc.perform(multipart("/student/transcript").file(samplePdf()).session(jackSession))
               .andExpect(content().string(containsString("Review your courses")))
               .andExpect(content().string(containsString("action=\"/student/transcript/confirm\"")));
        assertThat(jackSession.getAttribute(TranscriptController.PENDING_COURSES)).isNotNull();
    }

    // -- A file over the size limit --

    // The file is turned down before any controller runs, so UploadErrorHandler finds the advisor's student itself.
    // (The real upload is tested in UploadErrorHandlerTest; MockMvc skips the size limit)
    @Test
    void fileTooLargeKeepsTheAdvisorsVersionOfThePage() {
        UploadErrorHandler handler = new UploadErrorHandler(DataSize.ofMegabytes(1),
                context.getBeanProvider(PlanOwner.Finder.class));

        ExtendedModelMap forJack = new ExtendedModelMap();
        handler.fileTooLarge(forJack, uploadTo(jackPages + "/transcript"));
        assertThat(forJack.getAttribute("owner")).isInstanceOfSatisfying(PlanOwner.class,
                owner -> assertThat(owner.student().getUserId()).isEqualTo(jack.getUserId()));

        // Not assigned, or not an advisor page: the student's version, with nothing about the student in it
        ExtendedModelMap forFinn = new ExtendedModelMap();
        handler.fileTooLarge(forFinn, uploadTo(finnPages + "/transcript"));
        assertThat(forFinn.getAttribute("owner")).isNull();

        ExtendedModelMap forStudentPage = new ExtendedModelMap();
        handler.fileTooLarge(forStudentPage, uploadTo("/student/transcript"));
        assertThat(forStudentPage.getAttribute("owner")).isNull();
        assertThat(forStudentPage.getAttribute("error")).isEqualTo("Upload a file under 1 MB.");
    }

    private MockHttpServletRequest uploadTo(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setSession(advisorSession);
        return request;
    }
}
