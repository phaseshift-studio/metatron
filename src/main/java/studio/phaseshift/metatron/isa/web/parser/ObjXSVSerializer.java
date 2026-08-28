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

package studio.phaseshift.metatron.isa.web.parser;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Lst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Poly;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.mach.io.type.AbstractObjSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static studio.phaseshift.metatron.isa.m.mInstSet.LST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.type.Bool.BOOL_FALSE;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst0;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.web.webInstSet.OBJ_XSV_SERIALIZER_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class ObjXSVSerializer extends AbstractObjSerializer<String> {

    public static final fURI OBJ_XSV_SERIALIZER_VID = OBJ_XSV_SERIALIZER_TID;

    private static final fURI KEY_DELIMITER = fURI.Singleton.f("delimiter");
    private static final fURI KEY_HEADER = fURI.Singleton.f("header");

    private static final ObjXSVSerializer INSTANCE = new ObjXSVSerializer();

    public static ObjXSVSerializer single() {
        return INSTANCE;
    }

    public static ObjXSVSerializer csv() {
        return ObjXSVSerializer.of(rec(uri(KEY_DELIMITER), str(",")), null);
    }

    public static ObjXSVSerializer of(final Rec rec, final fURI vid) {
        return new ObjXSVSerializer(rec.jvm(), OBJ_XSV_SERIALIZER_TID, vid);
    }

    protected ObjXSVSerializer(final Map<Obj, Obj> jvm, final fURI tid, final fURI vid) {
        super(jvm, tid, vid);
    }

    public ObjXSVSerializer() {
        super(OBJ_XSV_SERIALIZER_TID, OBJ_XSV_SERIALIZER_VID);
        this.at(KEY_DELIMITER, str(","), Poly.MUTABLE);
        this.at(KEY_HEADER, BOOL_FALSE, Poly.MUTABLE);
    }

    /// //////////////////////////////

    private String getDelimiter() {
        return this.at(KEY_DELIMITER).orElse(str(",")).strValue();
    }

    private boolean isHeader() {
        return this.at(KEY_HEADER).orElse(BOOL_FALSE).boolValue();
    }

    /// //////////////////////////////

    @Override
    public Obj read(final String data) throws MTronException {
        if (null == data || data.isEmpty())
            return lst0();
        final String delimiter = this.getDelimiter();
        final boolean header = this.isHeader();
        final List<Obj> rows = new ArrayList<>();
        String[] keys = null;
        for (final String line : data.split("\\R")) {
            if (line.trim().isEmpty())
                continue;
            final String[] cells = line.split(Pattern.quote(delimiter), -1);
            if (header && null == keys) {
                keys = cells;
                continue;
            }
            if (header) {
                final Map<Obj, Obj> map = new LinkedHashMap<>();
                for (int i = 0; i < keys.length; i++) {
                    map.put(uri(keys[i].trim()), i < cells.length ? this.parseCell(cells[i]) : noobj());
                }
                rows.add(rec(map, REC_TID, null));
            } else {
                final List<Obj> row = new ArrayList<>();
                for (final String cell : cells)
                    row.add(this.parseCell(cell));
                rows.add(lst(row, LST_TID, null));
            }
        }
        return lst(rows, LST_TID, null);
    }

    private Obj parseCell(final String cell) {
        final String value = cell.trim();
        if (value.isEmpty())
            return noobj();
        try {
            return ObjmtronSerializer.single().parse(value);
        } catch (final Exception e) {
            return str(value);
        }
    }

    /// //////////////////////////////

    @Override
    public String write(final Obj obj) throws MTronException {
        if (obj.isNoObj())
            return "";
        if (!obj.isLst())
            throw MTronException.of("unsupported type: %s", obj.tid());
        final Lst lst = obj.asLst();
        if (lst.isEmpty())
            return "";
        final String delimiter = this.getDelimiter();
        final List<String> out = new ArrayList<>();
        if (lst.lstValue().get(0).isRec()) {
            final List<Rec> recs = new ArrayList<>();
            for (final Obj row : lst.lstValue()) {
                if (!row.isRec())
                    throw MTronException.of("unsupported lst element type: %s", row.tid());
                recs.add(row.asRec());
            }
            if (this.isHeader()) {
                out.add(recs.get(0).keys().map(k -> k.uriValue().toString()).collect(Collectors.joining(delimiter)));
            }
            for (final Rec rec : recs) {
                out.add(rec.jvm().values().stream().map(this::writeCell).collect(Collectors.joining(delimiter)));
            }
        } else if (lst.lstValue().get(0).isLst()) {
            for (final Obj row : lst.lstValue()) {
                out.add(row.asLst().lstValue().stream().map(this::writeCell).collect(Collectors.joining(delimiter)));
            }
        } else {
            throw MTronException.of("unsupported lst element type: %s", lst.lstValue().get(0).tid());
        }
        return String.join("\n", out);
    }

    private String writeCell(final Obj obj) {
        return obj.isNoObj() ? "" : ObjmtronSerializer.compact().write(obj);
    }

    /// //////////////////////////////

    @Override
    public Obj inputBytes(final ByteBuffer bytes) throws MTronException {
        return this.read(new String(bytes.array(), StandardCharsets.UTF_8));
    }

    @Override
    public fURI vid() {
        return OBJ_XSV_SERIALIZER_VID;
    }
}
