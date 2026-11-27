package cmpt276.group16.graduation;

import java.math.BigDecimal;
import java.util.Set;

import cmpt276.group16.models.Designation;
import cmpt276.group16.services.PlanRules;

// One course as the Graduation Report (#69) counts it: from the student's record, their main plan,
// or next semester's schedule. A course appears once, even if it was taken twice or is also planned.
// grade: the grade of the attempt that counts, only for COMPLETED courses.
// designations: the WQB designations the course has.
// designationsKnown: false when the app has no SFU data for the course, so it may have designations that aren't
//                    listed. The app only loads CMPT, ENSC, MACM and MATH, so this is most Breadth courses
public record StudentCourse(String subject, String number, BigDecimal units, State state, String grade,
                            Set<Designation> designations, boolean designationsKnown) {

    // A course whose designations are known, which is every course in RequirementCheckTest unless it says otherwise
    public StudentCourse(String subject, String number, BigDecimal units, State state, String grade,
                         Set<Designation> designations) {
        this(subject, number, units, state, grade, designations, true);
    }

    // Best first: a requirement uses completed courses before ones in progress, and those before planned ones
    public enum State {
        COMPLETED,
        IN_PROGRESS,
        PLANNED
    }

    // For example "CMPT 225"
    public String code() {
        return subject + " " + number;
    }

    // 225 from "225", 105 from "105W". -1 for transfer credit like "1XX", which isn't one SFU course,
    // so it never matches a range like "CMPT 400 and above"
    public int numberValue() {
        String digits = number.replaceAll("[^0-9].*$", "");
        return digits.isEmpty() || number.contains("X") ? -1 : Integer.parseInt(digits);
    }

    // Co-op work terms count only toward the co-op requirement, never toward units or electives
    public boolean isCoop() {
        return PlanRules.isCoopCourse(subject, number);
    }

    // For the report, for example "CMPT 225 (A)", "CMPT 276 (in progress)" or "CMPT 300 (planned)"
    public String label() {
        return switch (state) {
            case COMPLETED -> code() + " (" + grade + ")";
            case IN_PROGRESS -> code() + " (in progress)";
            case PLANNED -> code() + " (planned)";
        };
    }
}
