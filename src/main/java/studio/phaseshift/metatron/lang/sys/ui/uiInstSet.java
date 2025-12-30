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

package studio.phaseshift.metatron.lang.sys.ui;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.core.m.type.*;
import studio.phaseshift.metatron.lang.core.m.type.impl.MInstSet;
import studio.phaseshift.metatron.lang.sys.console.Highlighter;
import studio.phaseshift.metatron.lang.sys.fs.ImageHelper;
import studio.phaseshift.metatron.lang.sys.fs.fileSpace;
import studio.phaseshift.metatron.lang.sys.fs.fsInstSet;
import studio.phaseshift.metatron.ui.widget.Table;
import studio.phaseshift.metatron.util.MTronException;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.lang.core.m.inst.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.*;
import static studio.phaseshift.metatron.lang.core.m.inst.mInstSet.BYTES_TID;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MBytes.bytes;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MType.T;
import static studio.phaseshift.metatron.lang.core.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.lang.sys.fs.fileSpace.FS_TYPE;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class uiInstSet extends MInstSet {

    public static final fURI UI_INSTSET_TID = fURI.of("/ui");
    public static final fURI TABLE_TID = UI_INSTSET_TID.extend("table");
    public static final fURI PANEL_TID = UI_INSTSET_TID.extend("panel");
    public static final fURI STYLE_TID = UI_INSTSET_TID.extend("style");

    public uiInstSet(final fURI vid) {
        super(UI_INSTSET_TID, vid);
    }

    public static uiInstSet create() {
        return new uiInstSet(fURI.fnull);
    }

    @Override
    public Set<Type> types() {
        return Set.of(
                T(TABLE_TID, isa_(rec("header",T(LST_TID),"data",T(LST_TID)))),
                T(STYLE_TID, isa_(rec("root",T(TABLE_TID)))),
                T(PANEL_TID));

    }

    /*@Override
    public Set<Inst> insts() {
        return new LinkedHashSet<>(List.of(
                instC(UI_INSTSET_TID.extend("inst/draw").dom(TABLE_TID).rng(TABLE_TID),lst(),(lhs,inst) -> {
                   final Table table = new Table(lhs.<Rec>as(N).at("header").lstValue().stream().map(Obj::toString).toList());
                   lhs.<Rec>as().at("data").<Lst>as().lstValue().forEach(o -> {
                      table.addRow(o.<Lst>as().lstValue());
                   });
                   System.out.println(Highlighter.format(table));
                   return table;
                }),
                instC(UI_INSTSET_TID.extend("inst/style").dom(TABLE_TID).rng(STYLE_TID),lst(),(lhs,inst) -> {
                   return lhs; // return lhs.<Table>as().style();
                })
        ));
    }*/
}
