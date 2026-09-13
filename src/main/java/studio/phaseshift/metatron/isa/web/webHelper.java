/*
 * metatron: a distributed virtual machine and language
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

package studio.phaseshift.metatron.isa.web;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.type.Inst;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Rec;
import studio.phaseshift.metatron.isa.m.type.Type;
import studio.phaseshift.metatron.isa.m.type.Str;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRec;
import studio.phaseshift.metatron.isa.web.space.ws.WebSocketRecClient;
import studio.phaseshift.metatron.util.CommonUtil;
import studio.phaseshift.metatron.util.MTronException;

import java.util.ArrayList;
import java.util.List;

import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.m.mInstSet.M_ISA_INST_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.REC_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.web.webInstSet.PROTOCOL_TID;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.*;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.isa.sys.sysInstSet.SYS_INST_TID;
import static studio.phaseshift.metatron.isa.web.space.ws.wsSpace.WS_CLIENT_TID;
import static studio.phaseshift.metatron.isa.web.webInstSet.WEB_ISA_TID;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/*
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class webHelper {

    private webHelper() {
        // do nothing
    }

    /**
     * Does this route value address <b>per request</b> — i.e. does it have no boot-time target at all?
     * <p>
     * Only a templated uri does. It contains {@code ${…}} expressions, so it cannot be resolved before a
     * request exists: there is nothing to bind to. Every other value is resolvable once, and is deliberately
     * resolved <em>once</em> rather than per request — a {@code code} that materializes its target (for example
     * a route to {@code *dr.as(skill::T).as(mcp_server::T)}) must not re-materialize it on every request, and a
     * plain uri names a constant root. The distinction also decides addressing: a templated value has seen the
     * request path, so the uri it produces <em>is</em> the address; a plain uri is a prefix root that the mount
     * still extends with the request's remaining path.
     */
    public static boolean isTemplated(final Obj routeValue) {
        try {
            return routeValue.isUri() && routeValue.uriValue().hasTemplates();
        } catch (final Exception e) {
            return false;
        }
    }

    /**
     * Align a route value with the request it will serve: for a templated uri the request uri is the value's
     * lhs, so {@code ${name()}} and {@code ${as(rec::T).>>path/2}} mean "as this request asks". Every other
     * value is evaluated exactly as before — with no lhs ({@link Space.Helper#resolveApply}) — which is what
     * keeps an existing mount's resolution unchanged.
     */
    public static Obj align(final Space space, final Obj routeValue, final fURI requestUri) {
        if (null != requestUri && isTemplated(routeValue))
            return routeValue.apply(uri(requestUri));
        return Space.Helper.resolveApply(space, routeValue);
    }

    /**
     * Validate a route table against the route contract, returning an lst of problems — or {@code noobj} when
     * the table is clean.
     * <p>
     * Two rules, both learned from boot defects (see {@code docs/design/webspace.md}):
     * <ol>
     *   <li><b>shape</b> — the value must be a uri, a type or an instruction. The space constructor renders
     *   each value as a uri, so a value that cannot be rendered throws inside the constructor, is caught, and
     *   leaves the space with <em>no server</em>: every request then times out rather than failing.</li>
     *   <li><b>signature</b> — an instruction value must take a uri and yield a protocol surface
     *   ({@code dom=uri}, {@code rng isa protocol}).</li>
     * </ol>
     * A third is reported for the <em>key</em>: a pattern key ({@code /people/#}) is matched literally by the
     * carrier, so it mounts a context its own subtree cannot reach — silently, since the carrier accepts the key
     * happily. Resolvability is deliberately <em>not</em> checked: a web-root mount legitimately names a prefix to
     * be extended (e.g. {@code docker:} or {@code mfs:docs/website/}) rather than a leaf that reads back.
     */
    public static Obj routeProblems(final Rec routes) {
        final List<Obj> problems = new ArrayList<>();
        if (null == routes || routes.isNoObj())
            return noobj();
        routes.elements().forEach(r -> {
            final fURI path = r.first().uriValue();
            final Obj value = r.second();
            // A pattern key is the one problem whose symptom is pure silence: the carrier matches its context by
            // literal prefix, so `/people/#` mounts a context that `/people/34` cannot reach, and neither the
            // carrier nor the mount loop complains. See docs/design/webspace.md §8.1.5.
            if (path.hasPattern())
                problems.add(str("%s: a pattern key is matched literally by the carrier, so it will not match the paths it describes (%s)".formatted(path, value.isNoObj() ? "" : value.toShortString()).trim()));
            if (value.isUri() || value.isType())
                return;
            if (value.isObjInst()) {
                final Inst inst = value.asInst();
                if (!inst.dom().test(T(URI_TID)))
                    problems.add(str("%s: an instruction must take a uri, got dom %s".formatted(path, inst.dom())));
                else if (!isProtocolSurface(inst.rng()))
                    problems.add(str("%s: an instruction must yield a protocol surface, got rng %s".formatted(path, inst.rng())));
                return;
            }
            if (value.isCode())
                return; // a code chain's signature is resolved when it is applied, not declared
            problems.add(str("%s: value must be a uri, a type or an instruction, got %s".formatted(path, value.toShortString())));
        });
        return problems.isEmpty() ? noobj() : lst(problems);
    }

    /**
     * Whether a type is a protocol surface — i.e. a protocol type appears in its <b>declared</b> parent chain.
     * <p>
     * This asks the question for a <b>type</b>. {@code protocol::T} is a union of the protocol types, so its
     * predicate classifies the objs on its lhs: an {@code mcp_server} <em>value</em> tests as
     * {@code protocol::T}, while the <em>type</em> {@code rest::T} does not — a {@code Type} is not a protocol
     * value. The declared chain answers for types: {@code rest::T → http::T → protocol::T} is true, and
     * {@code rec::T} is a base type with an empty chain, so it is false (where a bare {@code test} against a
     * nominal umbrella would have allowed it via {@code testNominally}'s re-tag branch).
     */
    public static boolean isProtocolSurface(final Type type) {
        Type current = type;
        while (null != current && !current.isBaseType() && !current.isGeneric() && !current.isRootType()) {
            if (null != current.vid() && current.vid().test(PROTOCOL_TID))
                return true;
            current = current.parentType();
        }
        return false;
    }

    public static Inst remoteConsole() {
        return docWrap(instC(M_ISA_INST_TID, lst(), (lhs, inst) -> {
            try {
                Console.LOCAL_INSTANCE.logger().none("select location for remote console: ");
                final Str location = instB(SYS_INST_TID.extend("stdin"), lst()).apply().as();
                fURI locationUri = f("").authority(location.strValue());
                locationUri = locationUri.hasScheme() ? locationUri : locationUri.scheme("ws");
                locationUri = locationUri.segmentLength() > 0 ? locationUri : locationUri.segments(List.of("mtron"));
                Console.LOCAL_INSTANCE.logger().none("\nsetting up remote console for {{g}}%s{{X}}\n", uri(locationUri));
                final WebSocketRecClient client = new WebSocketRecClient(new WebSocketRec(mutableMap(uri(HOST), locationUri.toUri()), WS_CLIENT_TID, CommonUtil.mintShortUUID(f("/sys/web/ws"), true)));
                Console.LOCAL_INSTANCE.input = instLambda((lhs2, inst2) -> client.jvm().get(uri("send_recv")).apply(inst2.arg(0)));
                return location;
            } catch (final Exception e) {
                throw MTronException.of(e);
            }
        }), "creates a remote console connection");
    }
}
