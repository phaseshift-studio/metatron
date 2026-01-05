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
import studio.phaseshift.metatron.util.Threadable;

import java.util.List;

import static org.slf4j.event.Level.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class StatusLine implements Threadable, Runnable {

    private AttributedString line;
    private Level state = INFO;
    private long startTime = 0;
    private long lastExecutionTime = 0;
    private final Status status;
    private final Thread thread;


    public StatusLine(final Console console, final String line) {
        this.line = new AttributedStringBuilder().append(line).toAttributedString();
        this.status = Status.getStatus(console.getTerminal());
        this.thread = new Thread(this);
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
            final long time = System.currentTimeMillis() - this.startTime;
            return time;
        }
    }

    public void setLastExecutionTime(final long lastExecutionTime) {
        this.lastExecutionTime = lastExecutionTime;
    }

    public void setState(final Level state) {
        this.state = state;
    }

    public void refresh() {
        this.status.reset();
        this.status.suspend();
        this.status.update(List.of());
        this.status.update(List.of(this.line));
        this.status.restore();
        this.status.redraw();
    }

    public void run() {
        while (!this.thread.isInterrupted()) {
            final String color;
            if (this.state.equals(WARN))
                color = "y";
            else if (this.state.equals(ERROR))
                color = "r";
            else
                color = "b";
            if (Router.loaded()) {
                final AttributedString temp = new AttributedStringBuilder()
                        .ansiAppend(Graphitty.string("{{[" + color + "]&y}} %s", Router.global().server().host()))
                        .ansiAppend(Graphitty.string("{{g}}|{{w}}nodes:{{y}}%d{{[" + color + "]&w}}", Router.global().server().nodes().size()))
                        .ansiAppend(Graphitty.string("{{g}}|{{w}}in:{{y}}%d{{[" + color + "]&w}}", Router.global().server().stats().getBytesRecv()))
                        .ansiAppend(Graphitty.string("{{g}}|{{w}}out:{{y}}%d{{[" + color + "]&w}}", Router.global().server().stats().getBytesSent()))
                        .ansiAppend(Graphitty.string("{{g}}|{{w}}running time (ms):{{y}}%,d{{[" + color + "]&w}}", this.runningTime()))
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

    @Override
    public Thread getThread() {
        return this.thread;
    }
}
