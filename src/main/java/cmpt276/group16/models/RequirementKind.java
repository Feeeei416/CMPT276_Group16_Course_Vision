package cmpt276.group16.models;

// The kinds of rule a program's graduation requirements are made of.
// docs/graduation-requirements.md shows which kind each Calendar rule uses
public enum RequirementKind {
    // One course that must be completed, for example CMPT 225
    COURSE,
    // courseCount courses from the listed courses, for example 1 of MATH 150, 151, 154, 157
    CHOOSE,
    // units from the listed courses and/or a range like CMPT 400 and above, leaving out the excluded courses
    UNITS_FROM,
    // courseCount courses, each from a different area. Each area with required courses must be covered by one
    // of them, so required courses in the same area are alternatives (CMPT 300 or CMPT 303)
    AREAS,
    // units of courses with any of the listed WQB designations, optionally leaving out one subject (the major)
    DESIGNATION,
    // units overall, or only of courses numbered minNumber and above
    TOTAL_UNITS,
    // A rule that can't be checked from courses, like a GPA, shown to the student as text
    NOTE
}
