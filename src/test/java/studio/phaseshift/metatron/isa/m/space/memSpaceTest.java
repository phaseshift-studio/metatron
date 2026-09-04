/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.m.space;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.TestReport;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.furi.q.LineQTest;
import studio.phaseshift.metatron.furi.q.LockQTest;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.furi.q.SubQTest;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.DATA;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

@TestReport
public class memSpaceTest extends AbstractSpaceTest implements SubQTest, LineQTest, LockQTest {

    public memSpaceTest() {
        super(() -> {
            /*Graphitty.log(memSpaceTest.class).debug("deleting persisted memspace data at {{y}}/tmp/memspace-test.mtron{{X}}");
            final File file = new File("/tmp/memspace-test.mtron");
            if (file.exists())
                file.delete();*/
            final Space space = memSpace.of(rec(uri(PATTERN), uri("/t/#")), /*uri(PERSIST), uri("/tmp/memspace-test.mtron"),*/ f("/sys/space/mem"));
            space.addQ(QCollection.subq());
            space.addQ(QCollection.lockQ());
            return space;
        });
    }

    @Test
    public void testPersistence() {
        File file = new File("/tmp/memspace-test.mtron");
        assert !file.exists() || file.delete();
        final memSpace space = memSpace.of(rec(
                uri(DATA), uri("/tmp/memspace-test.mtron"),
                uri(PATTERN), uri("/tt/#")), f("/sys/space/mem_persist_1"));
        final Map<fURI, Obj> data = generateRandomData(space.pattern().retractPattern(), 10);
        data.forEach(Router::writeToSpace);
        data.forEach((k, v) -> assertEquals(v, Router.readFromSpace(k)));
        space.close();
        final memSpace space2 = memSpace.of(rec(
                uri(DATA), uri("/tmp/memspace-test.mtron"),
                uri(PATTERN), uri("/tt/#")), f("/sys/space/mem_persist_2"));
        data.forEach((k, v) -> assertEquals(v, Router.readFromSpace(k)));
        space2.close();
    }

    @ParameterizedTest()
    @TestData(value = {
            "/t/code -> ['a' => ['x' => 1, 'y' => 2], 'b' => 'hello']"
    })
    @CsvSource(value = {
            "*/t/code       % ['a'=>['x'=>1,'y'=>2],'b'=>'hello']",
            "*/t/code/a     % ['x'=>1,'y'=>2]",
            "*/t/code/b     % 'hello'",
            "*/t/code/a/x   % 1",
            "*/t/code/a/y   % 2",
            "*/t/code/a/+   % [1,2]",
            "*/t/code/a/0   % 1",
            "*/t/code/a/1   % 2",
            "*/t/code/0/a/x % 1",
            "*/t/code/1    % 'hello'",
            "*/t/code/+/a  % [1,2]",
    }, delimiter = '%')
    void testListElementReads(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

    @ParameterizedTest()
    @TestData(value = {
            "/dev/proj -> [name => 't', code => [[kind => 'class', sig => 'greet(String)'], [kind => 'ctor']]]"
    })
    @CsvSource(value = {
            "*/dev/proj/name   % 't'",
            "*/dev/proj/code   % [[kind=>'class',sig=>'greet(String)'], [kind=>'ctor']]",
            "*/dev/proj/code/0 % [kind=>'class',sig=>'greet(String)']",
            "*/dev/proj/code/1 % [kind=>'ctor']",
            "*/dev/proj/code/+ % [[kind=>'class',sig=>'greet(String)'], [kind=>'ctor']]",
            "*/dev/proj/code/0/kind % 'class'",
    }, delimiter = '%')
    void testProjectShapedListReads(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

    @ParameterizedTest()
    @TestData(value = {
            "/t/proj -> [name => 't', code => []]",
            "/t/proj/code/0 -> ['kind' => 'method', 'name' => 'greet']"
    })
    @CsvSource(value = {
            "*/t/proj/name   % 't'",
            "*/t/proj/code   % [['kind'=>'method','name'=>'greet']]",
            "*/t/proj/code/0 % ['kind'=>'method','name'=>'greet']",
    }, delimiter = '%')
    void testRecListFieldAppendWrite(final String code, final String expected) {
        // an element write into a rec list field that is still empty — the ide pull
        // grows its code list this way; the element must land at index == size
        // (Lst.at append) instead of throwing "lst index out of bounds: 0 > 0".
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

    @Override
    protected boolean skipBasicOperations() {
        return false;
    }
}
