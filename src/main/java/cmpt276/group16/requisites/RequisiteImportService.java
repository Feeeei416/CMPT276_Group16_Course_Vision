package cmpt276.group16.requisites;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Course;
import cmpt276.group16.models.CourseRepository;
import cmpt276.group16.models.NodeType;
import cmpt276.group16.models.RequisiteRule;
import cmpt276.group16.models.RequisiteRuleRepository;
import cmpt276.group16.models.RuleKind;
import cmpt276.group16.requisites.Requirement.And;
import cmpt276.group16.requisites.Requirement.CourseReq;
import cmpt276.group16.requisites.Requirement.MinUnits;
import cmpt276.group16.requisites.Requirement.Or;
import cmpt276.group16.requisites.Requirement.Special;

// Turns a course's prerequisite text into RequisiteRule rows.
// Rules entered by hand (MANUAL, from ManualRules) replace the imported ones instead of sitting beside them:
// if a course has any, its imported rules aren't saved, so a re-import can't bring back a rule someone fixed.
// That way a course only ever has one tree, which findByCourseIdAndRuleKindAndParentIsNull relies on
@Service
public class RequisiteImportService {

    private static final Logger log = LoggerFactory.getLogger(RequisiteImportService.class);

    private final RequisiteRuleRepository requisiteRuleRepository;
    private final CourseRepository courseRepository;
    private final ManualRules manualRules;

    public RequisiteImportService(RequisiteRuleRepository requisiteRuleRepository,
            CourseRepository courseRepository, ManualRules manualRules) {
        this.requisiteRuleRepository = requisiteRuleRepository;
        this.courseRepository = courseRepository;
        this.manualRules = manualRules;
    }

    // Saves the course's hand-entered rule if it has one, otherwise parses prereqText again and replaces
    // the course's imported rules. Returns the new parse status.
    // FAILED leaves the course with no rules, so the student is shown prereqText instead.
    // This is one transaction, so a failed save leaves the old rules in place
    @Transactional
    public Course.ParseStatus importPrerequisites(Course course) {
        // The old rules came from outdated text so they should be removed regardless of if the new rules parse or not
        deleteRules(course, RequisiteRule.Source.IMPORTED);
        // The manual rules file is the only place MANUAL rules come from, so the saved ones always go too.
        // Otherwise a rule taken out of the file would stay in the database and keep winning over the text
        deleteRules(course, RequisiteRule.Source.MANUAL);

        // Saved again each time, so a fix to the manual rules file reaches the database on the next import.
        // The text isn't needed then, so the course isn't flagged even if its text doesn't parse
        Optional<Requirement> manual = manualRules.forCourse(course);
        if (manual.isPresent()) {
            save(manual.get(), course, null, RequisiteRule.Source.MANUAL);
            return setStatus(course, Course.ParseStatus.PARSED);
        }

        Optional<Requirement> requirement;
        try {
            requirement = RequisiteParser.parse(course.getPrereqText());
        } catch (RequisiteParseException e) {
            return setStatus(course, Course.ParseStatus.FAILED);
        } catch (RuntimeException e) {
            // A bug in the parser, not text it turned down. Caught here because this method joins the course
            // import's transaction, and an exception leaving it would roll back the whole term's refresh.
            // Only the parse is covered: a database error while saving should still roll everything back
            log.warn("Parser crashed on {} {}: \"{}\"", course.getSubject(), course.getNumber(),
                    course.getPrereqText(), e);
            return setStatus(course, Course.ParseStatus.FAILED);
        }
        requirement.ifPresent(root -> save(root, course, null, RequisiteRule.Source.IMPORTED));
        return setStatus(course, Course.ParseStatus.PARSED);
    }

    // Newest first, so children are deleted before the groups they point at
    private void deleteRules(Course course, RequisiteRule.Source source) {
        requisiteRuleRepository.deleteAll(requisiteRuleRepository.findByCourseIdAndRuleKindAndSourceOrderByIdDesc(
                course.getId(), RuleKind.PREREQUISITE, source));
        requisiteRuleRepository.flush();
    }

    private Course.ParseStatus setStatus(Course course, Course.ParseStatus status) {
        course.setParseStatus(status);
        courseRepository.save(course);
        return status;
    }

    // save the node before its children so a parent always has a smaller id than the nodes under it
    private void save(Requirement requirement, Course course, RequisiteRule parent, RequisiteRule.Source source) {
        if (requirement instanceof And and) {
            RequisiteRule group = requisiteRuleRepository.save(
                    new RequisiteRule(course, parent, RuleKind.PREREQUISITE, NodeType.AND, source));
            and.parts().forEach(part -> save(part, course, group, source));
        } else if (requirement instanceof Or or) {
            RequisiteRule group = requisiteRuleRepository.save(
                    new RequisiteRule(course, parent, RuleKind.PREREQUISITE, NodeType.OR, source));
            or.parts().forEach(part -> save(part, course, group, source));
        } else if (requirement instanceof CourseReq required) {
            RequisiteRule node = new RequisiteRule(course, parent, RuleKind.PREREQUISITE, NodeType.COURSE, source);
            node.setRequiredSubjectAndNumber(required.subject(), required.number());
            node.setMinGrade(required.minGrade());
            node.setConcurrentOk(required.concurrentOk());
            // Null until the required course is imported too
            node.setRequiredCourse(courseRepository
                    .findBySubjectAndNumber(node.getReqSubject(), node.getReqNumber()).orElse(null));
            requisiteRuleRepository.save(node);
        } else if (requirement instanceof MinUnits units) {
            RequisiteRule node = new RequisiteRule(course, parent, RuleKind.PREREQUISITE, NodeType.MIN_UNITS, source);
            node.setMinUnits(units.units());
            requisiteRuleRepository.save(node);
        } else if (requirement instanceof Special special) {
            RequisiteRule node = new RequisiteRule(course, parent, RuleKind.PREREQUISITE, NodeType.SPECIAL, source);
            node.setSpecialText(special.text());
            requisiteRuleRepository.save(node);
        }
    }
}
