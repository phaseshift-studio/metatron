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

package studio.phaseshift.metatron.lang.monoid.rewrite.decoration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.phaseshift.metatron.lang.fURI;
import studio.phaseshift.metatron.lang.monoid.Monoid;
import studio.phaseshift.metatron.lang.monoid.MMonoid;
import studio.phaseshift.metatron.lang.monoid.rewrite.Rewrite;
import studio.phaseshift.metatron.lang.obj.SObj;
import studio.phaseshift.metatron.ui.Graphitty;

import java.util.List;

import static studio.phaseshift.metatron.lang.inst.SInst.EXPLAIN_URI;
import static studio.phaseshift.metatron.lang.obj.BObj.*;

public class ExplainRewrite extends SObj.Inst implements Rewrite {

    public ExplainRewrite() {
        super(fURI.of("explain/rewrite"));
    }

    @Override
    public Code rewrite(final Code code) {
        return code.value().stream().anyMatch(i -> i.tid().equals(EXPLAIN_URI)) ?
                new SObj.Code(List.of(new ExplainMetaInst(code)), CODE_URI, null) : code;
    }

    public static class ExplainMetaInst extends SObj.Inst {

        private static final Logger LOG = LoggerFactory.getLogger(ExplainMetaInst.class);


        public ExplainMetaInst(final Code code) {
            super(fURI.of("explain/engine"), new SObj.Code(code.value().stream().filter(i -> !i.tid().equals(EXPLAIN_URI)).toList(), CODE_URI, null));
        }

        @Override
        public InstF f() {
            return new InstF(o -> {
                Monoid.Monoid monoid = new MMonoid.Monoid(this.args(0));
                LOG.info(Graphitty.string("introspecting\n\t%s".formatted(this)));
                return o;
            });
        }
    }
}
