package cmpt276.group16.controllers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import cmpt276.group16.models.AdvisorAssignmentRepository;
import cmpt276.group16.models.Course;
import cmpt276.group16.models.Plan;
import cmpt276.group16.models.PlanTerm;
import cmpt276.group16.models.PlannedCourse;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.Role;
import cmpt276.group16.models.Season;
import cmpt276.group16.models.StudentProfile;
import cmpt276.group16.models.StudentProfileRepository;
import cmpt276.group16.models.Term;
import cmpt276.group16.models.TranscriptCourse;
import cmpt276.group16.models.TranscriptCourseRepository;
import cmpt276.group16.models.User;
import cmpt276.group16.models.UserRepository;
import cmpt276.group16.services.EligibilityService;
import cmpt276.group16.services.EligibilityService.CourseResult;
import cmpt276.group16.services.EligibilityService.Eligibility;
import cmpt276.group16.services.NextSemesterScheduleService;
import cmpt276.group16.services.PlanPrerequisites;
import cmpt276.group16.services.PlanService;
import cmpt276.group16.services.PlanService.TermPlan;

// #71: loads only AdvisorStudentController and the web layer (including AuthInterceptor); everything else is a mock.
// AssignedStudent is the real one, since it's the check that refuses other advisors' students
@WebMvcTest(AdvisorStudentController.class)
@Import(AssignedStudent.class)
class AdvisorStudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdvisorAssignmentRepository advisorAssignmentRepository;

    @MockitoBean
    private StudentProfileRepository studentProfileRepository;

    @MockitoBean
    private TranscriptCourseRepository transcriptCourseRepository;

    @MockitoBean
    private EligibilityService eligibilityService;

    @MockitoBean
    private NextSemesterScheduleService scheduleService;

    @MockitoBean
    private PlanService planService;

    @MockitoBean
    private PlanPrerequisites planPrerequisites;

    // AuthInterceptor loads the signed-in user by id on each request
    @MockitoBean
    private UserRepository userRepository;

    // The password hash is never checked on these pages, so any text works
    private final User advisor = new User("advisor@test.com", "hash", "Test Advisor", Role.ADVISOR);
    private final User itManager = new User("it@test.com", "hash", "Test IT Manager", Role.IT_MANAGER);
    private final User priyaUser = new User("priya@example.com", "hash", "Priya", Role.STUDENT);
    private final StudentProfile priya = new StudentProfile(priyaUser, Program.COMPUTING_SCIENCE, 2024);

    // Priya (id 5) is assigned to the advisor (id 10). Every other student isn't.
    // Each user gets an id and can be found by AuthInterceptor, as if they were saved in the database
    @BeforeEach
    void setUp() {
        advisor.setId(10L);
        priyaUser.setId(5L);
        itManager.setId(3L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(advisor));
        when(userRepository.findById(5L)).thenReturn(Optional.of(priyaUser));
        when(userRepository.findById(3L)).thenReturn(Optional.of(itManager));
        when(advisorAssignmentRepository.existsByAdvisorIdAndStudentUserId(10L, 5L)).thenReturn(true);
        when(studentProfileRepository.findById(5L)).thenReturn(Optional.of(priya));
        when(eligibilityService.forNextSemester(priya))
                .thenReturn(new Eligibility("2027 Spring", true, List.of(), List.of(), List.of()));
    }

    private static Course course(String subject, String number, String title) {
        return new Course(subject, number, title, new BigDecimal("3"), null, null, null);
    }

    // An advisor opens an assigned student's overview
    @Test
    void assignedStudent_showsOverview() throws Exception {
        mockMvc.perform(get("/advisor/students/5").sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().isOk())
               .andExpect(view().name("advisor/student"))
               .andExpect(model().attribute("student", priya))
               .andExpect(content().string(containsString("Priya")))
               .andExpect(content().string(containsString("Computing Science")))
               .andExpect(content().string(containsString("Eligible courses for 2027 Spring")))
               .andExpect(content().string(containsString("Schedule for 2027 Spring")));
    }

    // The course record, with the units earned, the same way My courses shows it
    @Test
    void showsTheCourseRecord() throws Exception {
        when(transcriptCourseRepository.findByStudentOrderByIdAsc(priya)).thenReturn(List.of(
                new TranscriptCourse(priya, "2025 Fall", "CMPT", "225", "Data Structures and Programming",
                        new BigDecimal("3.00"), "A")));

        mockMvc.perform(get("/advisor/students/5").sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("2025 Fall")))
               .andExpect(content().string(containsString("Data Structures and Programming")))
               .andExpect(content().string(containsString("3.00 units earned")));
    }

    // Eligible courses for next semester, and what's missing for the others
    @Test
    void showsEligibleCourses() throws Exception {
        when(eligibilityService.forNextSemester(priya)).thenReturn(new Eligibility("2027 Spring", true,
                List.of(new CourseResult(course("CMPT", "276", "Introduction to Software Engineering"), List.of())),
                List.of(new CourseResult(course("CMPT", "300", "Operating Systems I"),
                        List.of("Needs CMPT 225 with C- or better, you have D."))),
                List.of()));

        mockMvc.perform(get("/advisor/students/5").sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Can take (1)")))
               .andExpect(content().string(containsString("Introduction to Software Engineering")))
               .andExpect(content().string(containsString("Not yet (1)")))
               .andExpect(content().string(containsString("Needs CMPT 225 with C- or better, you have D.")));
    }

    // Next semester's offerings aren't loaded yet, so the page says so instead of showing empty lists
    @Test
    void eligibleCoursesNotLoadedYet() throws Exception {
        when(eligibilityService.forNextSemester(priya))
                .thenReturn(new Eligibility("2027 Spring", false, List.of(), List.of(), List.of()));

        mockMvc.perform(get("/advisor/students/5").sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Course offerings for 2027 Spring have not been loaded yet.")))
               .andExpect(content().string(not(containsString("Can take"))));
    }

    // The main plan, with the same missing-prerequisite flags the student sees on My plan
    @Test
    void showsTheMainPlan() throws Exception {
        PlanTerm fall = new PlanTerm(new Plan(priya, "My plan", true), new Term(2027, Season.FALL));
        PlannedCourse cmpt300 = new PlannedCourse(fall, course("CMPT", "300", "Operating Systems I"));
        // Ids are only set by the database, and the flags are looked up by the course's id
        ReflectionTestUtils.setField(cmpt300, "id", 7L);
        List<TermPlan> termPlans = List.of(new TermPlan(fall, List.of(cmpt300)));
        when(planService.termPlans(priya)).thenReturn(termPlans);
        when(planPrerequisites.missingFor(priya, termPlans))
                .thenReturn(Map.of(7L, List.of("Needs CMPT 225 before 2027 Fall.")));

        mockMvc.perform(get("/advisor/students/5").sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("2027 Fall")))
               .andExpect(content().string(containsString("CMPT 300")))
               .andExpect(content().string(containsString("3 units")))
               .andExpect(content().string(containsString("Needs CMPT 225 before 2027 Fall.")));
    }

    // A student with nothing saved yet gets a message in each section instead of an empty list
    @Test
    void studentWithNothingSaved_showsAMessageInEachSection() throws Exception {
        mockMvc.perform(get("/advisor/students/5").sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("No courses in their record yet.")))
               .andExpect(content().string(containsString("No courses in their schedule yet.")))
               .andExpect(content().string(containsString("No plan yet.")));
    }

    // Read-only: none of the Edit, Remove or Add course controls from the student's own pages
    @Test
    void overviewIsReadOnly() throws Exception {
        when(transcriptCourseRepository.findByStudentOrderByIdAsc(priya)).thenReturn(List.of(
                new TranscriptCourse(priya, "2025 Fall", "CMPT", "225", "Data Structures and Programming",
                        new BigDecimal("3.00"), "A")));
        PlanTerm fall = new PlanTerm(new Plan(priya, "My plan", true), new Term(2027, Season.FALL));
        when(planService.termPlans(priya)).thenReturn(List.of(new TermPlan(fall, List.of())));

        mockMvc.perform(get("/advisor/students/5").sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().isOk())
               .andExpect(content().string(not(containsString("/edit"))))
               .andExpect(content().string(not(containsString("Remove"))))
               .andExpect(content().string(not(containsString("Add course"))));
    }

    // #73: the plan section links to the student's plan pages, where the advisor can change them
    @Test
    void linksToTheStudentsPlanPages() throws Exception {
        mockMvc.perform(get("/advisor/students/5").sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("href=\"/advisor/students/5/plan\"")))
               .andExpect(content().string(containsString("href=\"/advisor/students/5/plans\"")));
    }

    // Another advisor's student, or an id that doesn't exist, is refused, and nothing about them is loaded
    @Test
    void unassignedStudent_isRefused() throws Exception {
        mockMvc.perform(get("/advisor/students/6").sessionAttr(SessionUser.SESSION_USER_ID, advisor.getId()))
               .andExpect(status().isForbidden());

        verify(studentProfileRepository, never()).findById(any());
        verify(transcriptCourseRepository, never()).findByStudentOrderByIdAsc(any());
    }

    // Students never reach it: AuthInterceptor sends them to their own home page
    @Test
    void student_isSentToTheirOwnHome() throws Exception {
        mockMvc.perform(get("/advisor/students/5").sessionAttr(SessionUser.SESSION_USER_ID, priyaUser.getId()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/student/home"));
    }

    // IT managers still can't see any student's coursework
    @Test
    void itManager_isSentToTheirOwnHome() throws Exception {
        mockMvc.perform(get("/advisor/students/5").sessionAttr(SessionUser.SESSION_USER_ID, itManager.getId()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/it/home"));

        verify(transcriptCourseRepository, never()).findByStudentOrderByIdAsc(any());
    }

    @Test
    void notLoggedIn_isSentToLogin() throws Exception {
        mockMvc.perform(get("/advisor/students/5"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/login"));
    }
}