package cmpt276.group16.graduation;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.graduation.RequirementFile.CourseEntry;
import cmpt276.group16.graduation.RequirementFile.RuleEntry;
import cmpt276.group16.graduation.RequirementFile.SetEntry;
import cmpt276.group16.models.Requirement;
import cmpt276.group16.models.RequirementCourse;
import cmpt276.group16.models.RequirementCourseRepository;
import cmpt276.group16.models.RequirementRepository;
import cmpt276.group16.models.RequirementSet;
import cmpt276.group16.models.RequirementSetRepository;

// Runs on startup and saves each set in the requirements file that isn't in the database yet.
// A set that's already saved is left alone, even if the file has changed, so changes made in the app
// (#70) are never overwritten. To load a changed set from the file, delete the saved one first
@Component
public class RequirementLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RequirementLoader.class);

    private final RequirementFile file;
    private final RequirementSetRepository setRepository;
    private final RequirementRepository requirementRepository;
    private final RequirementCourseRepository courseRepository;

    public RequirementLoader(RequirementFile file, RequirementSetRepository setRepository,
                             RequirementRepository requirementRepository,
                             RequirementCourseRepository courseRepository) {
        this.file = file;
        this.setRepository = setRepository;
        this.requirementRepository = requirementRepository;
        this.courseRepository = courseRepository;
    }

    // One transaction, so if saving fails partway, nothing from the file is saved
    @Override
    @Transactional
    public void run(String... args) {
        List<String> added = loadMissingSets();
        if (!added.isEmpty()) {
            log.info("Added graduation requirements for {}", String.join(", ", added));
        }
    }

    // Returns the names of the sets it added, for example "Computing Science 2024"
    public List<String> loadMissingSets() {
        List<String> added = new ArrayList<>();
        for (SetEntry entry : file.sets()) {
            if (setRepository.existsByProgramAndEntryYear(entry.program(), entry.entryYear())) {
                continue;
            }
            save(entry);
            added.add(entry.name());
        }
        return added;
    }

    private void save(SetEntry entry) {
        RequirementSet set = setRepository.save(
                new RequirementSet(entry.program(), entry.entryYear(), entry.calendarUrl()));
        // Positions follow the file, so requirements are shown in the same order they're written
        int position = 0;
        for (RuleEntry rule : entry.requirements()) {
            Requirement requirement = new Requirement(set, position++, rule.category(), rule.kind());
            requirement.setTitle(rule.title());
            requirement.setCourseCount(rule.count());
            requirement.setUnits(rule.units());
            requirement.setSubject(rule.subject());
            requirement.setMinNumber(rule.minNumber());
            requirement.setLimitBelow(rule.maxCoursesBelow(), rule.belowNumber());
            requirement.setDesignations(rule.designations());
            requirement.setMinGrade(rule.minGrade());
            requirement.setExclusive(rule.exclusive());
            requirement.setText(rule.text());
            requirementRepository.save(requirement);
            for (CourseEntry course : rule.courses()) {
                RequirementCourse saved = new RequirementCourse(requirement, course.subject(), course.number(),
                        course.use());
                saved.setArea(course.area());
                courseRepository.save(saved);
            }
        }
    }
}
