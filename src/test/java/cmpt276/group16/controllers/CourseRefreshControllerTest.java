package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
 
    private static ImportRun run(List<String> notes, String error) {
        return new ImportRun(2027, Season.SPRING, Instant.parse("2026-11-09T23:10:00Z"), 52, 310, 2, 1, notes, error);
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
               .andExpect(content().string(containsString("No refresh since the app last started.")));
    }
 
    // The last refresh is shown with its counts, time and what was skipped
    @Test
    void showsTheLastRefresh() throws Exception {
        when(importService.lastRun()).thenReturn(Optional.of(run(List.of("MATH 495 has no outline, skipped"), null)));
 
        mockMvc.perform(get("/it/course-refresh").session(sessionFor(Role.IT_MANAGER)))
               .andExpect(content().string(containsString("Nov 9, 2026, 3:10 PM")))
               .andExpect(content().string(containsString("52 courses and 310 sections saved")))
               .andExpect(content().string(containsString("MATH 495 has no outline, skipped")));
    }
 
    // A refresh that stopped shows why
    @Test
    void showsWhyTheLastRefreshStopped() throws Exception {
        when(importService.lastRun()).thenReturn(Optional.of(run(List.of(), "SFU is down")));
 
        mockMvc.perform(get("/it/course-refresh").session(sessionFor(Role.IT_MANAGER)))
               .andExpect(content().string(containsString("Stopped: SFU is down")));
    }
 
    // -- Refreshing --
 
    @Test
    void refreshImportsTheChosenTerm() throws Exception {
        when(importService.importTerm(2027, Season.SPRING)).thenReturn(run(List.of(), null));
 
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
