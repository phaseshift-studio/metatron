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

package studio.phaseshift.metatron.isa.mach.io.space.serial;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import org.jline.utils.AttributedString;
import studio.phaseshift.metatron.Tokens;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.mach.io.type.ObjByteBufferSerializer;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
import static com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.isa_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MFail.fail;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.mach.machInstSet.MACH_ISA_TID;
import static studio.phaseshift.metatron.isa.mach.machInstSet.SPACE_CONFIG;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class serialSpace extends AbstractSpace<SerialPort[]> {

    public static final fURI SERIAL_SPACE_TID = MACH_ISA_TID.extend("space/serial");

    public static final Type SERIAL_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(SERIAL_SPACE_TID)
            .constructor(
                    instC(INST_TID.dom(ALL.maybe()).rng(SERIAL_SPACE_TID),
                            lst(isa_(SPACE_CONFIG.plus(rec(uri(Tokens.ROUTE), REC_TYPE))).tryToInst()),
                            (lhs, inst) -> serialSpace.of(inst.arg(0).asRec(), inst.arg(0).vid()))).create();

    private final Map<String, Tuple.Pair<SerialPort, ByteArrayOutputStream>> buffers = new HashMap<>();
    protected static final byte[] CARRIAGE_RETURN = {(byte) 0x0D}; // Carriage return (CR)

    public static serialSpace of(final Rec config, final fURI vid) {
        return new serialSpace(SerialPort.getCommPorts(), config.jvm(), vid);
    }

    protected serialSpace(final SerialPort[] ports, final Map<Obj, Obj> config, final fURI vid) {
        super(ports, config, SERIAL_SPACE_TID, vid);
    }

    protected String getPortMetadata(final SerialPort port) {
        return String.format("{{b}}%s{{g}}@{{y}}%d{{X}} baud {{g}}[{{b}}%s{{g}}][{{b}}%s{{g}}]{{X}}", port.getSystemPortName(), port.getBaudRate(), port.getDescriptivePortName(), port.getManufacturer());
    }

    protected ByteArrayOutputStream getOrCreateBuffer(final SerialPort port) {
        if (!this.buffers.containsKey(port.getSystemPortName())) {
            final ByteArrayOutputStream sb = new ByteArrayOutputStream();
            this.buffers.put(port.getSystemPortName(), Tuple.Pair.with(port, sb));
            port.setBaudRate(115200);
            port.addDataListener((new SerialPortDataListener() {
                @Override
                public int getListeningEvents() {
                    return LISTENING_EVENT_DATA_AVAILABLE | LISTENING_EVENT_PORT_DISCONNECTED;
                }

                @Override
                public void serialEvent(final SerialPortEvent event) {
                    //LOG.info("serial event: %s", event);
                    try {
                        if ((event.getEventType() & LISTENING_EVENT_DATA_AVAILABLE) > 0) {
                            byte[] newData = new byte[port.bytesAvailable()];
                            int totalBytesRead = port.readBytes(newData, newData.length);
                            LOG.debug("read %d bytes from %s", totalBytesRead, port.getSystemPortName());
                            sb.write(newData);
                        } else if ((event.getEventType() & LISTENING_EVENT_PORT_DISCONNECTED) > 0) {
                            LOG.warn("port disconnected: %s", getPortMetadata(port));
                            port.closePort();
                            buffers.remove(port.getSystemPortName());
                        }
                    } catch (final Exception e) {
                        try {
                            sb.write(new ObjByteBufferSerializer().outputBytes(fail(e)).array());
                        } catch (IOException ioException) {
                            throw MTronException.of(ioException);
                        }
                    }
                }
            }));
            if (!port.openPort()) {
                LOG.error("failed to open port: %s", getPortMetadata(port));
                this.buffers.remove(port.getSystemPortName());
                return null;
            } else {
                LOG.info("opened port: %s", getPortMetadata(port));
                return this.buffers.get(port.getSystemPortName()).get1();
            }
        }
        return this.buffers.get(port.getSystemPortName()).get1();
    }

    @Override
    public Obj read(final fURI vid) {
        return objs(Arrays.stream(SerialPort.getCommPorts())
                .filter(port -> this.pattern.retractPattern().extend(port.getSystemPortName()).test(vid))
                .map(port -> {
                    final ByteArrayOutputStream buffer = this.getOrCreateBuffer(port);
                    if (null == buffer)
                        return noobj();

                    final String current = buffer.size() == 0 ? "" : buffer.toString(StandardCharsets.UTF_8);
                    final String stripped = AttributedString.stripAnsi(current);
                    final Str result = str(stripped);
                    return vid.isNode() ? result : rel(uri(this.pattern.retractPattern().extend(port.getSystemPortName())), result);
                }));
    }

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        return objs(Arrays.stream(SerialPort.getCommPorts())
                .filter(port -> this.pattern.retractPattern().extend(port.getSystemPortName()).test(vid))
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
                        final String string = obj.strValue();
                        final byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
                        if (-1 == pair.get0().writeBytes(bytes, bytes.length)) {
                            throw MTronException.of("failed to write %s bytes to %s", bytes.length, getPortMetadata(pair.get0()));
                        }
                        //if (string.endsWith("\n") || string.endsWith("\r")) {
                        pair.get0().writeBytes(CARRIAGE_RETURN, CARRIAGE_RETURN.length);
                        //}
                        LOG.debug("wrote %d bytes to %s", bytes.length, getPortMetadata(pair.get0()));
                        return obj;
                    }
                }));
    }
}
