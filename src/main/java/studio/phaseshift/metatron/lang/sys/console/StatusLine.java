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

package studio.phaseshift.metatron.lang.sys.console;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.Status;
import org.slf4j.event.Level;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.ui.graphitty.Graphitty;
import studio.phaseshift.metatron.util.Common;
import studio.phaseshift.metatron.util.MTronException;

import java.util.List;

import static org.slf4j.event.Level.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class StatusLine implements Runnable {

    private AttributedString line;
    private Level state = INFO;
    private long startTime = 0;
    private long lastExecutionTime = 0;
    private final Status status;

    public StatusLine(final Console console, final String line) {
        this.line = new AttributedStringBuilder().append(line).toAttributedString();
        this.status = Status.getStatus(console.getTerminal());
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

    public void refresh() {
        this.status.update(List.of());
        this.status.update(List.of(this.line));
    }

    private static String bytesFormat(final long bytes) {
        if (bytes < 1024)
            return bytes + "B";
        else if (bytes < 1024 * 1024)
            return String.format("%.2fK", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2fM", bytes / (1024.0 * 1024.0));
        else if (bytes < 1024L * 1024L * 1024L * 1024L)
            return String.format("%.2fG", bytes / (1024.0 * 1024.0 * 1024.0));
        else
            return String.format("%.2fT", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
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
            final boolean serverRunning = Router.global().server().isRunning();
            if (!serverRunning)
                this.setState(ERROR);
            /// //////////////////////////////////////
            final String color;
            if (this.state.equals(WARN))
                color = "y";
            else if (this.state.equals(ERROR))
                color = "r";
            else
                color = "b";
            if (Router.loaded()) {
                final AttributedString temp = new AttributedStringBuilder()
                        .ansiAppend(Graphitty.string("{{[" + color + "]&y}} %s", serverRunning ? Router.global().server().host() : "<server down>"))
                        .ansiAppend(Graphitty.string("{{g}}|{{w}}nodes:{{y}}%d{{[" + color + "]&w}}", Router.global().server().nodes().size()))
                        .ansiAppend(Graphitty.string("{{g}}|{{w}}in:{{y}}%s{{[" + color + "]}}", bytesFormat(Router.global().server().stats().getBytesRecv())))
                        .ansiAppend(Graphitty.string("{{g}}|{{w}}out:{{y}}%s{{[" + color + "]}}", bytesFormat(Router.global().server().stats().getBytesSent())))
                        .ansiAppend(Graphitty.string("{{g}}|{{w}}running time:{{y}}%s{{[" + color + "]}}", timeFormat(this.runningTime())))
                        .append(Graphitty.string("{{g}}|{{[" + color + "]}}%s.", " ".repeat(200)))
                        .toAttributedString();
                if (!this.line.equals(temp)) {
                    this.line = temp;
                    this.status.update(List.of(this.line));
                }
            }
            try {
                Common.sleepThread(250);
            } catch (final MTronException e) {
                // do nothing
            }
        }
        this.status.close();
    }
}
