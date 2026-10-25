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

package studio.phaseshift.metatron.isa.m.type;

import studio.phaseshift.metatron.furi.c.cInt;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.Sugar;
import studio.phaseshift.metatron.isa.m.parser.mParser;
import studio.phaseshift.metatron.isa.mach.type.Machine;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.MTronException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * A machine component that happens to also be a Space.
 * <p>
 * The {@code Machine.Component} half is the model — an ISA is one of the five things a Machine is made of, beside
 * its compiler, processor, network and memory. The {@code Space} half is an accident of how importing was
 * implemented: {@code importInstSetStream} does {@code Machine.current().memory().addSpace(isa)}, so an ISA had to be a
 * Space to be reachable. Frame-scoping imports removes that need, and this declaration is where the two
 * descriptions meet in the meantime.
 */
public interface InstSet extends Space, Machine.Component {

    public static InstSet instset0() {
        return new AbstractInstSet(false) {
        }.c(cInt.ZERO()).as();
    }

    Type INSTSET_TYPE = Type.Builder.build().tid(REC_TID).vid(INSTSET_TID)
            .isaPredicate(rec(
                    uri(PATTERN).maybe().asUri(), URI_TYPE,
                    uri(CONST).maybe().asUri(), lst(T(ALL.maybe())),
                    uri(TYPE).maybe(), lst(T(ALL_STAR)).maybe(),
                    uri(INST).maybe(), lst(INST_TYPE).maybe(),
                    uri(REWRITE).maybe(), lst(INST_TYPE).maybe(),
                    uri(SUGAR).maybe(), lst(LST_TYPE).maybe(),
                    // the union's MEMBERSHIP: the imported ISAs, by vid, IN IMPORT ORDER (earlier = higher
                    // precedence). Declared here because it is what makes the union reflective — this is the field
                    // that answers "what are my insts, rewrites, sugars and types" without walking the mounts.
                    uri(REFERENCE).maybe().asUri(), lst(URI_TYPE).maybe()))
            .constructor(arg -> {
                final InstSet isa = new AbstractInstSet(arg.asRec().jvm(), arg.tid(), arg.vid()) {
                };
                InstSet.mount(isa);
                isa.setup();
                return isa;
            }).create();

    fURI A = f("A");
    fURI B = f("B");
    fURI C = f("C");
    fURI D = f("D");
    fURI E = f("E");
    fURI F = f("F");
    fURI G = f("G");

    /**
     * The DECLARED read: what this instruction set's tables say about a pattern, with no lazy
     * materialization. A plain store answers exactly as {@code read} does; a union walks its members with
     * this form, so a read that the machine makes as a FALLBACK (see {@code Machine.read}'s instset hop)
     * cannot cause work on the reader's behalf.
     */
    default Obj readDeclared(final fURI pattern) {
        return this.read(pattern);
    }

    void setup();

    /**
     * Refer to another instset — an import, held BY REFERENCE and never copied.
     * <p>
     * A plain store ignores this (the default): it is a {@link Space} holding its own structure and nothing else.
     * It is meaningful on an instset that is a UNION over the ones it imports, where this both records the
     * membership and fixes its precedence — earlier references are consulted first, after the own structure.
     */
    default InstSet refer(final InstSet isa) {
        return this;
    }

    /**
     * The instruction sets this vocabulary is composed of — for a plain store, itself and nothing else. The
     * machine's own instset (a union) walks its levels: this level, its imports in import order, then the
     * enclosing machine's, each ISA once.
     * <p>
     * THIS IS THE ENUMERATION, and {@code Machine.current().instset()} is its door: a consumer that wants "every
     * instruction set this machine uses" (a rewriter collecting rules, the category graph collecting morphisms, a
     * reflector listing the vocabulary) asks it HERE rather than scanning {@code memory().spaces()} for
     * {@code instanceof InstSet} — the vocabulary is the machine's, not the data index's, and the index entry is
     * the half of the old Router model that is slated to go.
     */
    default List<InstSet> members() {
        return List.of(this);
    }

    @Override
    fURI pattern();

    Set<Obj> consts();

    Set<Type> types();

    Set<Inst> insts();

    Set<Inst> rewrites();

    default Set<Sugar> sugars() {
        return Set.of();
    }

    /**
     * The address→tid edge of this instset: given a vid, return the tid of the
     * obj stored at that vid — for a type, its refinement (parent); for a
     * constant, inst, or rewrite, the obj's own type.  Returns {@code null}
     * when the vid is unknown or a base type (vid == tid).  Address-only; does
     * not construct a {@link Type}.
     */
    default fURI vidToTid(final fURI vid) {
        return null;
    }

    class Helper {

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
                        throw MTronException.of("an inst set without a service metadata located: %s", p.type().getCanonicalName());
                })
                .filter(p -> p.type().isAnnotationPresent(JREService.class))
                .filter(p -> f(p.type().getAnnotation(JREService.class).vid()).test(pattern));
    }

    /**
     * MOUNT AN INSTRUCTION SET — the ONE door a machine gets an ISA through: it is REFERRED into the machine's own
     * instset, which is where it lives (Marko, 2026-10-25: "Instsets shouldn't be in Memory as spaces (though they are
     * spaces). They should be in InstSet only."). It is NOT a space in memory's index, which holds data spaces alone —
     * but the addresses it holds stay routable: memory's lookup weighs the libraries the machine mounted against its
     * data spaces, and the most specific claimant answers and takes the write ("the most specific gains the write").
     * A space handed to {@code BasicMemory.addSpace} that is an instruction set is referred into the instset too (its
     * sugars are this door's alone), so no caller can mount one as data; {@code removeSpace} unmounts it (no longer
     * routed, still a member).
     */
    static void mount(final InstSet isa) {
        Machine.current().instset().refer(isa);
        isa.sugars().forEach(mParser::addSugar);
    }

    static Stream<InstSet> importInstSetStream(final fURI vid) {
        return importInstSetStream(vid, null);
    }

    static Stream<InstSet> importInstSetStream(final fURI vid, final fURI prefix) {
        if (null != prefix)
            Machine.current().memory().registerPrefix(prefix, vid);
        // IMPORT IS "ENSURE MOUNTED" -- AN OPTIMIZATION, NEVER A PROTECTION. a provider whose
        // instruction set is already mounted in scope is skipped BEFORE it is instantiated (the
        // service annotation carries the vid), so re-running an import is observationally identical
        // to skipping it. that identity is what makes the skip sound: a mounted ISA's vocabulary is
        // not something a re-import may change -- a user redefinition belongs at the redefiner's own
        // level, shadowing the import, never written into it (seal-and-shadow) -- so there is nothing
        // a reload could restore. a caller that wants a FRESH instance unmounts the old one first,
        // which removes it from scope and makes the import real. the probe walks the machine's vocabulary
        // (its instset's members, the enclosing machines' included), so a frame sees its enclosing machine's mounts.
        final Set<fURI> mounted = Machine.current().instset().members().stream()
                .map(PlatonicObj::vid)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        return loadInstSetProvider(vid)
                .filter(p -> !mounted.contains(JREService.Helper.vid(p.type())))
                .map(ServiceLoader.Provider::get)
                .peek(InstSet::mount)
                .peek(InstSet::setup); // setup
    }

    static void importInstSet(final fURI vid, final fURI prefix) {
        importInstSetStream(vid, prefix).forEach(isa -> {
            Graphitty.log(isa).info("loading instruction set: %s", isa.vidOrTid());
        });
    }

    public static void importInstSet(final fURI vid) {
        importInstSet(vid, null);
    }
}
