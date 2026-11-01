package be.dontletit.draft.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The mana value of a mana cost such as "{3}{G}", "{X}{R}" or "{2/W}{2/W}": a generic number
 * counts its value, X counts 0, any other symbol (coloured, colourless, snow, hybrid, phyrexian)
 * counts 1, and a two-generic hybrid such as {2/W} counts 2. No cost (lands) is 0.
 */
public final class ManaValue {

    private static final Pattern SYMBOL = Pattern.compile("\\{([^}]+)}");

    private ManaValue() {}

    public static int of(String manaCost) {
        if (manaCost == null) return 0;
        int total = 0;
        Matcher matcher = SYMBOL.matcher(manaCost);
        while (matcher.find()) total += symbolValue(matcher.group(1));
        return total;
    }

    private static int symbolValue(String symbol) {
        String[] parts = symbol.split("/");
        for (String part : parts) {
            if (part.chars().allMatch(Character::isDigit) && !part.isEmpty()) return Integer.parseInt(part);
        }
        for (String part : parts) {
            if (!part.equalsIgnoreCase("X") && !part.equalsIgnoreCase("Y") && !part.equalsIgnoreCase("Z")) return 1;
        }
        return 0;
    }
}
