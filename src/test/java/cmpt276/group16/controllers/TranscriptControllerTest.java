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
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.transcript.TestTranscripts;

// Same setup as StudentHomeControllerTest: a MockHttpSession instead of the database-backed one,
// and each test's database changes are rolled back
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class TranscriptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private TranscriptCourseRepository transcriptCourseRepository;

    @Autowired
    private UploadLimit uploadLimit;

    // The table the review page should show for TestTranscripts.samplePdf()
    private static final List<List<String>> SAMPLE_ROWS = List.of(
            List.of("2025 Fall", "CMPT", "120", "Intro.Cmpt.Sci/Programming I", "3.00", "A-"),
            List.of("2025 Fall", "MACM", "101", "Discrete Math I", "3.00", "A"),
            List.of("2025 Fall", "MATH", "151", "Calculus I", "3.00", "B+"),
            List.of("2026 Spring", "CMPT", "125", "Intro.Cmpt.Sci/Programming II", "3.00", "B"),
            List.of("2026 Spring", "ENSC", "195", "Industrial Internship I", "3.00", "P"),
            List.of("2026 Fall", "CMPT", "105W", "Social Issues&Comm Strategies", "3.00", "In progress"),
            List.of("2026 Fall", "CMPT", "225", "Data Structures/Programming", "3.00", "In progress"),
            List.of("2026 Fall", "CMPT", "419", "Special Topics in AI", "3.00", "In progress"));

    private StudentProfile student;
    private MockHttpSession session;

    @BeforeEach
    void logInAsStudent() {
        User user = userRepository.save(new User("priya@example.com", "hash", "Priya", Role.STUDENT));
        student = studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
        session = new MockHttpSession();
        session.setAttribute(LoginController.SESSION_USER, user);
    }

    private MockMultipartFile pdf(byte[] bytes) {
        return new MockMultipartFile("file", "transcript.pdf", "application/pdf", bytes);
    }

    private void uploadSample() throws Exception {
        mockMvc.perform(multipart("/student/transcript").file(pdf(TestTranscripts.samplePdf())).session(session));
    }

    // Acceptance criterion: an upload page that accepts a PDF
    @Test
    void uploadPageAcceptsAPdf() throws Exception {
        mockMvc.perform(get("/student/transcript").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string(containsString("accept=\"application/pdf,.pdf\"")));
    }

    // The browser-side size check reads the same limit as the server
    @Test
    void uploadPageGivesTheBrowserTheSizeLimit() throws Exception {
        mockMvc.perform(get("/student/transcript").session(session))
                .andExpect(content().string(containsString("data-max-bytes=\"" + uploadLimit.getBytes() + "\"")))
                .andExpect(content().string(containsString("data-too-large=\"" + uploadLimit.getMessage() + "\"")));
    }

    @Test
    void transcriptPagesNeedALogin() throws Exception {
        mockMvc.perform(get("/student/transcript")).andExpect(redirectedUrl("/login"));
        mockMvc.perform(multipart("/student/transcript").file(pdf(TestTranscripts.samplePdf())))
                .andExpect(redirectedUrl("/login"));
        mockMvc.perform(post("/student/transcript/confirm")).andExpect(redirectedUrl("/login"));
    }

    // Acceptance criteria: lists each course with its subject, number, title, units, term and grade,
    // and nothing is saved until the student confirms
    @Test
    void uploadListsEachCourseWithoutSavingIt() throws Exception {
        String page = mockMvc.perform(multipart("/student/transcript").file(pdf(TestTranscripts.samplePdf()))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("transcript-review"))
                .andExpect(content().string(containsString("We found " + SAMPLE_ROWS.size() + " courses.")))
                .andReturn().getResponse().getContentAsString();

        assertThat(CourseTableRows.in(page)).isEqualTo(SAMPLE_ROWS);
        assertThat(transcriptCourseRepository.findByStudentOrderByIdAsc(student)).isEmpty();
        assertThat(session.getAttribute(TranscriptController.PENDING_COURSES)).isNotNull();
    }

    @Test
    void confirmSavesTheCourses() throws Exception {
        uploadSample();

        mockMvc.perform(post("/student/transcript/confirm").session(session))
                .andExpect(redirectedUrl("/student/home"))
                .andExpect(flash().attribute("message",
                        "Saved " + TestTranscripts.SAMPLE_COURSE_COUNT + " courses from your transcript."));

        assertThat(transcriptCourseRepository.findByStudentOrderByIdAsc(student))
                .hasSize(TestTranscripts.SAMPLE_COURSE_COUNT)
                .first()
                .extracting(TranscriptCourse::getTerm, TranscriptCourse::getSubject, TranscriptCourse::getNumber,
                        TranscriptCourse::getGrade)
                .containsExactly("2025 Fall", "CMPT", "120", "A-");
        assertThat(session.getAttribute(TranscriptController.PENDING_COURSES)).isNull();
    }

    @Test
    void uploadingAgainReplacesTheSavedCourses() throws Exception {
        for (int i = 0; i < 2; i++) {
            uploadSample();
            mockMvc.perform(post("/student/transcript/confirm").session(session));
        }

        assertThat(transcriptCourseRepository.findByStudentOrderByIdAsc(student))
                .hasSize(TestTranscripts.SAMPLE_COURSE_COUNT);
    }

    @Test
    void firstUploadHasNoReplaceWarning() throws Exception {
        mockMvc.perform(multipart("/student/transcript").file(pdf(TestTranscripts.samplePdf())).session(session))
                .andExpect(content().string(not(containsString("Confirming replaces"))));
    }

    // Confirming deletes the earlier courses, so the review page has to say so first
    @Test
    void uploadAfterConfirmingWarnsThatSavedCoursesWillBeReplaced() throws Exception {
        uploadSample();
        mockMvc.perform(post("/student/transcript/confirm").session(session));

        mockMvc.perform(multipart("/student/transcript").file(pdf(TestTranscripts.samplePdf())).session(session))
                .andExpect(content().string(containsString("You already have " + TestTranscripts.SAMPLE_COURSE_COUNT
                        + " saved courses. Confirming replaces them")));
    }

    @Test
    void replaceWarningUsesSingularForOneCourse() throws Exception {
        transcriptCourseRepository.save(new TranscriptCourse(student, "2025 Fall", "CMPT", "120",
                "Intro.Cmpt.Sci/Programming I", new BigDecimal("3.00"), "A-"));

        mockMvc.perform(multipart("/student/transcript").file(pdf(TestTranscripts.samplePdf())).session(session))
                .andExpect(content().string(containsString("You already have 1 saved course. Confirming")));
    }

    // e.g. the student clicks Confirm twice
    @Test
    void confirmWithNothingUploadedSavesNothing() throws Exception {
        mockMvc.perform(post("/student/transcript/confirm").session(session))
                .andExpect(redirectedUrl("/student/transcript"));

        assertThat(transcriptCourseRepository.findByStudentOrderByIdAsc(student)).isEmpty();
    }

    // Acceptance criterion: a non-PDF file shows an error instead of crashing
    @Test
    void nonPdfFileShowsAnError() throws Exception {
        MockMultipartFile text = new MockMultipartFile("file", "transcript.txt", "text/plain",
                "not a transcript".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/student/transcript").file(text).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("transcript-upload"))
                .andExpect(content().string(containsString("isn&#39;t a PDF")));
    }

    // Acceptance criterion: a PDF with no courses found shows an error instead of crashing
    @Test
    void pdfWithNoCoursesShowsAnError() throws Exception {
        mockMvc.perform(multipart("/student/transcript").file(pdf(TestTranscripts.blankPdf())).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("transcript-upload"))
                .andExpect(content().string(containsString("No courses were found")));
    }

    @Test
    void emptyUploadShowsAnError() throws Exception {
        mockMvc.perform(multipart("/student/transcript").file(pdf(new byte[0])).session(session))
                .andExpect(view().name("transcript-upload"))
                .andExpect(content().string(containsString("Please choose your transcript PDF")));
    }

    // A line that starts like a course but can't be read is shown to the student instead of being dropped
    @Test
    void reviewPageListsLinesThatCouldNotBeRead() throws Exception {
        byte[] pdf = TestTranscripts.pdfWithLines(List.of(
                "2025 Fall",
                "CMPT 120 Intro.Cmpt.Sci/Programming I 3.00 3.00 A- 11.01 B 310",
                "CMPT 307 Data Structures 3.00"));

        String page = mockMvc.perform(multipart("/student/transcript").file(pdf(pdf)).session(session))
                .andExpect(content().string(containsString("couldn't be read")))
                .andExpect(content().string(containsString("<li>CMPT 307 Data Structures 3.00</li>")))
                .andReturn().getResponse().getContentAsString();

        assertThat(CourseTableRows.in(page)).extracting(row -> row.get(2)).containsExactly("120");
    }

    @Test
    void oneCourseIsNotCalledCourses() throws Exception {
        byte[] pdf = TestTranscripts.pdfWithLines(List.of(
                "2025 Fall",
                "CMPT 120 Intro.Cmpt.Sci/Programming I 3.00 3.00 A- 11.01 B 310"));

        mockMvc.perform(multipart("/student/transcript").file(pdf(pdf)).session(session))
                .andExpect(content().string(containsString("We found 1 course.")));
        mockMvc.perform(post("/student/transcript/confirm").session(session))
                .andExpect(flash().attribute("message", "Saved 1 course from your transcript."));
    }

    // A failed upload clears the earlier one, so confirm can't save courses the student isn't looking at
    @Test
    void failedUploadDropsTheEarlierUnconfirmedCourses() throws Exception {
        uploadSample();
        mockMvc.perform(multipart("/student/transcript").file(pdf(TestTranscripts.blankPdf())).session(session));

        mockMvc.perform(post("/student/transcript/confirm").session(session));

        assertThat(transcriptCourseRepository.findByStudentOrderByIdAsc(student)).isEmpty();
    }
}
