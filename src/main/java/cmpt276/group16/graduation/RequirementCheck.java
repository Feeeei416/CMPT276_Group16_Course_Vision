package cmpt276.group16.graduation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import cmpt276.group16.graduation.GraduationReport.Category;
import cmpt276.group16.graduation.GraduationReport.Result;
import cmpt276.group16.graduation.GraduationReport.Status;
import cmpt276.group16.graduation.GraduationReport.Units;
import cmpt276.group16.graduation.GraduationRequirements.RequirementWithCourses;
import cmpt276.group16.graduation.GraduationRequirements.StudentRequirements;
import cmpt276.group16.graduation.StudentCourse.State;
import cmpt276.group16.models.Grades;
import cmpt276.group16.models.GraduationRequirement;
import cmpt276.group16.models.RequirementCourse;
import cmpt276.group16.models.RequirementKind;
import cmpt276.group16.services.UnitText;

// Checks a student's courses against their graduation requirements (#69).
// It doesn't touch the database: GraduationReportService loads the courses and requirements, so this can be tested
// on its own.
//
// How each requirement is checked:
//  - Only courses that can count are used: the right course or range, a grade that meets the requirement's minimum,
//    and never a co-op work term, unless the rule names it (like the co-op requirement does).
//  - Completed courses are used first, then courses in progress, then planned ones. The status is what the
//    requirement still depends on: Completed if completed courses are enough, In progress if it also needs a course
//    being taken now, Planned if it needs a planned course, and Remaining if even the planned ones aren't enough.
//  - Requirements are checked in Calendar order. A course used by one "exclusive" rule (Breadth, Depth and the BSc
//    tables, which each say "additional") isn't used again by a later exclusive rule. Required courses, WQB and unit
//    totals can share courses with anything, as docs/graduation-requirements.md says.
public final class RequirementCheck {

    // A choice with more options than this doesn't list them, like the BSc's 58 table courses
    static final int MAX_OPTIONS_SHOWN = 8;

    // A WQB note names at most this many courses, then says how many more
    static final int MAX_UNKNOWN_SHOWN = 5;

    // Completed first, then in progress, then planned. Lower numbers first within each, so Breadth, which comes
    // before Depth, uses 300-level courses and leaves the 400-level ones for Depth
    private static final Comparator<StudentCourse> BEST_FIRST = Comparator
            .comparing(StudentCourse::state)
            .thenComparingInt(StudentCourse::numberValue)
            .thenComparing(StudentCourse::code);

    // The courses picked for one requirement, best first, and whether they're enough.
    // note: set when the rule isn't met but might be, because of courses the app has no data for
    private record Pick(List<StudentCourse> courses, boolean met, String progress, String note) {

        Pick(List<StudentCourse> courses, boolean met, String progress) {
            this(courses, met, progress, null);
        }
    }

    private RequirementCheck() {
    }

