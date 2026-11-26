package cmpt276.group16.controllers;

import java.util.Optional;

import org.springframework.stereotype.Component;

import cmpt276.group16.models.StudentProfile;
import jakarta.servlet.http.HttpSession;

// Whose plans the My plan and All my plans pages work on (#73).
// /student/plan... is the signed-in student's own plans. /advisor/students/5/plan... is student 5's plans,
// opened by their advisor, so they can plan together in an appointment.
// Both use the same handlers and templates; basePath is where the page's links, forms and redirects point
public record PlanOwner(StudentProfile student, String basePath, boolean byAdvisor) {

    // Back to home for a student, back to the student's overview for an advisor
    public String homePath() {
        return byAdvisor ? basePath : "/student/home";
    }

    // The student's name, for the advisor's version of the pages
    public String name() {
        return student.getUser().getDisplayName();
    }

    @Component
    public static class Finder {

        private final CurrentStudent currentStudent;
        private final AssignedStudent assignedStudent;

        public Finder(CurrentStudent currentStudent, AssignedStudent assignedStudent) {
            this.currentStudent = currentStudent;
            this.assignedStudent = assignedStudent;
        }

        // studentId is null on the /student/ pages. Empty when nobody is signed in as a student there.
        // On the /advisor/ pages it throws a 403 unless the advisor is assigned to the student
        public Optional<PlanOwner> find(HttpSession session, Long studentId) {
            if (studentId == null) {
                return currentStudent.find(session).map(student -> new PlanOwner(student, "/student", false));
            }
            StudentProfile student = assignedStudent.find(session, studentId);
            return Optional.of(new PlanOwner(student, "/advisor/students/" + studentId, true));
        }
    }
}
