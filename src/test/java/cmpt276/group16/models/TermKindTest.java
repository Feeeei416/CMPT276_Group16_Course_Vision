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

    // A co-op term holds its work term course, so only a gap term holds nothing
    @Test
    void onlyGapTermsTakeNoCourses() {
        assertThat(TermKind.STUDY.takesCourses()).isTrue();
        assertThat(TermKind.COOP.takesCourses()).isTrue();
        assertThat(TermKind.GAP.takesCourses()).isFalse();
    }
}
