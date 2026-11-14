package cmpt276.group16.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {
    // Classes that need today's date ask for this instead of calling LocalDate.now(), so tests can fix the date.
    // Uses SFU's time zone, because Render's servers run on UTC and the date would change 8 hours early
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("America/Vancouver"));
    }
}
