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

@SpringBootTest
@AutoConfigureMockMvc(addFilters =  false)
@Transactional
class ViewMyCoursesControllerTest {

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
        session.setAttribute(CurrentStudent.SESSION_USER, user);
        return session;
    }

    private StudentProfile saveStudent(String email, String name){
        User user = userRepository.save(new User(email, "hash", name, Role.STUDENT));
        return studentProfileRepository.save(new StudentProfile(user, Program.COMPUTING_SCIENCE, 2024));    
    }

    @Test
    void myCoursesNeedsALogin() throws Exception {
        mockMvc.perform(get("/student/courses")).andExpect(redirectedUrl("/login"));
    }

    @Test 
    void noCoursesLinksToTheUploadPage() throws Exception {
        StudentProfile student = saveStudent("emma@example.com", "Emma");
        mockMvc.perform(get("/student/courses").session(sessionFor(student.getUser()))).andExpect(status().isOk())
        .andExpect(content().string(containsString("You don't have any saved courses yet")))
        .andExpect(content().string(containsString("href=\"/student/transcript\"")));
    }

    @Test
    void advisorsCantSeeMyCourses() throws Exception {
        User advisor = userRepository.save(new User("test.advisor@example.com", "hash", "Advisor", Role.ADVISOR));
        mockMvc.perform(get("/student/courses").session(sessionFor(advisor)))
        .andExpect(redirectedUrl("/login"));
    }

    @Test 
    void coursesAreGroupedByTermInOrder() throws Exception {
        StudentProfile student = saveStudent("emma@example.com", "Emma");
        transcriptService.replaceCourses(student, List.of(
            new ParsedCourse("2025 Fall", "CMPT", "120", "Intro.Cmpt.Sci/Programming I", new BigDecimal("3.00"), "A-"),
            new ParsedCourse("2025 Fall", "MATH", "151", "Calculus I", new BigDecimal("3.00"), "B+"),
            new ParsedCourse("2026 Spring", "CMPT", "225", "Data Structures/Programming", new BigDecimal("3.00"), null)));
    
        String page = mockMvc.perform(get("/student/courses").session(sessionFor(student.getUser()))).andReturn()
        .getResponse().getContentAsString();

        assertThat(page).containsSubsequence("<h2 class=\"label\">2025 Fall</h2>", "<h2 class=\"label\">2026 Spring</h2>");

        assertThat(CourseTableRows.in(page)).containsExactly(
            List.of("2025 Fall", "CMPT", "120", "Intro.Cmpt.Sci/Programming I", "3.00", "A-"),
            List.of("2025 Fall", "MATH", "151", "Calculus I", "3.00", "B+"),
            List.of("2026 Spring", "CMPT", "225", "Data Structures/Programming", "3.00", "In progress"));
    }

    @Test
    void studentsOnlySeeTheirOwnCourses() throws Exception {
        StudentProfile emma = saveStudent("emma@example.com", "Emma");
        StudentProfile sam = saveStudent("sam@example.com", "Sam");
        transcriptService.replaceCourses(sam, List.of(new ParsedCourse("2025 Fall", "PHYS", "140", "Physics", new BigDecimal("4.00"), "A")));

        mockMvc.perform(get("/student/courses").session(sessionFor(emma.getUser())))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Physics"))))
        .andExpect(content().string(containsString("You don't have any saved courses yet")));
    }

    @Test
    void homeLinksToMyCourses() throws Exception {
        StudentProfile student = saveStudent("emma@example.com", "Emma");
        mockMvc.perform(get("/student/home").session(sessionFor(student.getUser())))
        .andExpect(content().string(containsString("href=\"/student/courses\"")));
    }
}
