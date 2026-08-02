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

package studio.phaseshift.metatron.isa.dckr;

import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.AbstractInstSet;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.m.type.Type;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.q.QCollection.docWrap;
import static studio.phaseshift.metatron.isa.dckr.dckrSpace.DCKR_SPACE_TYPE;
import static studio.phaseshift.metatron.isa.m.mInstSet.*;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_DATA_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.inside_;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;

/**
 * Instruction set for Docker integration.
 * <p>
 * Type hierarchy:
 * <pre>
 *   rec::T
 *    ├─ docker_resource::T   (id, created)
 *    │    ├─ docker_image::T       (repo_tags, size)
 *    │    ├─ docker_container::T   (image, state, ports)
 *    │    ├─ docker_volume::T      (driver, mountpoint)
 *    │    └─ docker_network::T     (driver, scope)
 *    └─ docker_compose::T    (services)
 * </pre>
 * Types are passive data containers — they validate structure without
 * triggering Docker operations.  Execution is driven by writing to the
 * dockerSpace, which intercepts compose/container URIs.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@InstSet.JREService(vid = "/m/dckr")
public class dckrInstSet extends AbstractInstSet {

    public static final fURI DCKR_ISA_TID = M_ISA_TID.extend("dckr");
    public static final fURI DOCKER_INST_TID = DCKR_ISA_TID.extend("inst");

    public static final fURI DOCKER_RESOURCE_TID = DCKR_ISA_TID.extend("resource");
    public static final fURI DOCKER_IMAGE_TID = DCKR_ISA_TID.extend("image");
    public static final fURI DOCKER_CONTAINER_TID = DCKR_ISA_TID.extend("container");
    public static final fURI DOCKER_COMPOSE_TID = DCKR_ISA_TID.extend("compose");
    public static final fURI DOCKER_CONTAINER_STATE_TID = DCKR_ISA_TID.extend("container_state");
    public static final fURI DOCKER_VOLUME_TID = DCKR_ISA_TID.extend("volume");
    public static final fURI DOCKER_NETWORK_TID = DCKR_ISA_TID.extend("network");

    public static final String DOCKER_IMAGE_TID_STRING = "/m/dckr/image";
    public static final String DOCKER_CONTAINER_TID_STRING = "/m/dckr/container";
    public static final String DOCKER_COMPOSE_TID_STRING = "/m/dckr/compose";

    static {
        assert DOCKER_IMAGE_TID_STRING.equals(DOCKER_IMAGE_TID.toString());
        assert DOCKER_CONTAINER_TID_STRING.equals(DOCKER_CONTAINER_TID.toString());
        assert DOCKER_COMPOSE_TID_STRING.equals(DOCKER_COMPOSE_TID.toString());
    }

    public static Type DOCKER_RESOURCE_TYPE;
    public static Type DOCKER_IMAGE_TYPE;
    public static Type DOCKER_CONTAINER_TYPE;
    public static Type DOCKER_CONTAINER_STATE_TYPE;
    public static Type DOCKER_COMPOSE_TYPE;
    public static Type DOCKER_VOLUME_TYPE;
    public static Type DOCKER_NETWORK_TYPE;

    public dckrInstSet() {
        super(mutableMap(uri(PATTERN), uri(DCKR_ISA_TID.extend(HASH_FURI))), INSTSET_TID, DCKR_ISA_TID);
    }

    public void setup() {
        this.jvm().putAll(mutableMap(
                uri(PATTERN), uri(DCKR_ISA_TID.extend(ALL)),
                uri(TYPE), lst(
                        docWrap(DCKR_SPACE_TYPE,
                                "a docker daemon space",
                                "docker:compose/my-stack -> [services=>[web=>[image=>\"nginx\"]]]",
                                "*docker:container/+",
                                "*docker:image/nginx/<nginx:latest>"),
                        docWrap(DOCKER_RESOURCE_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(DOCKER_RESOURCE_TID)
                                        .isaPredicate(rec(
                                                uri(ID), URI_TYPE,
                                                uri("created").maybe(), URI_TYPE))
                                        .create(),
                                "base type for docker resources — id + created"),
                        docWrap(DOCKER_IMAGE_TYPE = Type.Builder.build()
                                        .tid(DOCKER_RESOURCE_TID)
                                        .vid(DOCKER_IMAGE_TID)
                                        .isaPredicate(rec(
                                                (Obj) uri("repo_tags").maybe(), lst(URI_TYPE),
                                                uri(SIZE).maybe(), T(MATH_DATA_TID)))
                                        .create(),
                                "a docker image — refines docker_resource::T",
                                "[id => <sha256:abc123>, repo_tags => [<nginx:latest>], size => mB::142.0]",
                                "matches docker_image::T"),
                        docWrap(DOCKER_CONTAINER_STATE_TYPE = Type.Builder.build()
                                        .tid(URI_TID)
                                        .vid(DOCKER_CONTAINER_STATE_TID)
                                        .isaPredicate(inside_(lst(
                                                uri("created"), uri("running"), uri("paused"),
                                                uri("restarting"), uri("exited"), uri("dead"))))
                                        .create(),
                                "docker container state — union of created|running|paused|restarting|exited|dead",
                                "running.matches(docker_container_state::T)     [-- true --]",
                                "stopped.matches(docker_container_state::T)     [-- false --]"),
                        docWrap(DOCKER_CONTAINER_TYPE = Type.Builder.build()
                                        .tid(DOCKER_RESOURCE_TID)
                                        .vid(DOCKER_CONTAINER_TID)
                                        .isaPredicate(rec(
                                                uri("image"), URI_TYPE,
                                                uri(STATE).maybe(), T(DOCKER_CONTAINER_STATE_TID),
                                                uri("ports").maybe(), lst(URI_TYPE)))
                                        .create(),
                                "a docker container — refines docker_resource::T",
                                "[id => <a1b2c3>, image => <nginx:latest>, state => running, ports => [<8080:80>]]",
                                "matches docker_container::T"),
                        docWrap(DOCKER_VOLUME_TYPE = Type.Builder.build()
                                        .tid(DOCKER_RESOURCE_TID)
                                        .vid(DOCKER_VOLUME_TID)
                                        .isaPredicate(rec(
                                                (Obj) uri(DRIVER).maybe(), URI_TYPE,
                                                uri("mountpoint").maybe(), URI_TYPE))
                                        .create(),
                                "a docker volume — refines docker_resource::T"),
                        docWrap(
                                DOCKER_NETWORK_TYPE = Type.Builder.build()
                                        .tid(DOCKER_RESOURCE_TID)
                                        .vid(DOCKER_NETWORK_TID)
                                        .isaPredicate(rec(
                                                (Obj) uri(DRIVER).maybe(), URI_TYPE,
                                                uri("scope").maybe(), URI_TYPE))
                                        .create(),
                                "a docker network — refines docker_resource::T"),
                        docWrap(DOCKER_COMPOSE_TYPE = Type.Builder.build()
                                        .tid(REC_TID)
                                        .vid(DOCKER_COMPOSE_TID)
                                        .isaPredicate(rec(
                                                uri("services"), rec()))
                                        .create(),
                                "a docker compose stack — refines rec::T",
                                "[services => [web => [image => \"nginx\"]]]",
                                "matches docker_compose::T"))));
        docWrap(this, "an instruction set to manipulate docker and docker compose");
        super.setup();
    }
}