    public static GraduationReport report(StudentRequirements requirements, List<StudentCourse> courses) {
        List<StudentCourse> sorted = courses.stream().sorted(BEST_FIRST).toList();
        // Codes already used by an exclusive rule
        Set<String> usedByExclusive = new HashSet<>();

        Map<String, List<Result>> byCategory = new LinkedHashMap<>();
        for (RequirementWithCourses item : requirements.requirements()) {
            GraduationRequirement requirement = item.requirement();
            List<StudentCourse> available = requirement.isExclusive()
                    ? sorted.stream().filter(course -> !usedByExclusive.contains(course.code())).toList()
                    : sorted;

            Result result = check(item, available);
            if (requirement.isExclusive()) {
                result.courses().forEach(course -> usedByExclusive.add(course.code()));
            }
            byCategory.computeIfAbsent(requirement.getCategory(), name -> new ArrayList<>()).add(result);
        }

        List<Category> categories = byCategory.entrySet().stream()
                .map(entry -> new Category(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        return new GraduationReport(requirements.set(), categories, units(requirements, sorted));
    }

    private static Result check(RequirementWithCourses item, List<StudentCourse> available) {
        GraduationRequirement requirement = item.requirement();
        List<RequirementCourse> named = item.courses();

        if (requirement.getKind() == RequirementKind.NOTE) {
            return new Result(requirement, requirement.getText(), Status.CHECK, null, null, null, List.of());
        }
        if (requirement.getKind() == RequirementKind.TOTAL_UNITS) {
            return totalUnits(requirement, available);
        }

        Pick pick = switch (requirement.getKind()) {
            case COURSE, CHOOSE -> choose(requirement, named, available);
            case UNITS_FROM -> unitsFrom(requirement, named, available);
            case AREAS -> areas(requirement, named, available);
            case DESIGNATION -> designation(requirement, available);
            default -> throw new IllegalStateException("Unknown kind " + requirement.getKind());
        };
        // A rule that isn't met but might be, once someone checks the courses the app has no data for
        Status status = pick.note() != null ? Status.CHECK : status(pick);
        return new Result(requirement, name(requirement, named), status, pick.progress(),
                options(requirement, named, status), pick.note(), pick.courses());
    }

    // COURSE: the one course. CHOOSE: courseCount of the listed courses
    private static Pick choose(GraduationRequirement requirement, List<RequirementCourse> named,
                               List<StudentCourse> available) {
        int needed = requirement.getKind() == RequirementKind.COURSE ? 1 : count(requirement);
        Set<String> codes = codes(named, false);
        List<StudentCourse> picked = available.stream()
                .filter(course -> codes.contains(course.code()))
                .filter(course -> meetsMinimum(course, requirement.getMinGrade()))
                .limit(needed)
                .toList();
        // "1 of 2 courses" only means something when more than one is needed
        String progress = needed > 1 ? picked.size() + " of " + needed + " courses" : null;
        return new Pick(picked, picked.size() >= needed, progress);
    }

    // Units from the listed courses and/or a range like CMPT 400 and above, leaving out excluded courses,
    // with at most maxCoursesBelow courses numbered below belowNumber
    private static Pick unitsFrom(GraduationRequirement requirement, List<RequirementCourse> named,
                                  List<StudentCourse> available) {
        Set<String> listed = codes(named, false);
        Set<String> excluded = named.stream()
                .filter(course -> course.getUse() == RequirementCourse.Use.EXCLUDED)
                .map(RequirementCourse::getCode)
                .collect(Collectors.toSet());

        List<StudentCourse> picked = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int below = 0;
        for (StudentCourse course : available) {
            if (total.compareTo(requirement.getUnits()) >= 0) {
                break;
            }
            boolean matches = listed.contains(course.code()) || inRange(requirement, course);
            if (!matches || excluded.contains(course.code()) || course.isCoop()
                    || !meetsMinimum(course, requirement.getMinGrade())) {
                continue;
            }
            if (requirement.getMaxCoursesBelow() != null && course.numberValue() < requirement.getBelowNumber()) {
                if (below >= requirement.getMaxCoursesBelow()) {
                    continue;
                }
                below++;
            }
            picked.add(course);
            total = total.add(course.units());
        }
        return unitPick(picked, total, requirement.getUnits());
    }

    // courseCount courses, each from a different area. An area with required courses only counts when one of
    // them is taken, and it has to be (CMPT 307 in Breadth, or CMPT 300 or 303 for 2023)
    private static Pick areas(GraduationRequirement requirement, List<RequirementCourse> named,
                              List<StudentCourse> available) {
        Map<String, List<RequirementCourse>> byArea = new LinkedHashMap<>();
        for (RequirementCourse course : named) {
            if (course.getUse() != RequirementCourse.Use.EXCLUDED && course.getArea() != null) {
                byArea.computeIfAbsent(course.getArea(), area -> new ArrayList<>()).add(course);
            }
        }

        List<StudentCourse> picked = new ArrayList<>();
        List<String> missingRequired = new ArrayList<>();
        // The best course in each area that doesn't have required courses
        List<StudentCourse> others = new ArrayList<>();
        for (List<RequirementCourse> inArea : byArea.values()) {
            List<RequirementCourse> required = inArea.stream()
                    .filter(course -> course.getUse() == RequirementCourse.Use.REQUIRED)
                    .toList();
            Set<String> allowed = (required.isEmpty() ? inArea : required).stream()
                    .map(RequirementCourse::getCode)
                    .collect(Collectors.toSet());
            StudentCourse best = available.stream()
                    .filter(course -> allowed.contains(course.code()) && !course.isCoop())
                    .filter(course -> meetsMinimum(course, requirement.getMinGrade()))
                    .findFirst()
                    .orElse(null);
            if (!required.isEmpty()) {
                if (best == null) {
                    missingRequired.add(required.stream().map(RequirementCourse::getCode)
                            .collect(Collectors.joining(" or ")));
                } else {
                    picked.add(best);
                }
            } else if (best != null) {
                others.add(best);
            }
        }

        // An area whose required course is missing still takes one of the places, so it isn't filled by another area
        int needed = count(requirement);
        int places = needed - missingRequired.size();
        others.sort(BEST_FIRST);
        for (StudentCourse course : others) {
            if (picked.size() >= places) {
                break;
            }
            picked.add(course);
        }
        picked.sort(BEST_FIRST);

        String progress = picked.size() + " of " + needed + " areas";
        if (!missingRequired.isEmpty()) {
            progress += ", still needs " + String.join(" and ", missingRequired);
        }
        return new Pick(picked, missingRequired.isEmpty() && picked.size() >= needed, progress);
    }

    // Units of courses with one of the designations, like 6 units of W. outsideSubject leaves out the major,
    // since Breadth courses have to be outside it.
    // The app only has SFU's data for CMPT, ENSC, MACM and MATH, so a HIST or PSYC course might have a designation
    // it doesn't know about. When the rule isn't met and the student has courses like that, it's left for an advisor
    // to check, with those courses named, instead of showing as Remaining
    private static Pick designation(GraduationRequirement requirement, List<StudentCourse> available) {
        List<StudentCourse> picked = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (StudentCourse course : available) {
            boolean outside = requirement.getOutsideSubject() == null
                    || !requirement.getOutsideSubject().equals(course.subject());
            if (!outside || course.isCoop() || !meetsMinimum(course, requirement.getMinGrade())) {
                continue;
            }
            boolean hasDesignation = !Collections.disjoint(course.designations(), requirement.getDesignations());
            if (hasDesignation && total.compareTo(requirement.getUnits()) < 0) {
                picked.add(course);
                total = total.add(course.units());
            } else if (!hasDesignation && !course.designationsKnown()) {
                unknown.add(course.code());
            }
        }

        Pick pick = unitPick(picked, total, requirement.getUnits());
        if (pick.met() || unknown.isEmpty()) {
            return pick;
        }
        return new Pick(pick.courses(), false, pick.progress(), unknownNote(unknown));
    }

    // For example "No WQB data for HIST 101 and PSYC 102, so check whether they count."
    private static String unknownNote(List<String> codes) {
        String list;
        if (codes.size() == 1) {
            list = codes.get(0);
        } else if (codes.size() <= MAX_UNKNOWN_SHOWN) {
            list = String.join(", ", codes.subList(0, codes.size() - 1)) + " and " + codes.get(codes.size() - 1);
        } else {
            list = String.join(", ", codes.subList(0, MAX_UNKNOWN_SHOWN)) + " and "
                    + (codes.size() - MAX_UNKNOWN_SHOWN) + " more";
        }
        return "No WQB data for " + list + ", so check whether " + (codes.size() == 1 ? "it counts." : "they count.");
    }

    // Units overall, or of courses numbered minNumber and above (upper division). Every course counts, so they
    // aren't listed; the progress says how many units are done, in progress and planned
    private static Result totalUnits(GraduationRequirement requirement, List<StudentCourse> available) {
        BigDecimal completed = BigDecimal.ZERO;
        BigDecimal inProgress = BigDecimal.ZERO;
        BigDecimal planned = BigDecimal.ZERO;
        for (StudentCourse course : available) {
            if (course.isCoop() || (requirement.getMinNumber() != null
                    && course.numberValue() < requirement.getMinNumber())) {
                continue;
            }
            switch (course.state()) {
                case COMPLETED -> completed = completed.add(course.units());
                case IN_PROGRESS -> inProgress = inProgress.add(course.units());
                case PLANNED -> planned = planned.add(course.units());
            }
        }

        BigDecimal target = requirement.getUnits();
        Status status;
        if (completed.compareTo(target) >= 0) {
            status = Status.COMPLETED;
        } else if (completed.add(inProgress).compareTo(target) >= 0) {
            status = Status.IN_PROGRESS;
        } else if (completed.add(inProgress).add(planned).compareTo(target) >= 0) {
            status = Status.PLANNED;
        } else {
            status = Status.REMAINING;
        }

        // For example "57 of 120 units completed, 15 in progress, 30 planned"
        String progress = UnitText.number(completed) + " of " + UnitText.units(target) + " completed";
        if (inProgress.signum() > 0) {
            progress += ", " + UnitText.number(inProgress) + " in progress";
        }
        if (planned.signum() > 0) {
            progress += ", " + UnitText.number(planned) + " planned";
        }
        String name = requirement.getTitle() != null ? requirement.getTitle() : "Total units";
        return new Result(requirement, name, status, progress, null, null, List.of());
    }

    // The program's units, leaving out co-op work terms. The total needed is the set's TOTAL_UNITS rule that
    // isn't limited to upper division
    private static Units units(StudentRequirements requirements, List<StudentCourse> courses) {
        BigDecimal completed = BigDecimal.ZERO;
        BigDecimal inProgress = BigDecimal.ZERO;
        BigDecimal planned = BigDecimal.ZERO;
        for (StudentCourse course : courses) {
            if (course.isCoop()) {
                continue;
            }
            switch (course.state()) {
                case COMPLETED -> completed = completed.add(course.units());
                case IN_PROGRESS -> inProgress = inProgress.add(course.units());
                case PLANNED -> planned = planned.add(course.units());
            }
        }
        BigDecimal required = requirements.requirements().stream()
                .map(RequirementWithCourses::requirement)
                .filter(rule -> rule.getKind() == RequirementKind.TOTAL_UNITS && rule.getMinNumber() == null)
                .map(GraduationRequirement::getUnits)
                .findFirst()
                .orElse(null);
        return new Units(completed, inProgress, planned, required);
    }

    private static Pick unitPick(List<StudentCourse> picked, BigDecimal total, BigDecimal target) {
        // For example "3 of 6 units"
        String progress = UnitText.number(total) + " of " + UnitText.units(target);
        return new Pick(picked, total.compareTo(target) >= 0, progress);
    }

    // The worst state among the courses a met requirement needs. Their order doesn't matter here
    private static Status status(Pick pick) {
        if (!pick.met()) {
            return Status.REMAINING;
        }
        State worst = pick.courses().stream()
                .map(StudentCourse::state)
                .max(Comparator.naturalOrder())
                .orElse(State.COMPLETED);
        return switch (worst) {
            case COMPLETED -> Status.COMPLETED;
            case IN_PROGRESS -> Status.IN_PROGRESS;
            case PLANNED -> Status.PLANNED;
        };
    }

    // What the student reads for a requirement: its title, or the course when it's a single course
    private static String name(GraduationRequirement requirement, List<RequirementCourse> named) {
        if (requirement.getTitle() != null) {
            return requirement.getTitle();
        }
        return switch (requirement.getKind()) {
            case COURSE -> named.isEmpty() ? "Required course" : named.get(0).getCode();
            case CHOOSE -> count(requirement) + " of " + String.join(", ", codes(named, false));
            case UNITS_FROM, DESIGNATION -> UnitText.units(requirement.getUnits());
            case AREAS -> count(requirement) + " areas";
            default -> requirement.getKind().name();
        };
    }

    // The courses to choose from, for a choice that isn't completed and isn't too long to list
    private static String options(GraduationRequirement requirement, List<RequirementCourse> named, Status status) {
        if (requirement.getKind() != RequirementKind.CHOOSE || status == Status.COMPLETED) {
            return null;
        }
        Set<String> codes = codes(named, false);
        return codes.size() > MAX_OPTIONS_SHOWN ? null : String.join(", ", codes);
    }

    // A range like "CMPT 400 and above": same subject, numbered minNumber or higher
    private static boolean inRange(GraduationRequirement requirement, StudentCourse course) {
        if (requirement.getSubject() == null || !requirement.getSubject().equals(course.subject())) {
            return false;
        }
        int lowest = requirement.getMinNumber() == null ? 0 : requirement.getMinNumber();
        return course.numberValue() >= lowest;
    }

    // Courses in progress or planned are assumed to meet it, since there's no grade yet.
    // Transfer credit meets any minimum. P, CR, CC and AE aren't letter grades, so they count too
    static boolean meetsMinimum(StudentCourse course, String minGrade) {
        if (minGrade == null || course.state() != State.COMPLETED) {
            return true;
        }
        int grade = Grades.LETTER.indexOf(course.grade());
        int minimum = Grades.LETTER.indexOf(minGrade);
        if (grade < 0 || minimum < 0) {
            return true;
        }
        // LETTER goes from best to worst, so a smaller index is a better grade
        return grade <= minimum;
    }

    // The codes a requirement lists, in saved order. With excluded false, courses it excludes are left out
    private static Set<String> codes(List<RequirementCourse> named, boolean excluded) {
        return named.stream()
                .filter(course -> excluded == (course.getUse() == RequirementCourse.Use.EXCLUDED))
                .map(RequirementCourse::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static int count(GraduationRequirement requirement) {
        return requirement.getCourseCount() == null ? 1 : requirement.getCourseCount();
    }
}
