package studio.phaseshift.metatron.lang.mtron.type;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.MetatronTest;
import studio.phaseshift.metatron.lang.mtron.mtronParser;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NoObjTest extends MetatronTest {

    private static final GraphittyLogger LOG = Graphitty.log(TypeTest.class);

    @ParameterizedTest
    @CsvSource(value = {
            "noobj|noobj|true",
            "noobj|10|false",
            "noobj|int{0}::10|true",
            "noobj{2}|noobj{1233}|true",
            "noobj{3}|noobj|true",
            "noobj{4}|str{4}::'meta'|false",
            "noobj{4}|str{0}::'tron'|true",
            "str{4}::'meta'|str{0}::'tron'|false",
            "'meta'|'meta'|true",
            "'meta'|str{0}::'meta'|false"},
            delimiter = '|')
    public void testNoObj(final String o1, final String o2, final boolean match) {
        final Obj obj1 = mtronParser.m_obj().parse(o1).get();
        final Obj obj2 = mtronParser.m_obj().parse(o2).get();
        LOG.trace("testing %s %s %s", obj1, match ? "{{g}}equals{{/g}}" : "{{r}}not equals{{/r}}", obj2);
        if (match) {
            assertEquals(obj1, obj2);
            assertEquals(obj2, obj1);
        } else {
            assertNotEquals(obj1, obj2);
            assertNotEquals(obj2, obj1);
        }
    }
}
