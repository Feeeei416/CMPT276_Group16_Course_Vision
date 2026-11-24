package cmpt276.group16.graduation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.graduation.RequirementFile.CourseEntry;
import cmpt276.group16.graduation.RequirementFile.RuleEntry;
import cmpt276.group16.graduation.RequirementFile.SetEntry;
import cmpt276.group16.models.GraduationRequirement;
import cmpt276.group16.models.GraduationRequirementRepository;
import cmpt276.group16.models.RequirementCourse;
import cmpt276.group16.models.RequirementCourseRepository;
import cmpt276.group16.models.RequirementSet;
import cmpt276.group16.models.RequirementSetRepository;

// Runs on startup and brings the saved requirement sets up to date with the requirements file:
// - a set that isn't saved yet is added
// - a saved set whose "version" in the file has gone up is replaced, so a fix to the file reaches Render
// - any other saved set is left alone, even if the file has changed without a new version
// Once IT can edit sets in the app (#70), a set edited there should be left alone too, so the edits aren't lost
@Component
public class RequirementLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RequirementLoader.class);

    // What one run of the loader did, by set name, for example "Computing Science 2024"
    public record LoadResult(List<String> added, List<String> replaced) {
    }

    private final RequirementFile file;
    private final RequirementSetRepository setRepository;
    private final GraduationRequirementRepository requirementRepository;
    private final RequirementCourseRepository courseRepository;

    public RequirementLoader(RequirementFile file, RequirementSetRepository setRepository,
                             GraduationRequirementRepository requirementRepository,
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
        LoadResult result = load();
        if (!result.added().isEmpty()) {
            log.info("Added graduation requirements for {}", String.join(", ", result.added()));
        }
        if (!result.replaced().isEmpty()) {
            log.info("Replaced graduation requirements with a newer version for {}",
                    String.join(", ", result.replaced()));
        }
    }

    public LoadResult load() {
        List<String> added = new ArrayList<>();
        List<String> replaced = new ArrayList<>();
        for (SetEntry entry : file.sets()) {
            Optional<RequirementSet> saved = setRepository.findByProgramAndEntryYear(entry.program(), entry.entryYear());
            if (saved.isEmpty()) {
                saveRequirements(setRepository.save(
                        new RequirementSet(entry.program(), entry.entryYear(), entry.calendarUrl())), entry);
                added.add(entry.name());
            } else if (saved.get().getFileVersion() < entry.version()) {
                replace(saved.get(), entry);
                replaced.add(entry.name());
            }
        }
        return new LoadResult(added, replaced);
    }

    // Keeps the set's row, so anything that points at it stays valid, and swaps everything inside it.
    // Courses go first, since each one points at its requirement. Both are deleted one at a time rather than
    // in a batch, so Hibernate knows they're gone (a batch delete would leave loaded courses pointing at deleted
    // requirements) and each requirement's designations go with it
    private void replace(RequirementSet set, SetEntry entry) {
        courseRepository.deleteAll(courseRepository.findByRequirementRequirementSetOrderByIdAsc(set));
        requirementRepository.deleteAll(requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(set));
        requirementRepository.flush();
        set.setCalendarUrl(entry.calendarUrl());
        saveRequirements(set, entry);
    }

    private void saveRequirements(RequirementSet set, SetEntry entry) {
        set.setFileVersion(entry.version());
        // Positions follow the file, so requirements are shown in the same order they're written
        int position = 0;
        for (RuleEntry rule : entry.requirements()) {
            GraduationRequirement requirement = new GraduationRequirement(set, position++, rule.category(), rule.kind());
            requirement.setTitle(rule.title());
            requirement.setCourseCount(rule.count());
            requirement.setUnits(rule.units());
            requirement.setSubject(rule.subject());
            requirement.setMinNumber(rule.minNumber());
            requirement.setLimitBelow(rule.maxCoursesBelow(), rule.belowNumber());
            requirement.setDesignations(rule.designations());
            requirement.setOutsideSubject(rule.outsideSubject());
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
