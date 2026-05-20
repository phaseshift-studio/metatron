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

package studio.phaseshift.metatron.isa.mach.type.router;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.noobjSpace;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.m.type.impl.MRec;
import studio.phaseshift.metatron.isa.mach.type.MStats;
import studio.phaseshift.metatron.isa.mach.type.Router;
import studio.phaseshift.metatron.isa.mach.type.Stats;

import java.util.Map;

import static studio.phaseshift.metatron.furi.fURI.Singleton.NOOBJ;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.mach.machInstSet.ROUTER_TID;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class NoObjRouter extends MRec implements Router {

    public static final NoObjRouter INSTANCE = new NoObjRouter();

    public static NoObjRouter single() {
        return INSTANCE;
    }

    private NoObjRouter() {
        super(Map.of(), ROUTER_TID, null);
    }
    
    @Override
    public void start() {

    }

    @Override
    public Object sjvm() {
        return Map.of();
    }

    @Override
    public Map<Uri, Uri> routes() {
        return Map.of();
    }

    @Override
    public Stats stats() {
        return new MStats();
    }

    @Override
    public Obj read(fURI vid) {
        return noobj();
    }

    @Override
    public Obj write(fURI vid, Obj obj) {
        return noobj();
    }

    @Override
    public boolean hasSpaceFor(fURI vid) {
        return false;
    }

    @Override
    public void addSpace(Space space) {

    }

    @Override
    public void removeSpace(fURI vid) {

    }

    @Override
    public <SPACE extends Space> SPACE getSpace(final fURI pattern) {
        return null;
    }

    @Override
    public void registerRedirect(fURI small, fURI big) {

    }

    @Override
    public void unregisterRedirect(fURI small, fURI big) {
        
    }

    @Override
    public void registerPrefix(fURI prefix, fURI vid) {
        
    }

    @Override
    public fURI redirect(fURI furi, boolean big) {
        return NOOBJ;
    }

    @Override
    public <SPACE extends Space> SPACE getSpaceFor(fURI vid) {
        return noobjSpace.single();
    }
}
