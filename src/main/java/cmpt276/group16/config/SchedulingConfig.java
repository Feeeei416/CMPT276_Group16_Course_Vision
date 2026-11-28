package cmpt276.group16.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Turns on @Scheduled methods. The only one is CourseRefreshScheduler.check()
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
