package cmpt276.group16.graduation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cmpt276.group16.models.Requirement;
import cmpt276.group16.models.RequirementCourse;
import cmpt276.group16.models.RequirementCourseRepository;
import cmpt276.group16.models.RequirementRepository;
import cmpt276.group16.models.RequirementSet;
import cmpt276.group16.models.RequirementSetRepository;
import cmpt276.group16.models.StudentProfile;

// Finds the graduation requirements a student follows: the set for their program and entry year,
// with each requirement's courses. The Graduation Report (#69) compares the student's courses against this
@Service
public class GraduationRequirements {

    // One requirement and the courses it names, in the order they were saved
    public record RequirementWithCourses(Requirement requirement, List<RequirementCourse> courses) {
    }

    // A student's whole set, with requirements in the order they're shown
    public record StudentRequirements(RequirementSet set, List<RequirementWithCourses> requirements) {

        // The requirements under each Calendar heading, headings in the order they first appear,
        // so a page can show "Lower division", then "Upper division", and so on
        public Map<String, List<RequirementWithCourses>> byCategory() {
            Map<String, List<RequirementWithCourses>> groups = new LinkedHashMap<>();
            for (RequirementWithCourses item : requirements) {
                groups.computeIfAbsent(item.requirement().getCategory(), category -> new ArrayList<>()).add(item);
            }
            return groups;
        }
    }

    private final RequirementSetRepository setRepository;
    private final RequirementRepository requirementRepository;
    private final RequirementCourseRepository courseRepository;

    public GraduationRequirements(RequirementSetRepository setRepository, RequirementRepository requirementRepository,
                                  RequirementCourseRepository courseRepository) {
        this.setRepository = setRepository;
        this.requirementRepository = requirementRepository;
        this.courseRepository = courseRepository;
    }

    // Empty when the student's program and year have no requirements yet, so the report can say so.
    // Read-only, so nothing the report does to these objects is saved
    @Transactional(readOnly = true)
    public Optional<StudentRequirements> forStudent(StudentProfile student) {
        return setRepository.findByProgramAndEntryYear(student.getProgram(), student.getCohortYear())
                .map(this::withRequirements);
    }

    // Two queries for the whole set, one for the requirements and one for all their courses,
    // instead of one query per requirement
    private StudentRequirements withRequirements(RequirementSet set) {
        Map<Long, List<RequirementCourse>> coursesByRequirement = new HashMap<>();
        for (RequirementCourse course : courseRepository.findByRequirementRequirementSetOrderByIdAsc(set)) {
            coursesByRequirement.computeIfAbsent(course.getRequirement().getId(), id -> new ArrayList<>()).add(course);
        }
        List<RequirementWithCourses> requirements = requirementRepository.findByRequirementSetOrderByPositionAsc(set)
                .stream()
                .map(requirement -> new RequirementWithCourses(requirement,
                        List.copyOf(coursesByRequirement.getOrDefault(requirement.getId(), List.of()))))
                .toList();
        return new StudentRequirements(set, requirements);
    }
}
