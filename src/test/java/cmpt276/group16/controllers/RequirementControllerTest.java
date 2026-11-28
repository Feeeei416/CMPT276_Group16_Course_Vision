package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.graduation.RequirementEditor;
import cmpt276.group16.models.*;

// Uses the whole app with the test database (H2), so AuthInterceptor is part of every test.
// Each test makes its own set, for a year requirements.json doesn't have, so the file's sets don't matter
@SpringBootTest(properties = "app.demo-password=")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class RequirementControllerTest {

    private static final String NOTE = """
            { "category": "Notes", "kind": "NOTE", "text": "Saved before the test." }
            """;

    // count is more than the courses listed, which the file's checks turn down
    private static final String MISTAKE = """
            { "category": "Lower division", "kind": "CHOOSE", "count": 3, "courses": ["MATH 150", "MATH 151"] }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RequirementSetRepository setRepository;

    @Autowired
    private RequirementEditor editor;

    private RequirementSet set;

    @BeforeEach
    void setUp() {
        set = setRepository.save(new RequirementSet(Program.COMPUTING_SCIENCE, 2099, null));
        editor.add(set.getId(), NOTE);
    }

    private MockHttpSession sessionFor(Role role) {
        User user = userRepository.save(new User(role.name().toLowerCase(Locale.ROOT) + "@example.com", "hash",
                "Faye", role));
        MockHttpSession session = new MockHttpSession();
        SessionUser.signIn(session, user);
        return session;
    }

    private MockHttpSession itManager() {
        return sessionFor(Role.IT_MANAGER);
    }

    private List<RequirementEditor.Row> rows() {
        return editor.rows(set);
    }

    private Long firstId() {
        return rows().get(0).requirement().getId();
    }

    private String listUrl() {
        return "/it/requirements?set=" + set.getId();
    }

    // -- The page --

    @Test
    void showsTheChosenSetsRequirementsAsJson() throws Exception {
        mockMvc.perform(get("/it/requirements").param("set", set.getId().toString()).session(itManager()))
               .andExpect(status().isOk())
               .andExpect(view().name("it/requirements"))
               .andExpect(model().attribute("chosen", set))
               .andExpect(content().string(containsString("Computing Science, entry 2099")))
               .andExpect(content().string(containsString("Saved before the test.")))
               .andExpect(content().string(containsString("/it/requirements/sets/" + set.getId() + "/add")));
    }

    // With no set chosen, the first one is shown instead of an empty page
    @Test
    void showsTheFirstSetWhenNoneIsChosen() throws Exception {
        mockMvc.perform(get("/it/requirements").session(itManager()))
               .andExpect(status().isOk())
               .andExpect(model().attributeExists("chosen"))
               .andExpect(model().attributeExists("rows"));
    }

    // -- Adding --

    // The add page shows which set it's for, the examples and an empty box
    @Test
    void addPageHasAnEmptyBoxAndExamples() throws Exception {
        mockMvc.perform(get("/it/requirements/sets/{id}/add", set.getId()).session(itManager()))
               .andExpect(status().isOk())
               .andExpect(view().name("it/requirement-add"))
               .andExpect(model().attribute("json", ""))
               .andExpect(content().string(containsString("Computing Science, entry 2099")))
               .andExpect(content().string(containsString("Examples")));
    }

    // After a mistake, the add page shows what was typed and why it was turned down
    @Test
    void addPageAfterAMistakeShowsWhatWasTyped() throws Exception {
        mockMvc.perform(get("/it/requirements/sets/{id}/add", set.getId()).flashAttr("json", MISTAKE)
                       .flashAttr("error", "count is 3 but only 2 courses are listed").session(itManager()))
               .andExpect(model().attribute("json", MISTAKE))
               .andExpect(content().string(containsString("count is 3 but only 2 courses are listed")));
    }

    @Test
    void addingSavesTheRequirementAndGoesBackToTheSet() throws Exception {
        mockMvc.perform(post("/it/requirements/sets/{id}/add", set.getId())
                       .param("json", "{ \"category\": \"Notes\", \"kind\": \"NOTE\", \"text\": \"Added\" }")
                       .session(itManager()))
               .andExpect(redirectedUrl(listUrl()))
               .andExpect(flash().attribute("message", "Requirement added."));

        assertThat(rows()).hasSize(2);
        assertThat(rows().get(1).json()).contains("Added");
    }

    // A mistake shows the file check's message and keeps what was typed, so it can be fixed
    @Test
    void addingWithAMistakeKeepsWhatWasTyped() throws Exception {
        mockMvc.perform(post("/it/requirements/sets/{id}/add", set.getId())
                       .param("json", MISTAKE).session(itManager()))
               .andExpect(redirectedUrl("/it/requirements/sets/" + set.getId() + "/add"))
               .andExpect(flash().attribute("error", "count is 3 but only 2 courses are listed"))
               .andExpect(flash().attribute("json", MISTAKE));

        assertThat(rows()).hasSize(1);
    }

    // -- Changing --

    @Test
    void editPageFillsTheBoxWithTheRequirement() throws Exception {
        mockMvc.perform(get("/it/requirements/{id}/edit", firstId()).session(itManager()))
               .andExpect(status().isOk())
               .andExpect(view().name("it/requirement-edit"))
               .andExpect(model().attribute("json", containsString("Saved before the test.")))
               .andExpect(content().string(containsString("Saved before the test.")));
    }

    @Test
    void savingAnEditGoesBackToTheSet() throws Exception {
        mockMvc.perform(post("/it/requirements/{id}/edit", firstId())
                       .param("json", "{ \"category\": \"Notes\", \"kind\": \"NOTE\", \"text\": \"Changed\" }")
                       .session(itManager()))
               .andExpect(redirectedUrl(listUrl()))
               .andExpect(flash().attribute("message", "Requirement saved."));

        assertThat(rows()).hasSize(1);
        assertThat(rows().get(0).json()).contains("Changed");
    }

    // A mistake goes back to the edit page with what was typed, and the requirement stays as it was
    @Test
    void editWithAMistakeStaysOnTheEditPage() throws Exception {
        Long id = firstId();

        mockMvc.perform(post("/it/requirements/{id}/edit", id).param("json", MISTAKE).session(itManager()))
               .andExpect(redirectedUrl("/it/requirements/" + id + "/edit"))
               .andExpect(flash().attribute("error", "count is 3 but only 2 courses are listed"))
               .andExpect(flash().attribute("json", MISTAKE));

        assertThat(rows().get(0).json()).contains("Saved before the test.");
    }

    // After a mistake, the edit page shows what was typed instead of what's saved
    @Test
    void editPageAfterAMistakeShowsWhatWasTyped() throws Exception {
        mockMvc.perform(get("/it/requirements/{id}/edit", firstId()).flashAttr("json", MISTAKE)
                       .flashAttr("error", "count is 3 but only 2 courses are listed").session(itManager()))
               .andExpect(model().attribute("json", MISTAKE))
               .andExpect(content().string(containsString("count is 3 but only 2 courses are listed")));
    }

    // -- Copying to a new entry year --

    @Test
    void pageOffersToCopyTheSet() throws Exception {
        mockMvc.perform(get("/it/requirements").param("set", set.getId().toString()).session(itManager()))
               .andExpect(content().string(containsString("/it/requirements/sets/" + set.getId() + "/copy")));
    }

    // A copy goes to the new set, so its differences can be changed straight away
    @Test
    void copyingGoesToTheNewSet() throws Exception {
        MockHttpSession session = itManager();
        mockMvc.perform(post("/it/requirements/sets/{id}/copy", set.getId()).param("entryYear", "2098")
                       .session(session))
               .andExpect(flash().attribute("message",
                       "Copied to Computing Science, entry 2098. Change anything that's different for that year."));

        RequirementSet copy = setRepository.findByProgramAndEntryYear(Program.COMPUTING_SCIENCE, 2098).orElseThrow();
        assertThat(editor.rows(copy)).hasSize(1);
        // Checked with a second copy, since a copy's id is only known once it's saved
        mockMvc.perform(post("/it/requirements/sets/{id}/copy", set.getId()).param("entryYear", "2097")
                       .session(session))
               .andExpect(redirectedUrl("/it/requirements?set="
                       + setRepository.findByProgramAndEntryYear(Program.COMPUTING_SCIENCE, 2097).orElseThrow().getId()));
    }

    @Test
    void copyingToAYearThatHasRequirementsShowsAnError() throws Exception {
        mockMvc.perform(post("/it/requirements/sets/{id}/copy", set.getId()).param("entryYear", "2099")
                       .session(itManager()))
               .andExpect(redirectedUrl(listUrl()))
               .andExpect(flash().attribute("error", "Computing Science already has requirements for entry 2099."));
    }

    @Test
    void copyingWithoutAYearShowsAnError() throws Exception {
        mockMvc.perform(post("/it/requirements/sets/{id}/copy", set.getId()).session(itManager()))
               .andExpect(redirectedUrl(listUrl()))
               .andExpect(flash().attribute("error", "Enter the entry year to copy to."));
    }

    // -- Removing --

    @Test
    void removingDeletesTheRequirementAndGoesBackToTheSet() throws Exception {
        mockMvc.perform(post("/it/requirements/{id}/remove", firstId()).session(itManager()))
               .andExpect(redirectedUrl(listUrl()))
               .andExpect(flash().attribute("message", "Requirement removed."));

        assertThat(rows()).isEmpty();
    }

    // -- Something that isn't there --

    @Test
    void unknownRequirementIsNotFound() throws Exception {
        MockHttpSession session = itManager();
        mockMvc.perform(get("/it/requirements/{id}/edit", 999_999L).session(session))
               .andExpect(status().isNotFound());
        mockMvc.perform(post("/it/requirements/{id}/remove", 999_999L).session(session))
               .andExpect(status().isNotFound());
        mockMvc.perform(get("/it/requirements/sets/{id}/add", 999_999L).session(session))
               .andExpect(status().isNotFound());
    }

    // -- Who can use it --

    // AuthInterceptor sends other roles home before the controller runs, so nothing is shown or changed
    @ParameterizedTest
    @EnumSource(value = Role.class, names = "IT_MANAGER", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesCantSeeOrChangeRequirements(Role role) throws Exception {
        MockHttpSession session = sessionFor(role);
        mockMvc.perform(get("/it/requirements").session(session))
               .andExpect(redirectedUrl(LoginController.homePath(role)));
        mockMvc.perform(post("/it/requirements/sets/{id}/add", set.getId()).param("json", NOTE).session(session))
               .andExpect(redirectedUrl(LoginController.homePath(role)));
        mockMvc.perform(post("/it/requirements/sets/{id}/copy", set.getId()).param("entryYear", "2098")
                       .session(session))
               .andExpect(redirectedUrl(LoginController.homePath(role)));

        assertThat(rows()).hasSize(1);
        assertThat(setRepository.findByProgramAndEntryYear(Program.COMPUTING_SCIENCE, 2098)).isEmpty();
    }
}
