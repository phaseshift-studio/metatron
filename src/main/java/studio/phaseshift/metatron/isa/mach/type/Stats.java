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

package studio.phaseshift.metatron.isa.mach.type;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Rec;

import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public interface Stats {

    public static final fURI STATS_TID = MACH_ISA_TID.extend("stats");

    default public <S extends Rec> S stats(final fURI tid) {
        return (S) rec();
    }

    public IOStats ioStats();

    public MonadicStats monadicStats();

    public interface IOStats extends Rec {

        public static final fURI STATS_IO_TID = STATS_TID.extend("io");

        default IOStats incrBytesRecv(final long bytes) {
            return this.at(uri("bytes_recv"), jnt(this.at(uri("bytes_recv")).intValue() + (int) bytes), MUTABLE).as();
        }

        default IOStats incrBytesSent(final long bytes) {
            return this.at(uri("bytes_sent"), jnt(this.at(uri("bytes_sent")).intValue() + (int) bytes), MUTABLE).as();
        }

        default long bytesSent() {
            return this.at(uri("bytes_sent")).intValue();
        }

        default long bytesRecv() {
            return this.at(uri("bytes_recv")).intValue();
        }

    }

    public interface MonadicStats extends Rec {

        public static final fURI MACH_ISA_STATS_MONADIC_TID = STATS_TID.extend("monadic");

        default MonadicStats incrHaltedMonads(long haltedMonads) {
            return this.at(uri("halted_monads"), jnt(this.at(uri("halted_monads")).intValue() + (int) haltedMonads), MUTABLE).as();
        }

        default MonadicStats incrKilledMonads(long killedMonads) {
            return this.at(uri("killed_monads"), jnt(this.at(uri("killed_monads")).intValue() + (int) killedMonads), MUTABLE).as();
        }

        default MonadicStats incrRunningMonads(long runningMonads) {
            return this.at(uri("running_monads"), jnt(this.at(uri("running_monads")).intValue() + (int) runningMonads), MUTABLE).as();
        }

        default MonadicStats incrBarrierMonads(long barrierMonads) {
            return this.at(uri("barrier_monads"), jnt(this.at(uri("barrier_monads")).intValue() + (int) barrierMonads), MUTABLE).as();
        }

        default void resetMonads() {
            this.at(uri("running_monads"), jnt(0), MUTABLE);
            this.at(uri("halted_monads"), jnt(0), MUTABLE);
            this.at(uri("killed_monads"), jnt(0), MUTABLE);
            this.at(uri("barrier_monads"), jnt(0), MUTABLE);
        }

        default long runningMonads() {
            return this.at(uri("running_monads")).intValue();
        }

        default long haltedMonads() {
            return this.at(uri("halted_monads")).intValue();
        }

        default long killedMonads() {
            return this.at(uri("killed_monads")).intValue();
        }

        default long barrierMonads() {
            return this.at(uri("barrier_monads")).intValue();
        }
    }
}