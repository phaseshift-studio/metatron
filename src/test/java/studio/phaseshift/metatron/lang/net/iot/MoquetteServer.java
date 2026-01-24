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

package studio.phaseshift.metatron.lang.net.iot;

/*
 * Copyright (c) 2012-2018 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

import io.moquette.broker.Server;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.util.MTronException;

/**
 * Simple example of how to embed the broker in another project
 *
 */
public final class MoquetteServer {

    private static Server mqttBroker;

    public static void stop() {
        if (mqttBroker != null)
            mqttBroker.stopServer();

    }

    public static void clear() {

    }

    public static void run() {
        try {
            mqttBroker = new Server();
            mqttBroker = mqttBroker.withConfig().disablePersistence().port(1882).startServer();
            Router.global().logger().info("mqtt broker started press [CTRL+C] to stop");
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    private MoquetteServer() {
        // do nothing
    }
}