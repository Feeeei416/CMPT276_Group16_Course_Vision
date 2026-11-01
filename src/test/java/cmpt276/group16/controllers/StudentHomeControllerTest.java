package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.transcript.ParsedCourse;
import cmpt276.group16.transcript.TranscriptService;

// Filters are off so the test's MockHttpSession is used instead of the database-backed session.
// @Transactional undoes each test's database changes when it finishes
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class StudentHomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private TranscriptService transcriptService;

    private MockHttpSession sessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LoginController.SESSION_USER, user);
        return session;
    }

    private StudentProfile saveStudent() {
        User user = userRepository.save(new User("priya@example.com", "hash", "Priya", Role.STUDENT));
        return studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));
    }

    @Test
    void homeNeedsALogin() throws Exception {
        mockMvc.perform(get("/student/home")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void advisorsCantSeeTheStudentHome() throws Exception {
        // Not advisor@example.com, which DataSeeder creates when DEMO_PASSWORD is set
        User advisor = userRepository.save(new User("test.advisor@example.com", "hash", "Advisor", Role.ADVISOR));

        mockMvc.perform(get("/student/home").session(sessionFor(advisor)))
                .andExpect(redirectedUrl("/login"));
    }

    // Acceptance criterion: the student home page links to the upload page
    @Test
    void homeGreetsTheStudentAndLinksToTheUploadPage() throws Exception {
        StudentProfile student = saveStudent();

        mockMvc.perform(get("/student/home").session(sessionFor(student.getUser())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Welcome, Priya")))
                .andExpect(content().string(containsString("href=\"/student/transcript\"")))
                .andExpect(content().string(containsString("No courses yet")));
    }

    @Test
    void homeListsSavedCourses() throws Exception {
        StudentProfile student = saveStudent();
        transcriptService.replaceCourses(student, List.of(
                new ParsedCourse("2025 Fall", "CMPT", "120", "Intro.Cmpt.Sci/Programming I", new BigDecimal("3.00"),
                        "A-"),
                new ParsedCourse("2026 Fall", "CMPT", "225", "Data Structures/Programming", new BigDecimal("3.00"),
                        null)));

        String page = mockMvc.perform(get("/student/home").session(sessionFor(student.getUser())))
                .andReturn().getResponse().getContentAsString();

        assertThat(CourseTableRows.in(page)).containsExactly(
                List.of("2025 Fall", "CMPT", "120", "Intro.Cmpt.Sci/Programming I", "3.00", "A-"),
                List.of("2026 Fall", "CMPT", "225", "Data Structures/Programming", "3.00", "In progress"));
    }
}
