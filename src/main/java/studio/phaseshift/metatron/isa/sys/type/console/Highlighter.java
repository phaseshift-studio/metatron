/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 *  Copyright (C) 2025- PhaseShift Studio, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package studio.phaseshift.metatron.isa.sys.type.console;

import org.jline.builtins.ConfigurationPath;
import org.jline.builtins.SyntaxHighlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import studio.phaseshift.metatron.io.serial.ObjCleanStringSerializer;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.sys.type.ui.graphitty.Graphitty;

import java.io.ByteArrayOutputStream;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Highlighter implements org.jline.reader.Highlighter {

    private final SyntaxHighlighter syntaxHighlighter;
    private final Pattern GRAPHITTY_PATTERN = Pattern.compile("\\{\\{.*?}}");
    private final Graphitty graphitty;
    private final static ConfigurationPath configurations = new ConfigurationPath(
            Paths.get("conf"),                                     // application-wide settings
            Paths.get(System.getProperty("user.home"), ".metatron") // user-specific settings
    );
    private ObjCleanStringSerializer serializer;

    private static final Highlighter INSTANCE = new Highlighter(SyntaxHighlighter.build(Highlighter.configurations.getConfig("jnanorc"), "mtron"));

    public static Highlighter single() {
        return INSTANCE;
    }

    public static Highlighter create() {
        return new Highlighter(INSTANCE.syntaxHighlighter);
    }

    private Highlighter(final SyntaxHighlighter syntaxHighlighter) {
        this.syntaxHighlighter = syntaxHighlighter;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        this.graphitty = new Graphitty(Map.of(), out);
        this.serializer = new ObjCleanStringSerializer(true);
    }

    public Highlighter justify(final boolean leftJustify) {
        this.serializer = new ObjCleanStringSerializer(leftJustify);
        return this;
    }

    public static String format(final Object object) {
        return INSTANCE.highlight(object);
    }

    public static String format(final String f, final Object... args) {
        return INSTANCE.highlight(f.formatted(args));
    }

    public static String unformat(final String string) {
        return Graphitty.strip(string);
    }

    public static int visualLength(final String string) {
        return Highlighter.unformat(string).length();
    }

    public String highlight(final Object object) {
        try {
            if (object instanceof Obj) {
                String preprocess = this.serializer.write((Obj) object);
                if (preprocess.length() > 550 && object instanceof Rec r) {
                    preprocess = "[" + r.jvm().entrySet()
                            .stream()
                            .map(kv -> (kv.getKey().toString() + "=>" + stringClip(kv.getValue().toString(), 40, true))).reduce("\n ", (a, b) -> a + b + ",\n ");
                    preprocess = preprocess.substring(0, preprocess.length() - 2);
                    preprocess += "]";
                }

                return this.highlight(null, preprocess).toAnsi();
            } else return this.graphitty.writeToString(this.highlight(null, object.toString()).toAnsi());
        } catch (final Exception e) {
            return object.toString();
        }
    }

    @Override
    public AttributedString highlight(final LineReader reader, final String buffer) {
        final Matcher matcher = this.GRAPHITTY_PATTERN.matcher(buffer);
        if (matcher.find()) {
            return new AttributedString(this.graphitty.writeToString(buffer));
        } else {
            return this.syntaxHighlighter.highlight(buffer);
        }
    }

    public String strip(final String string) {
        return Graphitty.strip(AttributedString.stripAnsi(string));
    }

    private String stringClip(final String s, final int clipSize, final boolean removeNewLines) {
        if (s.length() < clipSize)
            return s;
        String ret = s.substring(0, clipSize) + "...";
        return removeNewLines ? ret.replaceAll("\n", "") : ret;

    }
}
