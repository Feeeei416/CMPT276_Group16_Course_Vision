package cmpt276.group16.services;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

// How units are written everywhere in the app: "3", not "3.00" as they're stored.
// A bean as well as static methods, so templates can call it too: ${@unitText.number(course.units)}
@Component("unitText")
public class UnitText {

    // "3.00" -> "3", "1.50" -> "1.5", "0.00" -> "0". Empty for no units at all
    public static String number(BigDecimal units) {
        if (units == null) {
            return "";
        }
        // stripTrailingZeros turns 0.00 into 0 but 30.00 into 3E+1, so toPlainString puts it back to "30"
        return units.stripTrailingZeros().toPlainString();
    }

    // "3 units", "1 unit", "0 units"
    public static String units(BigDecimal units) {
        String number = number(units);
        return number + (units != null && units.compareTo(BigDecimal.ONE) == 0 ? " unit" : " units");
    }
}
