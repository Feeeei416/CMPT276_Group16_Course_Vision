package cmpt276.group16.requisites;

import java.util.List;
import java.util.Optional;

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
// Rules entered by hand (MANUAL) replace the imported ones instead of sitting beside them: if a course has any,
// its imported rules aren't saved, so a re-import can't bring back a rule someone fixed.
// That way a course only ever has one tree, which findByCourseIdAndRuleKindAndParentIsNull relies on
@Service
public class RequisiteImportService {

    private final RequisiteRuleRepository requisiteRuleRepository;
    private final CourseRepository courseRepository;

    public RequisiteImportService(RequisiteRuleRepository requisiteRuleRepository,
            CourseRepository courseRepository) {
        this.requisiteRuleRepository = requisiteRuleRepository;
        this.courseRepository = courseRepository;
    }

    // Parses prereqText again and replaces the course's imported rules, then returns the new parse status.
    // FAILED leaves the course with no imported rules, so the student is shown prereqText instead.
    // This is one transaction, so a failed save leaves the old rules in place
    @Transactional
    public Course.ParseStatus importPrerequisites(Course course) {
        // The old rules came from outdated text so they should be removed regardless of if the new rules parse or not 
        List<RequisiteRule> imported = requisiteRuleRepository.findByCourseIdAndRuleKindAndSourceOrderByIdDesc(
                course.getId(), RuleKind.PREREQUISITE, RequisiteRule.Source.IMPORTED);
        requisiteRuleRepository.deleteAll(imported);
        requisiteRuleRepository.flush();

        Optional<Requirement> requirement;
        try {
            requirement = RequisiteParser.parse(course.getPrereqText());
		} catch (RequisiteParseException e) {
			return setStatus(course, Course.ParseStatus.FAILED);
		}

        boolean hasManualRules = requisiteRuleRepository.existsByCourseIdAndRuleKindAndSource(
                course.getId(), RuleKind.PREREQUISITE, RequisiteRule.Source.MANUAL);
        if (!hasManualRules) {
            requirement.ifPresent(root -> save(root, course, null));
        }
        return setStatus(course, Course.ParseStatus.PARSED);
    }

    private Course.ParseStatus setStatus(Course course, Course.ParseStatus status) {
        course.setParseStatus(status);
        courseRepository.save(course);
        return status;
    }

    // save the node before its children so a parent always has a smaller id than the nodes under it
    private void save(Requirement requirement, Course course, RequisiteRule parent) {
        if (requirement instanceof And and) {
            RequisiteRule group = saveNode(course, parent, NodeType.AND);
            and.parts().forEach(part -> save(part, course, group));
        } else if (requirement instanceof Or or) {
            RequisiteRule group = saveNode(course, parent, NodeType.OR);
            or.parts().forEach(part -> save(part, course, group));
        } else if (requirement instanceof CourseReq required) {
            RequisiteRule node = new RequisiteRule(course, parent, RuleKind.PREREQUISITE, NodeType.COURSE);
            node.setRequiredSubjectAndNumber(required.subject(), required.number());
            node.setMinGrade(required.minGrade());
            node.setConcurrentOk(required.concurrentOk());
            // Null until the required course is imported too
            node.setRequiredCourse(courseRepository
                    .findBySubjectAndNumber(node.getReqSubject(), node.getReqNumber()).orElse(null));
            requisiteRuleRepository.save(node);
        } else if (requirement instanceof MinUnits units) {
            RequisiteRule node = new RequisiteRule(course, parent, RuleKind.PREREQUISITE, NodeType.MIN_UNITS);
            node.setMinUnits(units.units());
            requisiteRuleRepository.save(node);
        } else if (requirement instanceof Special special) {
            RequisiteRule node = new RequisiteRule(course, parent, RuleKind.PREREQUISITE, NodeType.SPECIAL);
            node.setSpecialText(special.text());
            requisiteRuleRepository.save(node);
        }
    }

    private RequisiteRule saveNode(Course course, RequisiteRule parent, NodeType type) {
        return requisiteRuleRepository.save(new RequisiteRule(course, parent, RuleKind.PREREQUISITE, type));
    }
}
