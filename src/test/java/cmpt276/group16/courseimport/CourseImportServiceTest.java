package cmpt276.group16.courseimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.AfterTransaction;

import cmpt276.group16.config.ClockConfig;
import cmpt276.group16.courseimport.CourseImportService.ImportRun;
import cmpt276.group16.models.*;
import cmpt276.group16.requisites.ManualRules;
import cmpt276.group16.requisites.RequisiteImportService;
import cmpt276.group16.services.TermCreator;

// The client is replaced by a mock that answers with the real responses saved in src/test/resources/sfu,
// so no test calls the SFU API. Each test runs in a transaction that is rolled back afterwards
@DataJpaTest 
@Import ({CourseImportService.class, SfuOutlineMapper.class, TermCreator.class, ClockConfig.class,
        RequisiteImportService.class, ManualRules.class})
class CourseImportServiceTest {
    
    // The test that adds a new term uses a year nothing else uses, so cleaning it up can't touch other data
    private static final int NEW_TERM_YEAR = 2098;
 
    // CMPT 225, plus 725, a graduate course the import should skip
    private static final String CMPT_COURSES = """
            [{"text":"225","value":"225","title":"Data Structures and Programming"},
             {"text":"725","value":"725","title":"Graduate Course"}]
            """;

    @MockitoBean
    private SfuCourseOutlinesClient client;
 
    @Autowired
    private CourseImportService importService;
 
    @Autowired
    private TermRepository termRepository;
 
    @Autowired
    private CourseRepository courseRepository;
 
    @Autowired
    private CourseDesignationRepository designationRepository;
 
    @Autowired
    private CourseOfferingRepository offeringRepository;
 
    @Autowired
    private SectionRepository sectionRepository;

    @Autowired
    private RequisiteRuleRepository requisiteRuleRepository;
 
    private Term spring;

