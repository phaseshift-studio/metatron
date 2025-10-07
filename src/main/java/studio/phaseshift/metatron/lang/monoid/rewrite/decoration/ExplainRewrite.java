/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.lang.monoid.rewrite.decoration;

import studio.phaseshift.metatron.lang.monoid.rewrite.Rewrite;
import studio.phaseshift.metatron.lang.obj.mtron.MInst;

public abstract class ExplainRewrite extends MInst implements Rewrite {

    public ExplainRewrite() {
        super(null, null, null);
        //  super(fURI.of("explain/rewrite"));
    }

  /*  @Override
    public Code rewrite(final Code code) {
        return code.value().stream().anyMatch(i -> i.tid().equals(EXPLAIN_URI)) ?
                new Code(List.of(new ExplainMetaInst(code)), CODE_URI, null) : code;
    }

    public static class ExplainMetaInst extends Inst {

        private static final Logger LOG = LoggerFactory.getLogger(ExplainMetaInst.class);


        public ExplainMetaInst(final Code code) {
            super(fURI.of("explain/engine"), new Code(code.value().stream().filter(i -> !i.tid().equals(EXPLAIN_URI)).toList(), CODE_URI, null));
        }

        @Override
        public InstF f() {
            return new InstF(o -> {
                Monoid monoid = new MMonoid(this.args(0));
                LOG.info(Graphitty.string("introspecting\n\t%s".formatted(this)));
                return o;
            });
        }
    }*/
}
