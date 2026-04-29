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

package studio.phaseshift.metatron.isa.mach.type.ui.console;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.Status;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.TypeCheck;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Call;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.reflect.TypedRec;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.slf4j.event.Level.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.STR_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Poly.MUTABLE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.m.type.reflect.TypedRec.typedRec;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class StatusLine implements Runnable {

    private List<AttributedString> line = new ArrayList<>();
    private Level state = INFO;
    private long startTime = 0;
    private long lastExecutionTime = 0;
    private final Status status;
    private final TypedRec<Uri, Call> widgets = typedRec();

    public StatusLine(final Console console) {
        this.line = new ArrayList<>();
        this.status = Status.getStatus(Console.getTerminal());
        this.addWidget(f("type_check_"), () -> "{{w&[%s]}} T {{X}}".formatted(TypeCheck.colorLevel()));
        //this.addWidget(f("spaces"), () -> "{{w}}spaces:{{y}}%d".formatted(Router.global().spaces().count()));
        //this.addWidget(f("nodes"), () -> "{{w}}nodes:{{y}}%d".formatted(Router.global().server().nodes().size()));
        this.addWidget(f("in_bytes"), () -> "{{w}}in:{{y}}%s".formatted(bytesFormat(Router.global().stats().ioStats().bytesRecv())));
        this.addWidget(f("out_bytes"), () -> "{{w}}out:{{y}}%s".formatted(bytesFormat(Router.global().stats().ioStats().bytesSent())));
        this.addWidget(f("time"), () -> "{{w}}time:{{y}}%s".formatted(timeFormat(this.runningTime())));
        this.addWidget(f("run"), () -> "{{w}}run:{{y}}%d".formatted(Router.global().stats().monadicStats().runningMonads()));
        this.addWidget(f("halt"), () -> "{{w}}halt:{{y}}%d".formatted(Router.global().stats().monadicStats().haltedMonads()));
        this.addWidget(f("kill"), () -> "{{w}}kill:{{y}}%d".formatted(Router.global().stats().monadicStats().killedMonads()));
        this.addWidget(f("barrier"), () -> "{{w}}barrier:{{y}}%d".formatted(Router.global().stats().monadicStats().barrierMonads()));
        this.addWidget(f("ws"), () -> "{{w}}ws:{{w&[g]}}[%d]{{[%s]}} %s".formatted(Router.global().stats().ioStats().connections(), this.getColor(), formatMessage(Router.global().stats().ioStats().lastMessage())));
    }

    private String formatMessage(final String message) {
        String newMessage = Graphitty.strip(message.trim());
        while (newMessage.startsWith("\""))
            newMessage = newMessage.substring(1);
        while (newMessage.endsWith("\""))
            newMessage = newMessage.substring(0, newMessage.length() - 1);
        newMessage = newMessage.replace("\\n", "\\").trim();
        while (newMessage.endsWith("\\"))
            newMessage = newMessage.substring(0, newMessage.length() - 1);
        return newMessage;
    }


    public void addWidget(final fURI name, final Supplier<String> widget) {
        this.widgets.at(uri(name), instC(name.prepend("status.").dom(ALL.maybe()).rng(STR_TID), lst(), (lhs, inst) -> str(widget.get())), MUTABLE);
    }

    private void compileWidgets() {
        final String color = this.getColor();
        this.line.clear();
        boolean capped = false;
        for (final Map.Entry<Uri, Call> ws : this.widgets.jvmTyped().entrySet()) {
            final String w = ws.getValue().apply(noobj()).strValue();
            final String cap;
            if (capped || ws.getKey().uriValue().toString().endsWith("_")) {
                cap = "";
                capped = true;
            } else {
                cap = "| ";
            }
            if (!ws.getKey().uriValue().toString().endsWith("_"))
                capped = false;
            this.line.add(new AttributedString(Graphitty.string("{{g&[%s]}}%s%s{{[%s]}} ", color, cap, w, color)));
        }
        this.line.add(new AttributedString(Graphitty.string("{{g}}{{[" + color + "]}}%s.".formatted(" ".repeat(Console.getTerminal().getWidth())))));

        final AttributedStringBuilder builder = new AttributedStringBuilder();
        for (final AttributedString s : this.line) {
            builder.appendAnsi(s.toAnsi());
        }
        this.line.clear();
        this.line.add(builder.toAttributedString());
    }

    private String getColor() {
        final String color;
        if (this.state.equals(WARN))
            color = "y";
        else if (this.state.equals(ERROR))
            color = "r";
        else
            color = "b";
        return color;
    }

    public void refresh() {
        this.status.update(List.of());
        this.status.update(this.line);
    }

    public void startTimer() {
        this.startTime = System.currentTimeMillis();
    }

    public void stopTimer() {
        this.lastExecutionTime = System.currentTimeMillis() - this.startTime;
        this.startTime = 0;
    }

    private long runningTime() {
        if (0 == this.startTime) {
            return this.lastExecutionTime;
        } else {
            return System.currentTimeMillis() - this.startTime;
        }
    }

    public void setState(final Level state) {
        this.state = state;
    }

    public Level getState() {
        return this.state;
    }

    private static String bytesFormat(final long bytes) {
        if (bytes < 1024)
            return bytes + "B";
        else if (bytes < 1024 * 1024)
            return String.format("%.2fkB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2fmB", bytes / (1024.0 * 1024.0));
        else if (bytes < 1024L * 1024L * 1024L * 1024L)
            return String.format("%.2fgB", bytes / (1024.0 * 1024.0 * 1024.0));
        else if (bytes < 1024L * 1024L * 1024L * 1024L * 1024L)
            return String.format("%.2ftB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
        else
            return String.format("%.2fpB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0 * 1024.0));
    }

    private static String timeFormat(final long millis) {
        if (millis < 1000)
            return String.format("%dms", millis);
        else if (millis < 60000)
            return String.format("%.2fs", millis / 1000.0);
        else if (millis < 3600000)
            return String.format("%.2fmin", millis / (60000.0));
        else if (millis < 86400000)
            return String.format("%.2fhr", millis / (3600000.0));
        else
            return String.format("%.2fd", millis / (86400000.0));
    }

    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            final boolean serverRunning = Router.loaded();
            if (!serverRunning)
                this.setState(ERROR);
            /// ////////////////////////////////////////////////
            if (Router.loaded()) {
                this.compileWidgets();
                this.status.update(this.line);
            }
            try {
                CommonUtil.sleepThread(250);
            } catch (final MTronException e) {
                // do nothing
            }
        }
        this.status.close();
    }
}
