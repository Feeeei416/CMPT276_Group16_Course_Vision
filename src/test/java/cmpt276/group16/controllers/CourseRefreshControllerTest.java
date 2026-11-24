package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.controllers.CourseRefreshController.TermOption;
import cmpt276.group16.courseimport.CourseImportService;
import cmpt276.group16.courseimport.CourseImportService.ImportAlreadyRunningException;
import cmpt276.group16.courseimport.CourseImportService.ImportRun;
import cmpt276.group16.courseimport.SfuApiException;
import cmpt276.group16.models.*;

// Uses the whole app, so AuthInterceptor is part of every test, like PlanControllerTest.
// The import itself is mocked: CourseImportServiceTest covers it, and these tests must never call SFU
@SpringBootTest (properties = "app.demo-password=")
@AutoConfigureMockMvc (addFilters = false)
@Transactional 
class CourseRefreshControllerTest {

    // November 2026, so the terms offered are 2026 Fall, 2027 Spring and 2027 Summer
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {return Clock.fixed(Instant.parse("2026-11-09T20:00:00Z"), ZoneId.of("America/Vancouver"));}
    }
 
    @Autowired
    private MockMvc mockMvc;
 
    @Autowired
    private UserRepository userRepository;
 
    @MockitoBean
    private CourseImportService importService;
 
    private MockHttpSession sessionFor(Role role) {
        User user = userRepository.save(new User(role.name().toLowerCase(Locale.ROOT) + "@example.com", "hash", "Faye", role));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LoginController.SESSION_USER, user);
        return session;
    }
 
    // July, when Vancouver is UTC-7 under both the old and new time zone rules.
    // BC stopped changing its clocks in 2026, so a November time shows an hour later on newer Java versions
    private static final Instant JULY_9 = Instant.parse("2026-07-09T22:10:00Z");

    private static ImportRun run() {
        return new ImportRun(2027, Season.SPRING, JULY_9, 52, 310, 2, 1, List.of());
    }

    // A 2027 Spring refresh that started on July 9 and hasn't finished yet
    private static CourseRefresh started(boolean automatic) {
        return new CourseRefresh(2027, Season.SPRING, automatic, JULY_9);
    }

    // -- The page --
 
    // The dropdown offers this term and the next two, with next semester picked
    @Test
    void itManagerSeesTheTermsWithNextSemesterPicked() throws Exception {
        mockMvc.perform(get("/it/course-refresh").session(sessionFor(Role.IT_MANAGER)))
               .andExpect(status().isOk())
               .andExpect(view().name("it/course-refresh"))
               .andExpect(content().string(containsString("2026 Fall")))
               .andExpect(model().attribute("selectedTerm", "2027-SPRING"))
               .andExpect(content().string(containsString("2027 Summer")))
               .andExpect(content().string(containsString("No refreshes yet.")));
    }
 
    // -- The log --

    // A refresh that worked shows its term, start time, type, counts and what was skipped
    @Test
    void logShowsARefreshThatWorked() throws Exception {
        CourseRefresh worked = started(false);
        worked.finish(JULY_9.plusSeconds(300), 52, 310, 2, 1, List.of("MATH 495 has no outline, skipped"));
        when(importService.refreshLog()).thenReturn(List.of(worked));

        mockMvc.perform(get("/it/course-refresh").session(sessionFor(Role.IT_MANAGER)))
               .andExpect(content().string(containsString("Jul 9, 2026, 3:10 PM")))
               .andExpect(content().string(containsString("Manual")))
               .andExpect(content().string(containsString("52 courses and 310 sections saved")))
               .andExpect(content().string(containsString("No longer offered: 2 sections and 1 course")))
               .andExpect(content().string(containsString("1 skipped")))
               .andExpect(content().string(containsString("MATH 495 has no outline, skipped")));
    }

    // A failed refresh shows why, and an automatic one says so
    @Test
    void logShowsWhyARefreshFailed() throws Exception {
        CourseRefresh failed = started(true);
        failed.fail(JULY_9.plusSeconds(60), "SFU is down");
        when(importService.refreshLog()).thenReturn(List.of(failed));

        mockMvc.perform(get("/it/course-refresh").session(sessionFor(Role.IT_MANAGER)))
               .andExpect(content().string(containsString("Automatic")))
               .andExpect(content().string(containsString("Failed: SFU is down")))
               .andExpect(content().string(not(containsString("sections saved"))));
    }

    @Test
    void logShowsTheRefreshRunningNow() throws Exception {
        CourseRefresh running = started(false);
        when(importService.refreshLog()).thenReturn(List.of(running));
        when(importService.isRunning(running)).thenReturn(true);

        mockMvc.perform(get("/it/course-refresh").session(sessionFor(Role.IT_MANAGER)))
               .andExpect(content().string(containsString("Running now")))
               .andExpect(content().string(not(containsString("Didn't finish"))));
    }

    // Not finished and not running means the app stopped partway through
    @Test
    void logShowsARefreshThatWasCutOff() throws Exception {
        when(importService.refreshLog()).thenReturn(List.of(started(true)));

        mockMvc.perform(get("/it/course-refresh").session(sessionFor(Role.IT_MANAGER)))
               .andExpect(content().string(containsString("Didn't finish: the app stopped partway through")))
               .andExpect(content().string(not(containsString("Running now"))));
    }

    // -- Refreshing --
 
    @Test
    void refreshImportsTheChosenTerm() throws Exception {
        when(importService.importTerm(2027, Season.SPRING)).thenReturn(run());
 
        mockMvc.perform(post("/it/course-refresh/refresh").param("term", "2027-SPRING")
                       .session(sessionFor(Role.IT_MANAGER)))
               .andExpect(redirectedUrl("/it/course-refresh"))
               .andExpect(flash().attribute("message", "Refreshed 2027 Spring: 52 courses and 310 sections."));
        verify(importService).importTerm(2027, Season.SPRING);
    }
 
    // When SFU can't be reached the page says so, and the import has already kept the old data
    @Test
    void apiFailureShowsAnError() throws Exception {
        when(importService.importTerm(2027, Season.SPRING)).thenThrow(new SfuApiException("SFU is down", null));
 
        mockMvc.perform(post("/it/course-refresh/refresh").param("term", "2027-SPRING")
                       .session(sessionFor(Role.IT_MANAGER)))
               .andExpect(redirectedUrl("/it/course-refresh"))
               .andExpect(flash().attribute("error", containsString("nothing was changed")));
    }
 
    @Test
    void secondRefreshWhileOneIsRunningShowsAnError() throws Exception {
        when(importService.importTerm(2027, Season.SPRING)).thenThrow(new ImportAlreadyRunningException());
 
        mockMvc.perform(post("/it/course-refresh/refresh").param("term", "2027-SPRING")
                       .session(sessionFor(Role.IT_MANAGER)))
               .andExpect(flash().attribute("error", containsString("already running")));
    }

    // Anything else that goes wrong still ends on the page with a message instead of an error page
    @Test
    void unexpectedFailureShowsAnError() throws Exception {
        when(importService.importTerm(2027, Season.SPRING)).thenThrow(new IllegalStateException("database is down"));

        mockMvc.perform(post("/it/course-refresh/refresh").param("term", "2027-SPRING")
                       .session(sessionFor(Role.IT_MANAGER)))
               .andExpect(redirectedUrl("/it/course-refresh"))
               .andExpect(flash().attribute("error", containsString("nothing was changed")));
    }
 
    // Only terms from the dropdown are imported, so a changed form can't start any other import
    @Test
    void termNotInTheListIsTurnedDown() throws Exception {
        MockHttpSession session = sessionFor(Role.IT_MANAGER);
        for (String term : List.of("2030-FALL", "", "2027-WINTER")) {
            mockMvc.perform(post("/it/course-refresh/refresh").param("term", term).session(session))
                   .andExpect(redirectedUrl("/it/course-refresh"))
                   .andExpect(flash().attribute("error", "Choose a term from the list."));
        }
        verify(importService, never()).importTerm(anyInt(), any());
    }
 
    // -- Who can use it --
 
    // AuthInterceptor sends other roles home before the controller runs, so no import starts
    @ParameterizedTest
    @EnumSource(value = Role.class, names = "IT_MANAGER", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesCantRefresh(Role role) throws Exception {
        mockMvc.perform(post("/it/course-refresh/refresh").param("term", "2027-SPRING").session(sessionFor(role)))
               .andExpect(redirectedUrl(LoginController.homePath(role)));
        verify(importService, never()).importTerm(anyInt(), any());
    }
 
    // Only IT managers see the log: AuthInterceptor sends other roles home before it's read
    @ParameterizedTest
    @EnumSource(value = Role.class, names = "IT_MANAGER", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesCantSeeTheLog(Role role) throws Exception {
        mockMvc.perform(get("/it/course-refresh").session(sessionFor(role)))
               .andExpect(redirectedUrl(LoginController.homePath(role)));
        verify(importService, never()).refreshLog();
    }

    // -- Which terms are offered --
 
    @Test
    void termsAreThisTermAndTheNextTwo() {
        assertThat(CourseRefreshController.termOptions(LocalDate.of(2026, 11, 15))).containsExactly(
                new TermOption(2026, Season.FALL), new TermOption(2027, Season.SPRING),
                new TermOption(2027, Season.SUMMER));
        assertThat(CourseRefreshController.termOptions(LocalDate.of(2027, 1, 5))).containsExactly(
                new TermOption(2027, Season.SPRING), new TermOption(2027, Season.SUMMER),
                new TermOption(2027, Season.FALL));
        assertThat(CourseRefreshController.termOptions(LocalDate.of(2027, 8, 31))).containsExactly(
                new TermOption(2027, Season.SUMMER), new TermOption(2027, Season.FALL),
                new TermOption(2028, Season.SPRING));
    }
}
