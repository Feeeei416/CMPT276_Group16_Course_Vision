package cmpt276.group16.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cmpt276.group16.models.AdvisorAssignmentRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.services.AdvisorCommentService;

// #72: loads only AdvisorCommentController and the web layer (including AuthInterceptor); everything else is a mock.
// AssignedStudent is the real one, since it's the check that refuses other advisors' students
@WebMvcTest(AdvisorCommentController.class)
@Import(AssignedStudent.class)
class AdvisorCommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdvisorAssignmentRepository advisorAssignmentRepository;

    @MockitoBean
    private StudentProfileRepository studentProfileRepository;

    @MockitoBean
    private AdvisorCommentService commentService;

    // AuthInterceptor loads the signed-in user by id on each request
    @MockitoBean
    private UserRepository userRepository;

    private final User advisor = new User("advisor@test.com", "hash", "Alex", Role.ADVISOR);
    private final User jackUser = new User("jack@example.com", "hash", "Jack", Role.STUDENT);
    private final StudentProfile jack = new StudentProfile(jackUser, Program.COMPUTING_SCIENCE, 2024);

    // Jack (id 5) is assigned to the advisor (id 10). Every other student isn't
    @BeforeEach
    void setUp() {
        advisor.setId(10L);
        jackUser.setId(5L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(advisor));
        when(userRepository.findById(5L)).thenReturn(Optional.of(jackUser));
        when(advisorAssignmentRepository.existsByAdvisorIdAndStudentUserId(10L, 5L)).thenReturn(true);
        when(studentProfileRepository.findById(5L)).thenReturn(Optional.of(jack));
    }

    @Test
    void advisor_addsAComment() throws Exception {
        when(commentService.add(advisor, jack, "Take CMPT 225 in Spring.")).thenReturn(List.of());

        mockMvc.perform(post("/advisor/students/5/comments").param("body", "Take CMPT 225 in Spring.")
                       .sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/advisor/students/5#comments"))
               .andExpect(flash().attributeCount(0));

        verify(commentService).add(advisor, jack, "Take CMPT 225 in Spring.");
    }

    // The reason goes back to the overview, and so does the text, so the advisor can fix it instead of starting over
    @Test
    void turnedDownComment_sendsTheReasonAndTheTextBack() throws Exception {
        when(commentService.add(advisor, jack, "   ")).thenReturn(List.of(AdvisorCommentService.EMPTY));

        mockMvc.perform(post("/advisor/students/5/comments").param("body", "   ")
                       .sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/advisor/students/5#comments"))
               .andExpect(flash().attribute("commentErrors", List.of(AdvisorCommentService.EMPTY)))
               .andExpect(flash().attribute("commentText", "   "));
    }

    // Another advisor's student, or an id that doesn't exist, is refused before anything is saved
    @Test
    void unassignedStudent_isRefused() throws Exception {
        mockMvc.perform(post("/advisor/students/6/comments").param("body", "Hi")
                       .sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().isForbidden());

        verify(commentService, never()).add(any(), any(), any());
    }

    // Students can't comment, not even on Jack's own report: AuthInterceptor sends a student to the student home page
    @Test
    void student_cantAddAComment() throws Exception {
        mockMvc.perform(post("/advisor/students/5/comments").param("body", "Hi")
                       .sessionAttr(SessionUser.SESSION_USER_ID, jackUser.getId()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/student/home"));

        verify(commentService, never()).add(any(), any(), any());
    }

    @Test
    void notLoggedIn_isSentToLogin() throws Exception {
        mockMvc.perform(post("/advisor/students/5/comments").param("body", "Hi"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/login"));

        verify(commentService, never()).add(any(), any(), any());
    }

    @Test
    void advisor_deletesOwnComment() throws Exception {
        when(commentService.delete(advisor, jack, 9L)).thenReturn(List.of());

        mockMvc.perform(post("/advisor/students/5/comments/9/delete")
                       .sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/advisor/students/5#comments"))
               .andExpect(flash().attributeCount(0));

        verify(commentService).delete(advisor, jack, 9L);
    }

    // Another advisor's comment (or one that doesn't exist) stays, and the overview says why
    @Test
    void someoneElsesComment_sendsTheReasonBack() throws Exception {
        when(commentService.delete(advisor, jack, 9L)).thenReturn(List.of(AdvisorCommentService.NOT_YOURS));

        mockMvc.perform(post("/advisor/students/5/comments/9/delete")
                       .sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/advisor/students/5#comments"))
               .andExpect(flash().attribute("commentErrors", List.of(AdvisorCommentService.NOT_YOURS)));
    }

    @Test
    void deleteOnAnUnassignedStudent_isRefused() throws Exception {
        mockMvc.perform(post("/advisor/students/6/comments/9/delete")
                       .sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().isForbidden());

        verify(commentService, never()).delete(any(), any(), any());
    }

    @Test
    void student_cantDeleteAComment() throws Exception {
        mockMvc.perform(post("/advisor/students/5/comments/9/delete")
                       .sessionAttr(SessionUser.SESSION_USER_ID, jackUser.getId()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/student/home"));

        verify(commentService, never()).delete(any(), any(), any());
    }
}
