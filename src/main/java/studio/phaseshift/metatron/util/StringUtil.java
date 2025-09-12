/*
 *   Metatron: A Distributed Virtual Machine
 *   Copyright (c) 2024 PhaseShift Studio, LLC
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class StringUtil {

    private StringUtil() {

    }

    public static int countLines(final String str){
        final String[] lines = str.split("\r\n|\r|\n");
        return  lines.length;
    }

    public static String replaceGroups(String s, final String leftDelim, final String rightDelim,
                                       final Function<String, String> replaceFunction) {
        String ss = s;
        int start_pos = 0;
        while (true) {
            // Find the start delimiter
            start_pos = ss.indexOf(leftDelim, start_pos);
            if (start_pos == -1) {
                break; // No more delimiters found
            }
            // Find the end delimiter
            int end_pos = ss.indexOf(rightDelim, start_pos + leftDelim.length());
            if (end_pos == -1) {
                break; // No matching end delimiter found
            }
            // Extract the substring between the delimiters
            String substring = ss.substring(start_pos + leftDelim.length(), end_pos - (start_pos + leftDelim.length()));
            // Apply the replacement function
            String replacement = replaceFunction.apply(substring);
            // Replace the substring in the original string
            ss = ss.substring(0, start_pos) + replacement + ss.substring(end_pos - start_pos + rightDelim.length());
            // Update the start position to continue scanning
            start_pos = start_pos + replacement.length();
        }
        return ss;
    }

    /**
     * Tokenises a string on a single character delimiter.
     */
    public static List<String> tokenize(String s, char delim) {
        if (s == null) return Collections.emptyList();
        return Arrays.stream(s.split(Pattern.quote(String.valueOf(delim))))
                .map(String::trim)
                .filter(x -> !x.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Returns true if {@code s} contains only decimal digits.
     */
    public static boolean isInteger(String s) {
        return s != null && s.matches("\\d+");
    }

    /**
     * Compares two string pointers for equality (handles null).
     */
    public static boolean charPtrEqual(String a, String b) {
        return Objects.equals(a, b);
    }
}
