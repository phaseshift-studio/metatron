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
import studio.phaseshift.metatron.furi.q.DecQTest;
import studio.phaseshift.metatron.furi.q.IncrQTest;
import studio.phaseshift.metatron.furi.q.LineQTest;
import studio.phaseshift.metatron.furi.q.LockQTest;
import studio.phaseshift.metatron.furi.q.QCollection;
import studio.phaseshift.metatron.furi.q.SubQTest;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.mach.type.Machine;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.DATA;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

@TestReport
public class memSpaceTest extends AbstractSpaceTest implements SubQTest, LineQTest, LockQTest, IncrQTest, DecQTest {

    public memSpaceTest() {
        super(() -> {
            /*Graphitty.log(memSpaceTest.class).debug("deleting persisted memspace data at {{y}}/tmp/memspace-test.mtron{{X}}");
            final File file = new File("/tmp/memspace-test.mtron");
            if (file.exists())
                file.delete();*/
            final Space space = memSpace.of(rec(uri(PATTERN), uri("/t/#")), /*uri(PERSIST), uri("/tmp/memspace-test.mtron"),*/ f("/sys/space/mem"));
            space.addQ(QCollection.subQ());
            space.addQ(QCollection.lockQ());
            return space;
        });
    }

    @Override
    public fURI incrQBaseURI() {
        return f(this.baseURI.toString() + "/incrq");
    }

    @Override
    public fURI decqBaseURI() {
        return f(this.baseURI.toString() + "/decq");
    }

    @Test
    public void testPersistence() {
        File file = new File("/tmp/memspace-test.mtron");
        assert !file.exists() || file.delete();
        final memSpace space = memSpace.of(rec(
                uri(DATA), uri("/tmp/memspace-test.mtron"),
                uri(PATTERN), uri("/tt/#")), f("/sys/space/mem_persist_1"));
        final Map<fURI, Obj> data = generateRandomData(space.pattern().retractPattern(), 10);
        data.forEach(Machine::write);
        data.forEach((k, v) -> assertEquals(v, Machine.read(k)));
        space.close();
        final memSpace space2 = memSpace.of(rec(
                uri(DATA), uri("/tmp/memspace-test.mtron"),
                uri(PATTERN), uri("/tt/#")), f("/sys/space/mem_persist_2"));
        data.forEach((k, v) -> assertEquals(v, Machine.read(k)));
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

    @ParameterizedTest()
    @TestData(value = {
            // A BRANCH URI IS A SUM OF ADDRESSES (Marko, 2026-11-10), n uris read and written in parallel (2026-11-13):
            // the door linearizes it, and its write writes each term
            "/t/br/{a,b}/c -> 23"
    })
    @CsvSource(value = {
            "*/t/br/{a,b}/c                        % {2}23",
            // two branched segments are the product of their terms, four addresses, two of them written
            "*/t/br/{a,b}/{c,d}                    % {2}23",
            "/t/br/{a,b}/{c,d} -> 1; */t/br/{a,b}/{c,d}  % {4}1",
            "*/t/br/a/c                            % 23",
            "*/t/br/b/c                            % 23",
            "*/t/br/+/c                            % {2}23",
            // a branch that holds nothing adds nothing, and a branch may span segments
            "*/t/br/{a,b,q}/c                      % {2}23",
            "*/t/br/{a/x,b}/c                      % 23",
            // a sum, not a union: the wildcard and the a branch both read /t/br/a/c
            "*/t/br/{+,a}/c                        % {3}23",
            // the branches are separate addresses: rewriting or deleting one leaves the other
            "/t/br/a/c -> 7; */t/br/{a,b}/c        % {7,23}",
            "/t/br/a/c -> noobj; */t/br/{a,b}/c    % 23",
            // the write answers its obj, as any write does (x -> y is y), however many terms it wrote
            "/t/br/{x,y}/c -> 5                    % 5",
            "/t/br/{x,y}/c -> noobj                % noobj",
    }, delimiter = '%')
    void testBranchReadWrite(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

    @ParameterizedTest()
    @TestData(value = {
            // branches in different spaces are written to, and read from, each their own space
            "memspace::[pattern=>/u/#]@/sys/space/mem_u",
            "/{t,u}/br -> 23"
    }, oneTime = true)
    @CsvSource(value = {
            "*/t/br                  % 23",
            "*/u/br                  % 23",
            "*/{t,u}/br              % {2}23",
            "*/{t,u,v}/br            % {2}23",
    }, delimiter = '%')
    void testBranchesAcrossSpaces(final String code, final String expected) {
        AbstractMetatronTest.checkCodeEvaluate(LOG, code, expected);
    }

    @Override
    protected boolean skipBasicOperations() {
        return false;
    }
}
