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

package studio.phaseshift.metatron.isa.sys.type_;

import org.zeroturnaround.exec.ProcessExecutor;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.impl.MCode;
import studio.phaseshift.metatron.isa.m.type.impl.MInst;
import studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread;

import java.util.List;

import static studio.phaseshift.metatron.isa.mach.type.thread.VirtualThread.virtual;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class NativeProcess {

    public static VirtualThread spawnNativeProcess(final String command) {
        return null;
        //  virtual(new MCode(List.of(MInst.instC(MINST_))))
        //new ProcessExecutor(command).redirectOutput(System.out).execute();
    }
}
