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

package studio.phaseshift.metatron.ui;

import org.slf4j.event.Level;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.lang.sys.router.Router;
import studio.phaseshift.metatron.lang.core.m.type.Obj;
import studio.phaseshift.metatron.lang.util.logObj;

import static org.slf4j.event.Level.*;
import static studio.phaseshift.metatron.BootLoader.BOOTING;


public class GraphittyObjLogger extends GraphittyLogger {

    private static fURI LOG_VID = fURI.NOOBJ;

    public GraphittyObjLogger(final Obj source) {
        super(source);
    }

    public static void setLogger(final fURI logvid) {
        LOG_VID = logvid;
    }


    private boolean doLog(final Level level) {
        if (!Router.loaded())
            return true;
        else {
            if (!LOG_VID.isZero()) {
                final Obj o = Router.global().read(LOG_VID);
                if (o.isNoObj()) {
                    if (!BOOTING)
                        this.none("no space embedded logger found at %s\n", LOG_VID.toUri());
                    return true;
                } else
                    return null == this.source || logObj.from(o.as()).check(level, ((Obj) this.source).vidOrTid());
            } else
                return level.compareTo(logObj.getSLF4J()) <= 0;
        }
        //   } catch(final Exception e) {
        //return false;
        // }
    }

    @Override
    public GraphittyObjLogger info(final Object f, final Object... args) {
        if (doLog(INFO)) super.logLevel(INFO, f, args);
        return this;
    }

    @Override
    public GraphittyObjLogger error(final Object f, final Object... args) {
        if (doLog(ERROR)) super.logLevel(ERROR, f, args);
        return this;
    }


    @Override
    public GraphittyObjLogger warn(final Object f, final Object... args) {
        if (doLog(WARN)) super.logLevel(WARN, f, args);
        return this;
    }


    @Override
    public GraphittyObjLogger debug(final Object f, final Object... args) {
        if (doLog(DEBUG)) super.logLevel(DEBUG, f, args);
        return this;
    }

    @Override
    public GraphittyObjLogger trace(final Object f, final Object... args) {
        if (doLog(TRACE)) super.logLevel(TRACE, f, args);
        return this;
    }


}