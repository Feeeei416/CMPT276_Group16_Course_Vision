package cmpt276.group16.graduation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import cmpt276.group16.models.GraduationRequirementRepository;
import cmpt276.group16.models.Program;
import cmpt276.group16.models.RequirementSet;
import cmpt276.group16.models.RequirementSetRepository;

// Starts the whole app, the same as running it, and checks the real requirements were saved
@SpringBootTest
class RequirementLoaderStartupTest {

    @Autowired
    private RequirementSetRepository setRepository;

    @Autowired
    private GraduationRequirementRepository requirementRepository;

    @Test
    void everySetIsSavedAtStartup() {
        assertThat(setRepository.count()).isEqualTo(4);
        for (Program program : Program.values()) {
            for (int year : new int[] {2023, 2024}) {
                assertThat(setRepository.existsByProgramAndEntryYear(program, year)).as(program + " " + year).isTrue();
            }
        }
    }

    @Test
    void savedSetHasItsRequirements() {
        RequirementSet cs = setRepository.findByProgramAndEntryYear(Program.COMPUTING_SCIENCE, 2024).orElseThrow();

        assertThat(requirementRepository.findByRequirementSetOrderByPositionAscIdAsc(cs)).isNotEmpty();
    }
}
