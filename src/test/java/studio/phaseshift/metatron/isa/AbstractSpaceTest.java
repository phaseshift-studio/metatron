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

package studio.phaseshift.metatron.isa;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.AbstractMetatronTest;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.CommonUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.f;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public abstract class AbstractSpaceTest extends AbstractMetatronTest {

    protected int sleepBetweenReads = 0;
    protected Space space;
    protected final Supplier<Space> spaceSupplier;
    protected Space spaceStorage = null;
    protected static final List<String> PREVIOUS_LINE = new ArrayList<>(List.of("", "", ""));
    protected final fURI baseURI;

    public AbstractSpaceTest(final Supplier<Space> spaceSupplier) {
        this(f("/t"), spaceSupplier);
    }

    public AbstractSpaceTest(final fURI baseURI, final Supplier<Space> spaceSupplier) {
        super();
        this.baseURI = baseURI;
        this.spaceSupplier = spaceSupplier;
    }

    @BeforeEach
    protected void setup() {
        if (!Router.global().hasSpaceFor(this.baseURI))
            this.spaceStorage = memSpace.of(rec(uri(PATTERN), uri(this.baseURI.retract().extend("#"))), f("/sys/space").extend(this.baseURI.retractPattern().name()));
        this.space = this.spaceSupplier.get();
        if (null == this.space)
            Assertions.fail("space supplier yielded a null space");
        if (this.space.vid() == null)
            LOG.debug("provided space has no vid and thus can not be shutdown automatically");
    }

    @AfterEach
    protected void stop() {
        if (null == this.space)
            Assertions.fail("space nullified over course of testing");
        if (null != this.space.vid()) {
            Router.global().removeSpace(this.space.vid());
            assertDoesNotThrow(this.space::close);
        }
        if (null != this.spaceStorage) {
            Router.global().removeSpace(this.spaceStorage.vid());
            this.spaceStorage.close();
            this.spaceStorage = null;
        }
        this.space = null;
    }

    public static Map<fURI, Obj> generateRandomData(final fURI furiPrefix, int size) {
        final Map<fURI, Obj> data = new HashMap<>();
        for (int i = 0; i < size; i++) {
            data.put(furiPrefix.extend("x" + i), str("value" + i));
        }
        return data;
    }

    @ParameterizedTest
    @CsvSource(value = {
            "1.to(a)                                               % *a                                % 1",
            "$$ -> [a,b,c]                                         % *<$$>                              % [a,b,c]",
            ".                                                     % *<$$/#>                            % {[a,b,c],a,b,c}",
            ".                                                     % *<$$/0>                            % a",
            ".                                                     % *<$$/1>                            % b",
            ".                                                     % *<$$/2>                            % c",
            ".                                                     % *<$$/+>                            % {a,b,c}",
            ".                                                     % *<$$/+/>                           % [<$$/0>=>a,<$$/1>=>b,<$$/2>=>c]>-",
            ".                                                     % *<$$/>                             % [<$$/0>=>a,<$$/1>=>b,<$$/2>=>c]>-",
            ".                                                     % *<$$/+>                            % [a,b,c]>-",
            ".                                                     % *<$$/0>                            % a",
            ".                                                     % *<$$/1>                            % b",
            ".                                                     % *<$$/2>                            % c",
            "$$ -> [a,[b,[c,d],e],f]                               % *<$$/0>                            % a",
            ".                                                     % *<$$>                              % [a,[b,[c,d],e],f]",
            //    ".                                                     % */+                                % [a,[b,[c,d],e],f]",
            //    ".                                                     % */+/                               % [$$=>[a,[b,[c,d],e],f]]>-",
            ".                                                     % *<$$/+>                            % {a, [b,[c,d],e],f}",
            ".                                                     % *<$$/+/>                           % [$$/0=>a,$$/1=>[b,[c,d],e],$$/2=>f]>-",
            ".                                                     % *<$$/+/+>                          % {b,[c,d],e}",
            ".                                                     % *<$$/+/+/>                         % [<$$/1/0>=>b,<$$/1/1>=>[c,d],<$$/1/2>=>e]>-",
            ".                                                     % *<$$/RaNDoM>                       % noobj",
            ".                                                     % *<$$/0/0>                          % noobj",
            ".                                                     % *$$/1/0                            % b",
            ".                                                     % *$$/1/1                            % [c,d]",
            ".                                                     % *$$/0/1/1                          % noobj",
            ".                                                     % *$$/1/1/1                          % d",
            ".                                                     % *$$/1/1/+                          % {c,d}",
            ".                                                     % *$$/+                              % {a,[b,[c,d],e],f}",
            ".                                                     % *$$/+/+                            % {b,[c,d],e}",
            ".                                                     % *$$/+/+/+                          % {c,d}",
            ".                                                     % *$$/+/+/+/+                        % noobj",
            ".                                                     % *$$/+/+/+/+/+                      % noobj",
            ".                                                     % *$$/+/+/+/                         % [$$/1/1/0=>c,$$/1/1/1=>d]>-",
            //   ".                                                     % *$$/#                              % {[a,[b,[c,d],e],f],a,[b,[c,d],e],b,[c,d],c,d,e,f}",
            "$$ -> [a=>1,b=>2,c=>3]                                % *<$$>                              % [a=>1,b=>2,c=>3]",
            ".                                                     % *$$/a                              % 1",
            ".                                                     % *$$/b                              % 2",
            ".                                                     % *$$/c                              % 3",
            ".                                                     % *$$/c                              % 3",
            ".                                                     % *$$/+                              % {1,2,3}",
            ".                                                     % *<$$/+>.sum?int<=int{*}()          % 6",
            // ".                                                     % *#.sum?int<=int{*}()               % .",
            // ".                                                     % *(/+/+).sum?int<=int{*}()          % .",
            //         "$$/ -> [a=>1,b=>2,c=>3]                               % *$$/                               % [$$/a=>1,$$/b=>2,$$/c=>3]>-",
            ".                                                     % *<$$/x>                            % noobj",
            ".                                                     % *$$/a                              % 1",
            "$$ -> [a=>[b=>2,c=>3],d=>4]                           % *$$/a/b                            % 2",
            //".                                                     % *$$/#                              % [[a=>[b=>2,c=>3],d=>4],[b=>2,c=>3],2,3,4]>-", TODO: make poly.at() consistent with space.read()
            ".                                                     % *<$$/x>                            % noobj",
            //      ".                                                     % *$$/                               % [$$/a=>[b=>2,c=>3],$$/d=>4]>-",
            //       ".                                                     % *$$/+/                             % [$$/a=>[b=>2,c=>3],$$/d=>4]>-",
            ".                                                     % *$$/a/c                            % 3",
            ".                                                     % *$$/a                              % [b=>2,c=>3]",
            ".                                                     % *$$/d                              % 4",
            ".                                                     % *$$/+                              % [[b=>2,c=>3],4]>-",
            //   ".                                                     % *$$/+/#                            % [[a=>[b=>2,c=>3],d=>4],[b=>2,c=>3],2,3,4]>-",
            //     ".                                                     % *$$/+/+/#                          % [[b=>2,c=>3],2,3,4]>-",
            //         ".                                                     % *$$/a/                             % [$$/a/b=>2,$$/a/c=>3]>-",
            ".                                                     % *$$/a/+                            % {2,3}",
            //          ".                                                     % *$$/a/+/                           % [$$/a/b=>2,$$/a/c=>3]>-",
            //  "[$$/a/b -> 2, $$/a/c -> 3, $$/d -> 4]                 % *$$/+/                             % [$$/a/b=>2,$$/a/c=>3]>-",
            // Additional wildcard pattern tests
            "$$ -> [x=>1,y=>2,z=>3]                                % *<$$/+>                            % {1,2,3}",
            ".                                                     % *<$$/+/>                           % [$$/x=>1,$$/y=>2,$$/z=>3]>-",
            "$$ -> [a=>[b=>1,c=>2],d=>[e=>3,f=>4]]                 % *<$$/+/+>                          % {1,2,3,4}",
            ".                                                     % *<$$/a/+>                          % {1,2}",
            ".                                                     % *<$$/d/+>                          % {3,4}",
            ".                                                     % *<$$/+/b>                          % 1",
            ".                                                     % *<$$/+/e>                          % 3",
            "$$ -> [a=>[b=>[c=>10,d=>20],e=>[f=>30,g=>40]],h=>[i=>[j=>50,k=>60]]]  % *<$$/a/+/+>        % {10,20,30,40}",
            ".                                                     % *<$$/a/b/+>                        % {10,20}",
            ".                                                     % *<$$/+/+/+>                        % {10,20,30,40,50,60}",
            "$$ -> [a=>1,b=>2,c=>3,d=>4,e=>5]                      % *<$$/+>                            % {1,2,3,4,5}",
            ".                                                     % *<$$/b>                            % 2",
            ".                                                     % *<$$/+/>                           % [$$/a=>1,$$/b=>2,$$/c=>3,$$/d=>4,$$/e=>5]>-",
            "1.vid(abc)                                            % *abc                               % 1@abc",
            "1.vid(abc)                                            % *abc.vid(<.>)                    % 1",
            "[1@a,2@b,3@c]@d.map(10).vid(b)                        % *d                               % [1@a,10@b,3@c]@d",
            "[1@a,2@b,3@c]@d.map(10@b)                             % *d                               % [1@a,10@b,3@c]@d",
            // "[1@a,2@b,3@c]@d.map(*b + 10@b)                        % *d                               % [1@a,12@b,3@c]@d",
            // "[1@a,2@b,3@c]@d.map(*b + 10@b).to(d)                  % *d                               % 12@d",
            // "[1@a,2@b,3@c]@d                                       % *d._/_.vid(<.>)\\_.vid(<.>)      % [1,2,3]",
            // "[1@a,2@b,3@c]@d.map(*b + 10@b).to(d)                  % *d._/_.vid(<.>)\\_               % [1,2,3]@d",
            // "[1@a,2@b,3@c]@d.map(*b + 10@b).to(d)                  % *d._.vid(<.>)                    % 12"
    }, delimiter = '%')
    public void testMonoReadWrite(final String writeExpression, final String readExpression, final String expectedExpression) {
        final Obj writeObj = mParser.parse(make(writeExpression.equals(".") ? PREVIOUS_LINE.get(0) : writeExpression)).apply();
        if (this.sleepBetweenReads > 0)
            CommonUtil.sleepThread(this.sleepBetweenReads);
        final Obj readObj = mParser.parse(make(readExpression.equals(".") ? PREVIOUS_LINE.get(1) : readExpression)).apply();
        final Obj resultObj = mParser.parse(make(expectedExpression.equals(".") ? PREVIOUS_LINE.get(2) : expectedExpression)).apply();
        if (!writeExpression.equals("."))
            PREVIOUS_LINE.set(0, make(writeExpression));
        if (!readExpression.equals("."))
            PREVIOUS_LINE.set(1, make(readExpression));
        if (!expectedExpression.equals("."))
            PREVIOUS_LINE.set(2, make(expectedExpression));
        Graphitty.log(this.space).debug("\n\twrite [%s => %s]\n\tread [%s => %s]\n\texpected [%s => %s]",
                make(writeExpression), writeObj,
                make(readExpression), readObj,
                make(expectedExpression), resultObj);
        try {
            assertEquals(resultObj, readObj);
        } catch (final Exception e) {
            LOG.error(e);
        }
    }

    private String make(final String expression) {
        return expression.contains("$$") ? expression.replace("$$", this.baseURI.toString()) : expression;
    }
}
