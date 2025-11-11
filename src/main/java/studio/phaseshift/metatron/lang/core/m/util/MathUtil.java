package studio.phaseshift.metatron.lang.core.m.util;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class MathUtil {

    private MathUtil() {
        // 
    }

    private static final String[] FUNCTIONS = new String[]{
            "abs", "acos", "asin", "atan",
            "cbrt", "ceil", "cos", "cosh",
            "exp",
            "floor",
            "log", "log10", "log2",
            "signum", "sin", "sinh", "sqrt",
            "tan", "tanh"
    };

    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "\\b(?![0-9]+|\\b(?:" + String.join("|", FUNCTIONS) + ")\\b)([a-zA-Z_][a-zA-Z0-9_]*)\\b");

    public static Set<String> getVariables(final String equation) {
        final Matcher matcher = VARIABLE_PATTERN.matcher(equation);
        final Set<String> variables = new LinkedHashSet<>();
        while (matcher.find()) {
            variables.add(matcher.group());
        }
        return variables;
    }

}
