/*
 * Metatron: A Distributed Computing Language and Virtual Machine
 * Copyright (C) 2025- PhaseShift Studio, LLC
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

package studio.phaseshift.metatron.ui.mode.server;

import studio.phaseshift.metatron.BootLoader;
import studio.phaseshift.metatron.lang.mtron.type.Rec;
import studio.phaseshift.metatron.ui.Graphitty;
import studio.phaseshift.metatron.ui.GraphittyLogger;
import studio.phaseshift.metatron.ui.Mode;
import studio.phaseshift.metatron.util.MTronException;

import java.io.IOException;
import java.util.Optional;
import java.util.Scanner;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class Server implements Mode {

    public static String HEADER_FILE = "./conf/metatron-character.ans";
    private Thread mainThread;
    private GraphittyLogger LOG;

    protected Server(final Rec options) {
        LOG = Graphitty.log(this);
    }

    public static Server of(Rec options) {
        return new Server(options);
    }

    public void run() throws IOException {
        LOG.none("""
                
                                \s
                {{m}}                __          __                                                                \s
                  _____   _____/  |______ _/  |________  ____   ____     ______ ______________  __ ___________\s
                 /     \\_/ __ \\   __\\__  \\\\   __\\_  __ \\/  _ \\ /  {{c}}  \\   /  ___// __ \\_  __ \\  \\/ // __ \\_  __ \\
                |  Y Y  \\  ___/|  |  / __ \\|  |  |  | \\(  <_> )   |  \\  \\___ \\\\  ___/|  | \\/\\   /\\  ___/|  | \\/
                |__|_|  /\\___  >__| (____  /__|  |__|   \\____/|___|  {{b}}/ /____  >\\___  >__|    \\_/  \\___  >__|  \s
                      \\/     \\/          \\/                        \\/       \\/     \\/                 \\/      \s{{X}}
                \n\t{{y}}:quit{{/y}} to shutdown server\n\n
                """);
        Mode.waitForBoot();
       /* final BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(HEADER_FILE)));
        input.lines().forEach(line -> {
            LOG.none("%s\n", line);
        });*/
        try {
            final Scanner userInput = new Scanner(System.in);
            while (true) {
                LOG.none("{{m}}mtron{{g}}>{{X}} ");
                final String line = userInput.nextLine();
                if (line.trim().equals(":quit"))
                    break;
            }
        } catch (final Exception e) {
            LOG.error("unable to read user input (failback to ctrl-c to shutdown server): %s", e);
            MTronException.wrap(() -> Thread.currentThread().join());
        }
        BootLoader.close();

    }

    public void start() {
        final Runnable server = () -> {
            try {
                this.run();
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        };
        this.mainThread = new Thread(server);
        this.mainThread.start();
    }

    @Override
    public void stop() {

    }

    @Override
    public Optional<Thread> mainThread() {
        return Optional.of(this.mainThread);
    }


}
