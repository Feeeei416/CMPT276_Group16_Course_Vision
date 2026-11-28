package cmpt276.group16.models;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledCourseRepository extends JpaRepository<ScheduledCourse, Long>{
  
    // Every course in one schedule, for showing the page
    List<ScheduledCourse> findBySchedule(NextSemesterSchedule schedule);
    
    // Is this course already in the schedule?
    boolean existsByScheduleAndOffering(NextSemesterSchedule schedule, CourseOffering offering);
    
    // Finds the course only if its in this student's schedule, for safe removal
    Optional<ScheduledCourse> findByIdAndScheduleStudent(Long id, StudentProfile student);

    // How many courses a schedule has, for the list of schedules
    long countBySchedule(NextSemesterSchedule schedule);

    // Used when a schedule is deleted, so its courses go with it
    long deleteBySchedule(NextSemesterSchedule schedule);
}
