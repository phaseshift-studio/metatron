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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.Inst.INST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Lst.LST_TYPE;
import static studio.phaseshift.metatron.isa.m.type.Type.TYPE_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

public interface InstSet extends Space {

    InstSet instset0 = (new AbstractInstSet(false) {
    }).c(cInt.ZERO()).as();

    Type INSTSET_TYPE = Type.Builder.build().tid(REC_TID).vid(INSTSET_TID)
            .isaPredicate(rec(
                    uri(CONSTQ).maybe().asUri(), lst(T(ALL.maybe())),
                    uri(TYPE).maybe(), lst(T(ALL_STAR)).maybe(),
                    uri(INST).maybe(), lst(INST_TYPE).maybe(),
                    uri(REWRITE).maybe(), lst(INST_TYPE).maybe(),
                    uri(SUGAR).maybe(), lst(LST_TYPE).maybe())).create();

    fURI A = f("A");
    fURI B = f("B");
    fURI C = f("C");
    fURI D = f("D");
    fURI E = f("E");
    fURI F = f("F");
    fURI G = f("G");

    void setup();

    @Override
    fURI pattern();

    Set<Obj> consts();

    Set<Type> types();

    Set<Inst> insts();

    Set<Inst> rewrites();

    public static class Helper {

        public static Inst rewriter(final fURI tid, Function<Code, Code> rewrite) {
            return instC(tid.dom(CODE_TID).rng(CODE_TID.maybe()), lst(), (lhs, inst) -> rewrite.apply(lhs.asCode()));
        }
    }

    /*
     * @author Marko A. Rodriguez (http://markorodriguez.com)
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface JREService {
        String vid();


        class Helper {
            public static fURI vid(final Class<?> spec) {
                return f(spec.getAnnotation(JREService.class).vid());
            }

            public static void verifyClass(final Class<?> spec, final fURI vid) throws MTronException {
                if (!(!spec.isAnnotationPresent(JREService.class) || Helper.vid(spec).equals(vid))) {
                    throw MTronException.of("invalid service annotation for %s: %s (expected %s)", spec, vid, Helper.vid(spec));
                }
            }
        }
    }

    /// ///////////////////////////////////////////////////////////////////////////////////////

    static Stream<ServiceLoader.Provider<InstSet>> loadInstSetProvider(final fURI pattern) {
        return ServiceLoader.load(InstSet.class)
                .stream()
                .peek(p -> {
                    if (!p.type().isAnnotationPresent(JREService.class))
                        Graphitty.log(InstSet.class).warn("an inst set without a service metadata located: %s", p.type().getCanonicalName());
                })
                .filter(p -> p.type().isAnnotationPresent(JREService.class))
                .filter(p -> f(p.type().getAnnotation(JREService.class).vid()).test(pattern));
    }

    static Stream<InstSet> importInstSetStream(final fURI vid) {
        return importInstSetStream(vid, null);
    }

    static Stream<InstSet> importInstSetStream(final fURI vid, final fURI prefix) {
        if (null != prefix)
            Router.global().registerPrefix(prefix, vid);
        return loadInstSetProvider(vid)
                .map(ServiceLoader.Provider::get)///  new
                .peek(isa -> Router.global().addSpace(isa)) // add to router
                .peek(InstSet::setup); // setup
    }

    static void importInstSet(final fURI vid, final fURI prefix) {
        importInstSetStream(vid, prefix).forEach(isa -> {
            Graphitty.log(isa.getClass()).error("loading instruction set: %s", isa);
        });
    }

    static void importInstSet(final fURI vid) {
        importInstSet(vid, null);
    }
}
