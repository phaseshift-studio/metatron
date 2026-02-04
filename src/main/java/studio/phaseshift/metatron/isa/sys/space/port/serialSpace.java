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

package studio.phaseshift.metatron.isa.sys.space.port;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.MSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static studio.phaseshift.metatron.furi.fURI.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Rel.REL_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SPACE_CONFIG;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS_ISA_TID;
import static studio.phaseshift.metatron.lang.ai.llm.llmInstSet.SPACE_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class serialSpace extends MSpace<SerialPort[]> {

    public static final fURI SERIAL_SPACE_TID = SYS_ISA_TID.extend("space/serial");

    public static final Type SERIAL_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(SERIAL_SPACE_TID)
            .constructor(
                    instC(INST_TID.dom(ALL.maybe()).rng(SERIAL_SPACE_TID),
                            lst(isa_(SPACE_CONFIG.plus(rec(uri(Tokens.REWRITE), REL_TYPE))).tryToInst()),
                            (lhs, inst) -> serialSpace.of(inst.arg(0).asRec(), inst.arg(0).vid()))).create();

    private final Tuple.Pair<String, String> rewrite;
    private final Map<String, Tuple.Pair<SerialPort, Lst>> buffers = new HashMap<>();

    public static serialSpace of(final Rec config, final fURI vid) {
        return new serialSpace(SerialPort.getCommPorts(), config.jvm(), vid);
    }

    protected serialSpace(final SerialPort[] ports, final Map<Obj, Obj> config, final fURI vid) {
        super(ports, config, SERIAL_SPACE_TID, vid);
        final Rel rewrite = this.at(uri(Tokens.REWRITE)).orElse(rel(uri(""), uri(""))).asRel();
        LOG.debug("rewrite: %s", rewrite);
        this.rewrite = Tuple.Pair.with(rewrite.first().uriValue().toString(), rewrite.second().uriValue().toString());
    }

    protected String getPortMetadata(final SerialPort port) {
        return String.format("{{b}}%s{{g}}@{{y}}%d{{X}} baud {{g}}[{{b}}%s{{g}}][{{b}}%s{{g}}]{{X}}", port.getSystemPortName(), port.getBaudRate(), port.getDescriptivePortName(), port.getManufacturer());
    }

    protected Obj getOrCreateBuffer(final SerialPort port) {
        if (!this.buffers.containsKey(port.getSystemPortName())) {
            final Lst sb = lst(Collections.synchronizedList(new ArrayList<>()));
            if (!port.openPort()) {
                LOG.error("failed to open port: %s", getPortMetadata(port));
                this.buffers.remove(port.getSystemPortName());
                return noobj();
            } else
                LOG.info("opened port: %s", getPortMetadata(port));
            this.buffers.put(port.getSystemPortName(), Tuple.Pair.with(port, sb));
            port.addDataListener(new SerialPortDataListener() {
                @Override
                public int getListeningEvents() {
                    return SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
                }

                @Override
                public void serialEvent(SerialPortEvent event) {
                    if ((event.getEventType() & SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) > 0) {
                        LOG.warn("port disconnected: %s", getPortMetadata(port));
                        port.closePort();
                        buffers.remove(port.getSystemPortName());
                    }
                }
            });
            port.addDataListener((new SerialPortDataListener() {
                @Override
                public int getListeningEvents() {
                    return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
                }

                @Override
                public void serialEvent(final SerialPortEvent event) {
                    try {
                        if ((event.getEventType() & SerialPort.LISTENING_EVENT_DATA_AVAILABLE) > 0) {
                            byte[] newData = new byte[port.bytesAvailable()];
                            int totalBytesRead = port.readBytes(newData, newData.length);
                            LOG.info("read %d bytes from %s", totalBytesRead, port.getSystemPortName());
                            sb.add(str(new String(newData, StandardCharsets.UTF_8)), MUTABLE);
                        }
                    } catch (final Exception e) {
                        sb.add(fail(e));
                    }
                }
            }));
        }
        return this.buffers.get(port.getSystemPortName()).get1();
    }

    @Override
    public Obj read(final fURI vid) {
        return objs(Arrays.stream(SerialPort.getCommPorts())
                .filter(port -> this.pattern.retractPattern().extend(port.getSystemPortName()).matches(vid))
                .map(port -> {
                    final Obj buffer = this.getOrCreateBuffer(port);
                    if (buffer.isNoObj())
                        return noobj();
                    final Lst result = lst(new ArrayList<>(buffer.jvm()));
                    // buffer.jvm().clear();
                    return vid.isNode() ? result : rel(uri(this.pattern.retractPattern().extend(port.getSystemPortName())), result);
                }));
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return objs(Arrays.stream(SerialPort.getCommPorts())
                .filter(port -> this.pattern.retractPattern().extend(port.getSystemPortName()).matches(vid))
                .map(port -> this.buffers.get(port.getSystemPortName()))
                .filter(Objects::nonNull)
                .map(pair -> {
                    if (obj.isNoObj()) {
                        pair.get0().removeDataListener();
                        pair.get0().closePort();
                        this.buffers.remove(pair.get0().getSystemPortName());
                        LOG.info("closed port: %s", getPortMetadata(pair.get0()));
                        return noobj();
                    } else {
                        final OutputStream out = pair.get0().getOutputStream();
                        try {
                            out.write(obj.strValue().getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        } catch (final IOException e) {
                            throw MTronException.of(e);
                        }
                        return obj;
                    }
                }));
    }
}
