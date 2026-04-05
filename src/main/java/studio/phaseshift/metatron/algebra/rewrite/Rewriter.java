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

package studio.phaseshift.metatron.algebra.rewrite;

import com.google.common.base.Objects;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Rewriter {

    protected final List<Inst> sourceInsts;
    protected List<Inst> matchInsts;
    protected Predicate<List<Inst>> matchPredicate = null;
    protected boolean repeat = false;
    protected boolean matchC = true;


    private Rewriter(final List<Inst> sourceInsts) {
        this.sourceInsts = sourceInsts;
    }

    public static Rewriter search(final List<Inst> sourceInsts) {
        return new Rewriter(sourceInsts);
    }

    public Rewriter match(final List<Inst> matchInsts, final Predicate<List<Inst>> predicate) {
        this.matchInsts = matchInsts;
        this.matchPredicate = predicate;
        return this;
    }

    public Rewriter match(final List<Inst> matchInsts) {
        this.matchInsts = matchInsts;
        return this;
    }

    public Rewriter repeat() {
        this.repeat = true;
        return this;
    }


    public Rewriter matchCC() {
        this.matchC = true;
        return this;
    }


    private static boolean instsMatch(final Inst match, final Inst source) {
        if (!source.tid().test(match.tid()))
            return false;
        /*if(!source.dom().test(match.dom()))
            return false;
        if(!source.rng().test(match.rng()))
            return false;*/
       /* if (!match.c().equals(source.c())) // equals or within?
            return false;*/
        if (match.args().isEmpty())
            return true;
        if (match.args().count() != source.args().count())
            return false;
        for (int i = 0; i < match.args().count(); i++) {

            Obj matchArg = match.arg(i);
            final Obj sourceArg = source.arg(i);
            //if( sourceArg.isNoObj() && matchArg.isNoObj())
            // return false;
            if (matchArg.isObjCall()) {
                //if (matchArg.isCall())
                //  matchArg = matchArg.apply(sourceArg);
                if (!sourceArg.test(matchArg))
                    return false;
            } else if (!source.arg(i).tid().basePath().equals(match.arg(i).tid().basePath()))
                return false;
            //if (!source.arg(i).test(match.arg(i))) // TODO: why is this matching on map
            //    return false;
        }
        return true;
    }

    public List<Inst> rewrite(final Function<Map<Inst, Inst>, List<Inst>> rewriteFunc) {
        List<Inst> current = this.sourceInsts;
        if (this.matchPredicate != null && !this.matchPredicate.test(this.matchInsts))
            return current;
        List<Inst> last = List.of();
        while (!Objects.equal(current, last)) {
            last = current;
            current = internalRewrite(this.matchInsts, current, rewriteFunc);
            if (!this.repeat)
                break;
        }
        return current;
    }


    private static List<Inst> internalRewrite(final List<Inst> matchInsts, final List<Inst> sourceInsts, final Function<Map<Inst, Inst>, List<Inst>> rewriteFunc) {
        final Map<Inst, Inst> matchMap = new LinkedHashMap<>();
        final List<Inst> newInsts = new ArrayList<>();
        if (matchInsts.size() > sourceInsts.size())
            return sourceInsts;
        for (int i = 0; i < sourceInsts.size(); i++) {
            if (matchInsts.size() > (sourceInsts.size() - i)) {
                newInsts.add(sourceInsts.get(i));
                continue;
            }
            final List<Inst> subSource = sourceInsts.subList(i, i + matchInsts.size());
            boolean found = true;
            for (int j = 0; j < matchInsts.size(); j++) {
                if (!instsMatch(matchInsts.get(j), subSource.get(j))) { // TWWEAK
                    newInsts.add(sourceInsts.get(i));
                    found = false;
                    matchMap.clear();
                    break;
                } else {
                    matchMap.put(matchInsts.get(j), subSource.get(j));
                }
            }
            if (found) {
                newInsts.addAll(rewriteFunc.apply(matchMap));
                i = i + matchInsts.size() - 1;
            }
        }
        return newInsts;
    }
}
