package cmpt276.group16.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TermKindTest {

    @Test
    void eachKindHasADisplayName() {
        assertThat(TermKind.STUDY.getShowName()).isEqualTo("Study");
        assertThat(TermKind.COOP.getShowName()).isEqualTo("Co-op");
        assertThat(TermKind.GAP.getShowName()).isEqualTo("Gap");
    }

    @Test
    void onlyStudyTermsTakeCourses() {
        assertThat(TermKind.STUDY.takesCourses()).isTrue();
        assertThat(TermKind.COOP.takesCourses()).isFalse();
        assertThat(TermKind.GAP.takesCourses()).isFalse();
    }
}
