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

package studio.phaseshift.metatron.isa.m.type.impl;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Uri;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;
import studio.phaseshift.metatron.util.Tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;

public class MUri extends MObj implements Uri {

    private static final Uri EMPTY_URI = new MUri(f(""), URI_TID, null);

    /**
     * Memoized parsed template expressions.
     * null = not yet parsed, empty list = no templates or already parsed with no templates
     */
    private volatile List<Tuple.Pair<fURI.Component, Obj>> parsedTemplatesCache = null;

    public MUri(final fURI jvm, final fURI tid, final fURI vid) {
        super(jvm.resolve(), tid, vid);
        if (jvm.isZero())
            this.tid = this.tid.zero();
    }

    @Override
    public List<Tuple.Pair<fURI.Component, Obj>> parsedTemplates() {
        // Double-checked locking for thread-safe memoization
        if (parsedTemplatesCache == null) {
            synchronized (this) {
                if (parsedTemplatesCache == null) {
                    parsedTemplatesCache = parseTemplateExpressions();
                }
            }
        }
        return parsedTemplatesCache;
    }

    /**
     * Parse all template expression strings to Obj instances.
     * Called once and memoized.
     */
    private List<Tuple.Pair<fURI.Component, Obj>> parseTemplateExpressions() {
        final fURI furi = this.jvm();
        if (!furi.hasTemplates()) {
            return List.of();
        }

        final List<Tuple.Pair<fURI.Component, String>> templates = furi.templates();
        final List<Tuple.Pair<fURI.Component, Obj>> result = new ArrayList<>(templates.size());

        for (final Tuple.Pair<fURI.Component, String> template : templates) {
            final fURI.Component component = template.get0();
            final String exprStr = template.get1();

            try {
                // Parse the expression using mParser
                final Obj expr = ObjmtronSerializer.parse(exprStr);
                result.add(Tuple.Pair.with(component, expr));
            } catch (Exception e) {
                throw MTronException.of("Failed to parse template expression '${%s}': %s", exprStr, e.getMessage());
            }
        }

        return result;
    }

    public static Uri uri(final String jvm) {
        return uri(f(jvm), URI_TID,null);
    }

    public static Uri uri() {
        return EMPTY_URI;
    }

    public static Uri uri(final fURI jvm) {
        return uri(jvm, URI_TID,null);
    }

    public static Uri uri(final fURI jvm, final fURI tid) {
        return uri(jvm, tid,null);
    }

    public static Uri uri(final fURI jvm, final fURI tid, final fURI vid) {
        return null == tid ? new MUri(jvm, URI_TID, vid) : new MUri(jvm, tid, vid);
    }

    public static Uri uri(final String jvm, final fURI tid) {
        return uri(f(jvm), tid,null);
    }

    @Override
    public Uri clone(final Object jvm, final fURI tid, final fURI vid) {
        MUri clone = super.clone(jvm, tid, vid);
        if (clone.jvm().isZero())
            clone.tid = clone.tid.zero();
        // Reset parsed templates cache if jvm changed (different fURI means different templates)
        if (!Objects.equals(jvm, this.jvm)) {
            clone.parsedTemplatesCache = null;
        }
        return clone;
    }

    @Override
    public fURI jvm() {
        return (fURI) this.jvm;
    }
}