    private static String saved(String name) throws IOException {
        try (InputStream in = CourseImportServiceTest.class.getResourceAsStream("/sfu/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // Answers like the API for CMPT 225 in any term: D100 and D101 have outlines,
    // the other sections answer 404 and other departments have no courses
    @BeforeEach
    void setUp() throws IOException {
        spring = termRepository.saveAndFlush(new Term(2027, Season.SPRING));
        when(client.listCourses(anyInt(), any(), eq("CMPT"))).thenReturn(Optional.of(CMPT_COURSES));
        answerSections(saved("cmpt-225-sections.json"));
        when(client.getOutline(anyInt(), any(), eq("CMPT"), eq("225"), eq("D100")))
                .thenReturn(Optional.of(saved("cmpt-225-d100.json")));
        when(client.getOutline(anyInt(), any(), eq("CMPT"), eq("225"), eq("D101")))
                .thenReturn(Optional.of(saved("cmpt-225-d101.json")));
    }
 
    private void answerSections(String json) {
        when(client.listSections(anyInt(), any(), eq("CMPT"), eq("225"))).thenReturn(Optional.of(json));
    }
 
    private Course cmpt225() {
        return courseRepository.findByNormalizedSubjectAndNumber("CMPT", "225").orElseThrow();
    }
 
    private CourseOffering cmpt225Offering() {
        return offeringRepository.findByCourseIdAndTermId(cmpt225().getId(), spring.getId()).orElseThrow();
    }
 
    private List<Section> cmpt225Sections() {
        return sectionRepository.findByOfferingIdOrderBySectionCodeAsc(cmpt225Offering().getId());
    }
 
    // The term created by addsTheTermIfItIsNew is saved in its own transaction, so it isn't rolled back
    @AfterTransaction
    void deleteNewTerm() {
        termRepository.findByTermYearAndSeason(NEW_TERM_YEAR, Season.FALL).ifPresent(termRepository::delete);
    }

    // The course, its designation, its offering and all its sections are saved; the graduate course isn't
    @Test 
    void importsCoursesAndSections() {
        ImportRun run = importService.importTerm(2027, Season.SPRING);
 
        assertThat(run.courses()).isEqualTo(1);
        assertThat(run.sections()).isEqualTo(10);
        assertThat(run.error()).isNull();
 
        Course course = cmpt225();
        assertThat(course.getTitle()).isEqualTo("Data Structures and Programming");
        assertThat(course.getUnits()).isEqualByComparingTo("3");
        assertThat(course.getPrereqText()).startsWith("(MACM 101 and");
        assertThat(designationRepository.findByCourseId(course.getId()))
                .extracting(CourseDesignation::getDesignation).containsExactly(Designation.Q);
        assertThat(cmpt225Offering().isOffered()).isTrue();
        assertThat(courseRepository.findByNormalizedSubjectAndNumber("CMPT", "725")).isEmpty();
 
        List<Section> sections = cmpt225Sections();
        assertThat(sections).hasSize(10);
        assertThat(sections.get(0).getSectionCode()).isEqualTo("D100");
        assertThat(sections.get(0).getMeetings()).hasSize(2);
        assertThat(sections.get(1).getMeetings()).hasSize(1);
        // D102 answered 404, so it is kept without meeting times
        assertThat(sections.get(2).getMeetings()).isEmpty();
    }

    // CMPT 225's prerequisite text is turned into rules as it's saved
    @Test
    void parsesPrerequisitesIntoRules() {
        importService.importTerm(2027, Season.SPRING);

        assertThat(cmpt225().getParseStatus()).isEqualTo(Course.ParseStatus.PARSED);
        assertThat(requisiteRuleRepository.findByCourseIdAndRuleKindAndParentIsNull(cmpt225().getId(),
                RuleKind.PREREQUISITE)).map(RequisiteRule::getNodeType).contains(NodeType.OR);
    }

    // Importing again replaces the rules, so the course still has one tree
    @Test
    void importingAgainReplacesThePrerequisiteRules() {
        importService.importTerm(2027, Season.SPRING);
        int rules = requisiteRuleRepository.findByCourseIdAndRuleKindOrderByIdAsc(cmpt225().getId(),
                RuleKind.PREREQUISITE).size();

        importService.importTerm(2027, Season.SPRING);

        assertThat(requisiteRuleRepository.findByCourseIdAndRuleKindOrderByIdAsc(cmpt225().getId(),
                RuleKind.PREREQUISITE)).hasSize(rules);
    }

    // Text the parser can't read marks the course FAILED, and the rest of the import still saves
    @Test
    void prerequisitesThatDontParseDontStopTheImport() throws IOException {
        String cmpt225Text = "(MACM 101 and (CMPT 125, CMPT 129 or CMPT 135)) or (ENSC 251 and ENSC 252), "
                + "all with a minimum grade of C-.";
        // CMPT 276's text, which the parser turns down (see SavedOutlinesTest)
        String unreadable = "One W course, CMPT 225, (MACM 101 or (ENSC 251 and ENSC 252)) and (MATH 151 or "
                + "MATH 150), all with a minimum grade of C-. MATH 154 or MATH 157 with at least a B+ may be "
                + "substituted for MATH 151 or MATH 150.";
        for (String section : List.of("D100", "D101")) {
            String outline = saved("cmpt-225-" + section.toLowerCase() + ".json");
            assertThat(outline).contains(cmpt225Text);
            when(client.getOutline(anyInt(), any(), eq("CMPT"), eq("225"), eq(section)))
                    .thenReturn(Optional.of(outline.replace(cmpt225Text, unreadable)));
        }

        ImportRun run = importService.importTerm(2027, Season.SPRING);

        assertThat(run.error()).isNull();
        assertThat(cmpt225().getParseStatus()).isEqualTo(Course.ParseStatus.FAILED);
        assertThat(requisiteRuleRepository.findByCourseIdAndRuleKindOrderByIdAsc(cmpt225().getId(),
                RuleKind.PREREQUISITE)).isEmpty();
        assertThat(cmpt225Sections()).hasSize(10);
    }

    // Running the same import again updates the rows instead of adding new ones
    @Test
    void importingAgainDoesNotDuplicate() {
        importService.importTerm(2027, Season.SPRING);
        long courses = courseRepository.count();
        long offerings = offeringRepository.count();
        long sections = sectionRepository.count();
        long terms = termRepository.count();
 
        importService.importTerm(2027, Season.SPRING);
 
        assertThat(courseRepository.count()).isEqualTo(courses);
        assertThat(offeringRepository.count()).isEqualTo(offerings);
        assertThat(sectionRepository.count()).isEqualTo(sections);
        assertThat(termRepository.count()).isEqualTo(terms);
    }
 
    // A course that is already saved, like a demo course, is updated in place,
    // and its new prerequisite text is parsed again straight away
    @Test
    void updatesACourseThatIsAlreadySaved() {
        Course old = new Course("CMPT", "225", "Old title", new BigDecimal("4.00"), null, "Old prerequisites", null);
        old.setParseStatus(Course.ParseStatus.PARSED);
        courseRepository.saveAndFlush(old);
 
        importService.importTerm(2027, Season.SPRING);
 
        assertThat(courseRepository.findAll()).hasSize(1);
        Course course = cmpt225();
        assertThat(course.getTitle()).isEqualTo("Data Structures and Programming");
        assertThat(course.getUnits()).isEqualByComparingTo("3");
        assertThat(course.getParseStatus()).isEqualTo(Course.ParseStatus.PARSED);
        assertThat(requisiteRuleRepository.findByCourseIdAndRuleKindAndParentIsNull(course.getId(),
                RuleKind.PREREQUISITE)).map(RequisiteRule::getNodeType).contains(NodeType.OR);
    }
 
    // When the API fails partway, nothing is saved and the saved data stays as it was
    @Test
    void apiFailureKeepsTheSavedData() {
        importService.importTerm(2027, Season.SPRING);
        when(client.listSections(anyInt(), any(), eq("CMPT"), eq("225")))
                .thenThrow(new SfuApiException("SFU is down", null));
 
        assertThatThrownBy(() -> importService.importTerm(2027, Season.SPRING))
                .isInstanceOf(SfuApiException.class);
 
        assertThat(cmpt225Sections()).hasSize(10);
        assertThat(importService.lastRun()).map(ImportRun::error).contains("SFU is down");
        assertThat(importService.isRunning()).isFalse();
    }
 
    // CMPT 225's sections without D104, as if SFU cancelled that lab
    private static String sectionsWithoutD104() throws IOException {
        return saved("cmpt-225-sections.json").replace("{\"text\":\"D104\",\"value\":\"d104\","
                + "\"title\":\"Data Structures and Programming\",\"classType\":\"n\",\"sectionCode\":\"LAB\","
                + "\"associatedClass\":\"1\"},", "");
    }

    private Section cmpt225Section(String code) {
        return cmpt225Sections().stream().filter(s -> s.getSectionCode().equals(code)).findFirst().orElseThrow();
    }

    // A section SFU no longer lists is kept but marked not offered, since a schedule can point at it
    @Test
    void marksSectionsSfuNoLongerListsNotOffered() throws IOException {
        importService.importTerm(2027, Season.SPRING);
        answerSections(sectionsWithoutD104());

        ImportRun run = importService.importTerm(2027, Season.SPRING);

        assertThat(run.removedSections()).isEqualTo(1);
        assertThat(cmpt225Sections()).hasSize(10);
        assertThat(cmpt225Sections()).filteredOn(s -> !s.isOffered())
                .extracting(Section::getSectionCode).containsExactly("D104");
    }

    // Only counted the first time: a section already marked isn't counted again on the next import
    @Test
    void sectionAlreadyMarkedNotOfferedIsntCountedAgain() throws IOException {
        importService.importTerm(2027, Season.SPRING);
        answerSections(sectionsWithoutD104());
        importService.importTerm(2027, Season.SPRING);

        ImportRun run = importService.importTerm(2027, Season.SPRING);

        assertThat(run.removedSections()).isZero();
        assertThat(cmpt225Section("D104").isOffered()).isFalse();
    }

    // A cancelled section that SFU lists again is offered again
    @Test
    void sectionListedAgainIsOfferedAgain() throws IOException {
        importService.importTerm(2027, Season.SPRING);
        answerSections(sectionsWithoutD104());
        importService.importTerm(2027, Season.SPRING);
        answerSections(saved("cmpt-225-sections.json"));

        importService.importTerm(2027, Season.SPRING);

        assertThat(cmpt225Section("D104").isOffered()).isTrue();
    }
 
    // A course missing from the next import is marked not offered, but the course itself is kept
    @Test
    void courseNoLongerListedIsNotOffered() {
        importService.importTerm(2027, Season.SPRING);
        when(client.listCourses(anyInt(), any(), eq("CMPT"))).thenReturn(Optional.of("[]"));
 
        ImportRun run = importService.importTerm(2027, Season.SPRING);
 
        assertThat(run.notOffered()).isEqualTo(1);
        assertThat(cmpt225Offering().isOffered()).isFalse();
    }
 
    // A term that isn't saved yet is added
    @Test
    void addsTheTermIfItIsNew() {
        importService.importTerm(NEW_TERM_YEAR, Season.FALL);
 
        assertThat(termRepository.findByTermYearAndSeason(NEW_TERM_YEAR, Season.FALL)).isPresent();
    }
 
    // 1xx to 4xx are undergraduate, with or without a letter
    @Test
    void onlyUndergraduateNumbersAreImported() {
        assertThat(CourseImportService.isUndergraduate("105W")).isTrue();
        assertThat(CourseImportService.isUndergraduate("499")).isTrue();
        assertThat(CourseImportService.isUndergraduate("725")).isFalse();
        assertThat(CourseImportService.isUndergraduate("500")).isFalse();
    }

    // A course whose sections all answer 404 has nothing to take its title or units from, so it is skipped
    @Test
    void courseWithNoOutlineIsSkipped() {
        when(client.getOutline(anyInt(), any(), eq("CMPT"), eq("225"), any())).thenReturn(Optional.empty());

        ImportRun run = importService.importTerm(2027, Season.SPRING);

        assertThat(run.courses()).isZero();
        assertThat(run.notes()).contains("CMPT 225 has no outline, skipped");
        assertThat(courseRepository.findByNormalizedSubjectAndNumber("CMPT", "225")).isEmpty();
    }

    // A new course needs units, which the courses table requires
    @Test
    void newCourseWithoutUnitsIsSkipped() {
        when(client.getOutline(anyInt(), any(), eq("CMPT"), eq("225"), any()))
                .thenReturn(Optional.of("{\"info\": {\"title\": \"Data Structures and Programming\"}}"));

        ImportRun run = importService.importTerm(2027, Season.SPRING);

        assertThat(run.courses()).isZero();
        assertThat(run.notes()).contains("CMPT 225 has no title or units, skipped");
        assertThat(courseRepository.findByNormalizedSubjectAndNumber("CMPT", "225")).isEmpty();
    }

    // A database error while saving (here units too big for the column) is recorded as a failed run,
    // and another refresh can start afterwards
    @Test
    void failureWhileSavingIsRecorded() {
        when(client.getOutline(anyInt(), any(), eq("CMPT"), eq("225"), any()))
                .thenReturn(Optional.of("{\"info\": {\"title\": \"Too Many Units\", \"units\": \"12345\"}}"));

        assertThatThrownBy(() -> importService.importTerm(2027, Season.SPRING))
                .isInstanceOf(RuntimeException.class);

        assertThat(importService.lastRun()).map(ImportRun::error)
                .hasValueSatisfying(error -> assertThat(error).contains("nothing was changed"));
        assertThat(importService.isRunning()).isFalse();
    }
}
