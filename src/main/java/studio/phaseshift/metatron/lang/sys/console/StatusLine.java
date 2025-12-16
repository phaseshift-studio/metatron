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
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.util.Threadable;

import java.util.List;

import static org.slf4j.event.Level.*;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class StatusLine implements Threadable, Runnable {

    private AttributedString line;
    private Level state = INFO;
    private final Status status;
    private final Thread thread;

    public StatusLine(final Console console, final String line) {
        this.line = new AttributedStringBuilder().append(line).toAttributedString();
        this.status = Status.getStatus(console.getTerminal());
        this.thread = new Thread(this);
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
                        .ansiAppend(Graphitty.string("{{-X-&[" + color + "]&w}} %s", Router.global().server().host()))
                        .ansiAppend(Graphitty.string(" [connections: {{M}}%d{{[" + color + "]&w}}]", Router.global().server().nodes().size()))
                        .ansiAppend(Graphitty.string(" [bytes >: {{M}}%d{{[" + color + "]&w}}]", Router.global().server().stats().getBytesSent()))
                        .ansiAppend(Graphitty.string(" [bytes <: {{M}}%d{{[" + color + "]&w}}]", Router.global().server().stats().getBytesReceived()))
                        .append(Graphitty.string("{{[" + color + "]}}%s", " ".repeat(200)))
                        .toAttributedString();
                if (!this.line.equals(temp)) {
                    this.line = temp;
                    this.status.update(List.of(this.line));
                }
            }
        }
        this.status.close();
    }

    @Override
    public Thread getThread() {
        return this.thread;
    }
}
