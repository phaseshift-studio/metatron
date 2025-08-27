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

package studio.phaseshift.metatron.lang.parse;

import org.petitparser.context.*;
import org.petitparser.parser.*;
import org.petitparser.parser.combinators.*;
import org.slf4j.*;
import studio.phaseshift.metatron.lang.obj.SObj.*;

import java.util.*;

import static org.petitparser.parser.primitive.CharacterParser.*;
import static org.petitparser.parser.primitive.CharacterParser.of;
import static org.petitparser.parser.primitive.StringParser.of;

public class ObjParser {

    private static final Logger LOG = LoggerFactory.getLogger(ObjParser.class);


    public static void main(final String[] args) {
        System.out.println(m_bool().parse("true"));
        System.out.println(m_int().accept("20010"));
        System.out.println(m_real().accept("122.023451"));
        System.out.println(m_str().accept("'ab34dsf   3656c'"));
        System.out.println(m_obj().accept("'f   3656c'"));
    }

    private static String foldCharacters(final Object p) {
        if (p instanceof List)
            return ((List) p).stream().map(Object::toString).reduce("", (a, b) -> a.toString() + b).toString();
        else
            return p.toString();
    }

    public static Obj parse(final String code) {
        Result result = m_obj().parse(code);
        //LOG.info("{}==to==>{}", code, result.get().toString());
        return Obj.of(result.get());
    }

    public static Parser m_obj() {
        return new ChoiceParser(m_noobj(), m_bool(), m_real(), m_int(), m_str()).star().map(o -> {
            if (o instanceof List && ((List<?>) o).size() == 1)
                return ((List<?>) o).get(0);
            else if (o instanceof List) {
                return Objs.of(((List<?>) o).iterator());
            } else {
                throw new RuntimeException("wrong type: " + o);
            }

        }).end();
    }

    public static Parser m_noobj() {
        return of("noobj").map(t -> NoObj.of());
    }

    public static Parser m_bool() {
        return of("true").or(of("false")).map(t -> t.equals("true") ? Obj.of(true) : Obj.of(false));
    }

    public static Parser m_int() {
        return new SequenceParser(new OptionalParser(of('-'), '+'), new ChoiceParser(of('0'), digit().plus())).map(t -> {
            final List<?> components = (List) t;
            final int i = Integer.parseInt(foldCharacters(components.get(1)));
            return Int.of('+' == (Character) components.get(0) ? i : i * -1);
        });
    }

    public static Parser m_real() {
        return new SequenceParser(new OptionalParser(of('-'), '+'), new ChoiceParser(of('0'), digit().plus()), of('.'), digit().plus()).map(t -> {
            final List<?> components = (List) t;
            final double r = Double.parseDouble(foldCharacters(components.get(1)) + "." + foldCharacters(components.get(3)));
            return Real.of('+' == (Character) components.get(0) ? r : r * -1.0);
        });
    }

    public static Parser m_str() {
        return of('\'').seq(any().starLazy(of('\'')), of('\'')).map(a -> Str.of(foldCharacters(((List) a).get(1))));
    }

    public static Parser m_lst() {
        return of('.');
    }
}
