package cmpt276.group16.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UnitTextTest {

    // Units are stored as "3.00", and shown without the trailing zeros
    @ParameterizedTest
    @CsvSource({"3.00, 3", "4, 4", "1.50, 1.5", "0.00, 0", "30.00, 30", "120.00, 120", "45.00, 45"})
    void numberDropsTrailingZeros(String stored, String shown) {
        assertThat(UnitText.number(new BigDecimal(stored))).isEqualTo(shown);
    }

    @ParameterizedTest
    @CsvSource({"3.00, 3 units", "1.00, 1 unit", "0.00, 0 units", "1.50, 1.5 units"})
    void unitsSaysUnitOrUnits(String stored, String shown) {
        assertThat(UnitText.units(new BigDecimal(stored))).isEqualTo(shown);
    }

    @Test
    void noUnitsIsBlank() {
        assertThat(UnitText.number(null)).isEmpty();
    }
}
