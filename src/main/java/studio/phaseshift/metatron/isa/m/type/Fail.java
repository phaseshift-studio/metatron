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

import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.algebra.PlusMonoid;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.util.MTronException;

import java.util.*;

import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;

import studio.phaseshift.metatron.isa.m.type.impl.MFail;

import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Fail extends Obj, PlusMonoid<Fail> {

    Type FAIL_TYPE = Type.Builder.build().tid(FAIL_TID).vid(FAIL_TID).create();

    @Override
    Fail clone(final Object jvm, final fURI tid, final fURI vid);

    @Override
    Throwable jvm();

    Fail plus(final Fail rhs);

    default Fail jvm(final Fail value) {
        return this.clone(value, this.tid(), this.vid());
    }

    default Fail tid(final fURI tid) {
        return this.clone(this.jvm(), tid, this.vid());
    }

    /**
     * The Java exception at this fail level.
     */
    default String message() {
        return this.jvm().getMessage();
    }

    /**
     * The nested mtron cause, derived from {@link Throwable#getCause()} on the fly.
     * Returns empty when there is no nested Java cause.
     * If this fail is caught, the returned cause is also caught (recursive).
     * The returned Fail is transient (no VID) — it is not written to the fail space.
     */
    default Optional<Fail> cause() {
        return Optional.ofNullable(this.jvm().getCause()).map(t -> {
            Fail inner = MFail.transientFail(t);
            if (this instanceof CaughtFail)
                inner = inner.caught();
            return inner;
        });
    }

    Fail caught();

    @Override
    default boolean isFail() {
        return true;
    }

    default MTronException asException() {
        return MTronException.of(this.jvm());
    }

    @Override
    default boolean isResolved(final boolean nested) {
        return true;
    }

    final class Helper {
        public static Fail inst_eval_fail(final Inst inst, final String reason, final Exception e) {
            return fail(e, "[{{r}}inst eval error{{X}}] %s: %s", reason, inst);
        }
    }

    final class FailType {

        public static Set<Inst> insts() {
            return new LinkedHashSet<>(List.of(
                    instC(CAUSE_INST_TID.dom(FAIL_TID).rng(FAIL_TID.maybe()), lst(), (lhs, x) -> lhs.<Fail>as().cause().map(z -> (Obj) z).orElse(noobj())), // necessary cause of type casting
                    instC(REIFY_INST_TID.dom(FAIL_TID).rng(REC_TID), lst(), (lhs, x) -> {
                        final StackTraceElement[] element = lhs.<Fail>as().jvm().getStackTrace();
                        final Map<Obj, Obj> throwable = new LinkedHashMap<>();
                        throwable.put(uri(Tokens.MESSAGE), str(lhs.<Fail>as().jvm().getMessage()));
                        if (element.length > 0) {
                            throwable.put(uri("class"), uri(element[0].getClassName().replace(".", "/")));
                            throwable.put(uri("method"), uri(element[0].getMethodName().replace(".", "/")));
                            throwable.put(uri("line"), jnt(element[0].getLineNumber()));
                        }
                        return rec(throwable);
                    }) // necessary cause of type casting
            ));

        }
    }

    interface CaughtFail extends Fail {

    }
}
