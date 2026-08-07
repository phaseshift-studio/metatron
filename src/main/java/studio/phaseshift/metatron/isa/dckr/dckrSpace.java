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

import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.space.AbstractMemorySpace;
import studio.phaseshift.metatron.isa.m.space.TopicTrie;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.mach.io.type.ObjYAMLSerializer;
import studio.phaseshift.metatron.isa.mach.io.type.ObjmtronSerializer;
import studio.phaseshift.metatron.isa.mach.type.ui.console.Console;
import studio.phaseshift.metatron.isa.mach.type.ui.widget.ProgressTableWidget;
import studio.phaseshift.metatron.util.MTronException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.HOST;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.dckr.dckrInstSet.DCKR_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.INST_CTOR_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.SPACE_TID;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.Uri.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MType.T;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;
import static studio.phaseshift.metatron.util.CommonUtil.mutableMap;
import static studio.phaseshift.metatron.util.IteratorUtil.map;

/**
 * A metatron space bridging to a Docker daemon.
 * <p>
 * Docker state (containers, images) is mirrored into an internal
 * {@link memSpace} whose VID is {@code null} so the Router does not
 * index it.  All reads delegate to that internal store, which handles
 * Rec sub-path unrolling for free.
 * <p>
 * Writes trigger Docker operations then update the internal store.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public class dckrSpace extends AbstractMemorySpace {

    public static final fURI DCKR_SPACE_TID = DCKR_ISA_TID.extend("space/dckrspace");
    public static final Type DCKR_SPACE_TYPE = Type.Builder.build()
            .tid(SPACE_TID)
            .vid(DCKR_SPACE_TID)
            .isaPredicate(rec(
                    uri(HOST).maybe().asUri(), URI_TYPE,
                    uri("progress").maybe(), REC_TYPE))
            .constructor(
                    instC(INST_CTOR_TID.dom(ALL.maybe()).rng(DCKR_SPACE_TID),
                            lst(T(DCKR_SPACE_TID)),
                            (lhs, inst) -> dckrSpace.of(inst.arg(0).asRec(), inst.arg(0).vid())))
            .create();

    private static final Path COMPOSE_DIR = Path.of("/tmp", "metatron-docker");
    private static final ObjYAMLSerializer YAML_SERIALIZER = ObjYAMLSerializer.single();
    private static final ObjDockerSerializer DOCKER_SERIALIZER = ObjDockerSerializer.single();

    private final memSpace store;
    private volatile ProgressTableWidget pullWidget;
    private final ProgressTableWidget progressWidget;
    private final String dockerHost;

    // ===================================================================
    // Factory & Constructor
    // ===================================================================

    public static dckrSpace of(final Rec config, final fURI vid) {
        return new dckrSpace(new TopicTrie(), config.jvm(), DCKR_SPACE_TID, vid);
    }

    protected dckrSpace(final TopicTrie sjvm, final Map<Obj, Obj> config,
                        final fURI tid, final fURI vid) {
        super(sjvm, config, tid, vid);
        this.dockerHost = this.at(uri(HOST)).isNoObj() ? null
                : this.at(uri(HOST)).uriValue().toString();
        if (this.dockerHost != null)
            LOG.info("docker host {{y}}%s", this.dockerHost);
        this.store = memSpace.of(f("#"), null);
        final Obj pw = this.at(uri("progress"));
        this.progressWidget = (!pw.isNoObj() && pw instanceof ProgressTableWidget)
                ? (ProgressTableWidget) pw : null;
        checkDockerAvailable();
    }

    private void checkDockerAvailable() {
        try {
            final ProcessResult r = exec(dockerCmd("version", "--format", "{{.Client.Version}}"));
            LOG.info("docker {{b}}%s{{X}} available", r.stdout().trim());
        } catch (final Exception e) {
            LOG.warn("docker cli not available: %s", e.getMessage());
        }
    }

    // ===================================================================
    // directReader / directWriter — bridge to internal memSpace store
    // ===================================================================

    @Override
    public Function<fURI, Iterator<IdObj>> directReader() {
        return (pattern) -> {
            final fURI routed = Space.Helper.routeFromSpace(pattern, this.routes());
            final Iterator<IdObj> storeResults = this.store.directReader().apply(routed);
            return map(storeResults, kv ->
                    IdObj.of(Space.Helper.routeToSpace(kv.furi(), this.routes()), kv.obj()));
        };
    }

    @Override
    public BiFunction<fURI, Obj, Obj> directWriter() {
        return (pattern, obj) -> {
            if (pattern.hasPattern()) {
                // Expand wildcard: read matching entries, write obj to each
                this.directReader().apply(pattern)
                        .forEachRemaining(kv -> this.write(kv.furi(), obj));
            } else {
                final fURI routed = Space.Helper.routeFromSpace(pattern, this.routes());
                if (obj.isNoObj()) {
                    this.store.sjvm().remove(routed);
                } else {
                    if (obj.isRec())
                        Rec.Helper.stripNone(obj.asRec());
                    this.store.sjvm().put(routed, obj);
                }
            }
            return obj;
        };
    }

    // ===================================================================
    // Read — refresh store from Docker daemon, then delegate
    // ===================================================================

    @Override
    public Obj read(final fURI vid) {
        final fURI routed = Space.Helper.routeFromSpace(vid, this.routes());
        final DataPath dp = DataPath.withoutDB(routed);
        if ("container".equals(dp.collection()) || "+".equals(dp.collection())) {
            refreshImages();
            refreshNetworks();
            refreshVolumes();
            refreshContainers();
        } else if ("image".equals(dp.collection())) {
            refreshImages();
            refreshContainers();
        } else if ("volume".equals(dp.collection())) {
            refreshVolumes();
            refreshContainers();
        } else if ("network".equals(dp.collection())) {
            refreshNetworks();
            refreshContainers();
        }

        if (vid.isBranch())
            return objs(this.store.read(routed).stream().map(i -> rel(Space.Helper.routeToSpace(i.asRel().first().uriValue(), this.routes()).toUri(), i.asRel().second())));
        return this.store.read(routed);
    }

    private void refreshContainers() {
        refresh("ps", "-a", "--no-trunc", "--format", "json", "names", "id", "container");
        // Build graph: link each container under its image + add !* refs
        final Obj containers = this.store.read(f("container"));
        if (!containers.isNoObj()) {
            final Map<String, List<Obj>> byImage = new LinkedHashMap<>();
            containers.asRec().jvm().forEach((name, obj) -> {
                if (!obj.isRec()) return;
                final Rec c = obj.asRec();
                final String imageName = c.at(uri("image")).isNoObj() ? null : objToString(c.at(uri("image")));
                if (imageName != null) {
                    // Replace string image name with !* ref into dckrSpace image
                    final Obj imageRef = auto_from_(uri(this.pattern().retractPattern().toString() + "image/" + imageName)).tryToInst();
                    c.jvm().put(uri("image"), imageRef);
                    this.store.write(f("container").extend(name.uriValue().name()), c);
                    byImage.computeIfAbsent(imageName, k -> new ArrayList<>()).add(name);
                }
            });
            // Add containers refs to each image rec
            byImage.forEach((imageName, containerNames) -> {
                final Obj image = this.store.read(f("image").extend(imageName));
                if (!image.isNoObj() && image.isRec()) {
                    final List<Obj> refs = containerNames.stream()
                            .map(n -> uri(this.pattern().retractPattern().toString() + "container/"
                                    + n.uriValue().name()))
                            .map(i -> (Obj) auto_from_(i).tryToInst())
                            .toList();
                    image.asRec().jvm().put(uri("container"), lst(refs));
                    this.store.write(f("image").extend(imageName), image);
                }
            });

            // -- Network graph: container <-> network --
            final Map<String, List<Obj>> byNetwork = new LinkedHashMap<>();
            containers.asRec().jvm().forEach((name, obj) -> {
                if (!obj.isRec()) return;
                final Rec c = obj.asRec();
                // Docker field is "Networks" → ObjDockerSerializer produces "networks"
                final String netName = c.at(uri("networks")).isNoObj() ? null
                        : objToString(c.at(uri("networks")));
                if (netName != null && !netName.isBlank()) {
                    final Obj netRef = auto_from_(uri(this.pattern().retractPattern().toString() + "network/" + netName)).tryToInst();
                    c.jvm().put(uri("network"), netRef);
                    c.jvm().remove(uri("networks"));
                    this.store.write(f("container").extend(name.uriValue().name()), c);
                    byNetwork.computeIfAbsent(netName, k -> new ArrayList<>()).add(name);
                }
            });
            byNetwork.forEach((netName, containerNames) -> {
                final Obj network = this.store.read(f("network").extend(netName));
                if (!network.isNoObj() && network.isRec()) {
                    final List<Obj> refs = containerNames.stream()
                            .map(n -> uri(this.pattern().retractPattern().toString() + "container/"
                                    + n.uriValue().name()))
                            .map(i -> (Obj) auto_from_(i).tryToInst())
                            .toList();
                    network.asRec().at(uri("container"), lst(refs), Poly.MUTABLE);
                    this.store.write(f("network").extend(netName), network);
                }
            });

            // -- Volume graph: container <-> volume --
            // Docker field is "Mounts" → ObjDockerSerializer produces "mounts".
            // Named volumes (non-path source) are tracked in docker volume ls.
            final Map<String, List<Obj>> byVolume = new LinkedHashMap<>();
            containers.asRec().jvm().forEach((name, obj) -> {
                if (!obj.isRec()) return;
                final Rec c = obj.asRec();
                final Obj mountsObj = c.at(uri("mounts"));
                if (mountsObj.isNoObj()) return;

                final List<Obj> containerVolRefs = new ArrayList<>();
                if (mountsObj.isUri()) {
                    final String volName = extractVolumeName(mountsObj);
                    if (volName != null)
                        containerVolRefs.add(auto_from_(uri(this.pattern().retractPattern().toString() + "volume/" + volName)).tryToInst());
                } else if (mountsObj.isLst()) {
                    mountsObj.asLst().elements().forEach(mount -> {
                        final String volName = extractVolumeName(mount);
                        if (volName != null)
                            containerVolRefs.add(auto_from_(uri(this.pattern().retractPattern().toString() + "volume/" + volName)).tryToInst());
                    });
                }
                if (!containerVolRefs.isEmpty()) {
                    c.jvm().put(uri("mount"), lst(containerVolRefs));
                    c.jvm().remove(uri("mounts"));
                    this.store.write(f("container").extend(name.uriValue().name()), c);
                    containerVolRefs.forEach(ref -> {
                        final String volName = ref.asInst().arg(0).uriValue().name();
                        byVolume.computeIfAbsent(volName, k -> new ArrayList<>()).add(name);
                    });
                }
            });
            // Path-based lookup may need fallback to Rec scan for memSpace individual writes
            final Obj allVols = this.store.read(f("volume"));
            byVolume.forEach((volName, containerNames) -> {
                Obj volume = this.store.read(f("volume").extend(volName));
                if (volume.isNoObj() && !allVols.isNoObj() && allVols.isRec()) {
                    for (final Obj v : allVols.asRec().jvm().values()) {
                        if (!v.isRec()) continue;
                        final Obj nameObj = v.asRec().at(uri("name"));
                        if (nameObj.isNoObj()) continue;
                        if (volName.equals(objToString(nameObj))) {
                            volume = v;
                            break;
                        }
                    }
                }
                if (!volume.isNoObj() && volume.isRec()) {
                    final List<Obj> refs = containerNames.stream()
                            .map(n -> uri(this.pattern().retractPattern().toString() + "container/"
                                    + n.uriValue().name()))
                            .map(i -> (Obj) i)
                            .toList();
                    volume.asRec().jvm().put(uri("container"), lst(refs));
                    this.store.write(f("volume").extend(volName), volume);
                }
            });
        }
    }

    /**
     * Extract a Docker volume name from a mount object.  Returns {@code null}
     * when the source is a bind-mount path (starts with {@code /}, {@code .},
     * or {@code ~}) — those are not tracked in {@code docker volume ls}.
     */
    private String extractVolumeName(final Obj mount) {
        if (mount.isUri()) {
            final fURI furi = mount.uriValue();
            final String scheme = furi.scheme();
            if (scheme != null && !scheme.isEmpty())
                return scheme;                        // "myvol:/data" → myvol
            // No scheme: bare named volume ("myvol") or bind mount ("/host/path")
            if (furi.path().size() == 1) {
                final String name = furi.pathString();
                if (!name.startsWith("/") && !name.startsWith(".") && !name.startsWith("~"))
                    return name;                      // bare named volume
            }
            return null;                              // multi-segment: bind mount, skip
        }
        if (mount.isRec()) {
            // Mount object: {source: "myvol", destination: "/data", ...}
            final Obj src = mount.asRec().at(uri("source"));
            if (src.isNoObj()) return null;
            final String s = objToString(src);
            if (s.startsWith("/") || s.startsWith(".") || s.startsWith("~"))
                return null;                          // bind mount
            return s;
        }
        return null;
    }

    private void refreshImages() {
        // Parse docker image ls NDJSON and write each image individually keyed by
        // repository:tag (so containers can link to their image by repo:tag)
        try {
            final ProcessResult r = exec(dockerCmd("image", "ls", "--format", "json"));
            for (final String line : r.stdout().split("\\R")) {
                if (line.isBlank()) continue;
                final Rec img = (Rec) DOCKER_SERIALIZER.inputBytes(line);
                final String repo = img.at(uri("repository")).isNoObj() ? null
                        : objToString(img.at(uri("repository")));
                final String tag = img.at(uri("tag")).isNoObj() ? null
                        : objToString(img.at(uri("tag")));
                final String key = repo != null && tag != null ? repo + ":" + tag
                        : repo != null ? repo
                        : tag != null ? tag
                        : objToString(img.at(uri("id")));
                this.store.write(f("image").extend(key), img);
            }
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    private void refreshVolumes() {
        try {
            final ProcessResult r = exec(dockerCmd("volume", "ls", "--format", "json"));
            for (final String line : r.stdout().split("\\R")) {
                if (line.isBlank()) continue;
                final Rec vol = (Rec) DOCKER_SERIALIZER.inputBytes(line);
                final String name = vol.at(uri("name")).isNoObj() ? null
                        : objToString(vol.at(uri("name")));
                if (name != null)
                    this.store.write(f("volume").extend(name), vol);
            }
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    private void refreshNetworks() {
        try {
            final ProcessResult r = exec(dockerCmd("network", "ls", "--format", "json"));
            for (final String line : r.stdout().split("\\R")) {
                if (line.isBlank()) continue;
                final Rec net = (Rec) DOCKER_SERIALIZER.inputBytes(line);
                final String name = net.at(uri("name")).isNoObj() ? null
                        : objToString(net.at(uri("name")));
                if (name != null)
                    this.store.write(f("network").extend(name), net);
            }
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /**
     * Run a docker ls command, parse NDJSON, clean+store results keyed by the first non-noobj name field found.
     */
    private void refresh(final String subcommand, final String... argsAndKeyFields) {
        try {
            // Last two varargs are keyFieldFallback and storePath; rest are docker args
            final int n = argsAndKeyFields.length;
            final String storePathKey = argsAndKeyFields[n - 1];    // e.g. "container"
            final String fallbackKey = argsAndKeyFields[n - 2];     // e.g. "ID"
            final String primaryKey = argsAndKeyFields[n - 3];      // e.g. "Names"
            final String[] dockerArgs = new String[n - 3];
            System.arraycopy(argsAndKeyFields, 0, dockerArgs, 0, n - 3);

            final ProcessResult r = exec(dockerCmd(subcommand, dockerArgs));
            final Map<Obj, Obj> results = new LinkedHashMap<>();
            for (final String line : r.stdout().split("\\R")) {
                if (line.isBlank()) continue;
                final Rec rec = (Rec) DOCKER_SERIALIZER.inputBytes(line);
                final String name = resourceName(rec, primaryKey, fallbackKey);
                results.put(uri(name), rec);
            }
            this.store.write(f(storePathKey), rec(results));
            //LOG.warn("refresh {{b}}%s{{X}}: stored %d entries: %s", storePathKey, results.size(), results.keySet());

        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /**
     * Extract the display name from a Docker resource Rec.
     */
    private static String resourceName(final Rec rec, final String primary, final String fallback) {
        final Obj val = rec.at(uri(primary));
        if (!val.isNoObj()) return objToString(val);
        return objToString(rec.at(uri(fallback)));
    }

    private static String objToString(final Obj obj) {
        String s;
        if (obj.isStr()) s = Str.Helper.cleanString(obj, true);
        else if (obj.isUri()) s = Str.Helper.cleanString(obj, true);
        else s = new String(ObjmtronSerializer.compact().outputBytes(obj).array());
        // Docker prefixes container names with a leading slash (e.g. "/sqlite").
        // Don't strip if the value contains ':' — those are bind-mount paths
        // ("/host/path:/container/path") or image references ("nginx:alpine").
        return (s.startsWith("/") && !s.contains(":")) ? s.substring(1) : s;
    }

    // ===================================================================
    // Write — Docker operations, then update store
    // ===================================================================

    @Override
    public Obj write(final fURI vid, final Obj obj) {
        final fURI routed = Space.Helper.routeFromSpace(vid, this.routes());
        final DataPath dp = DataPath.withoutDB(routed);

        // Expand + wildcard in entry position: write obj to each concrete child
        if (dp.hasEntry() && "+".equals(dp.entry())) {
            final fURI parentRouted = routed.retract(1);
            this.store.readStream(parentRouted.asBranch()).forEach(kv -> {
                final fURI concreteRouted = kv.furi();
                final fURI concreteVID = Space.Helper.routeToSpace(concreteRouted, this.routes());
                this.write(concreteVID, obj);
            });
            return obj;
        }

        if ("compose".equals(dp.collection()) && dp.hasEntry() && !dp.hasField()) {
            final String name = dp.entry();
            if (obj.isNoObj()) {
                composeDown(name);
                this.store.write(routed, noobj());
                return noobj();
            } else if (obj.isRec()) {
                this.store.write(routed, obj);
                composeUp(name, obj.asRec());
                refreshContainers();
                return this.store.read(routed);
            }
        }

        if ("container".equals(dp.collection()) && dp.hasEntry() && !dp.hasField()) {
            if (obj.isNoObj()) {
                stopAndRemove(dp.entry());
                this.store.write(routed, noobj());
                return noobj();
            } else if (obj.isRec()) {
                containerRun(dp.entry(), obj.asRec());
                refreshContainers();
                return this.store.read(routed);
            }
        }

        if ("volume".equals(dp.collection()) && dp.hasEntry() && !dp.hasField()) {
            this.read(vid.asBranch()).stream().forEach(rel -> {
                final DataPath vp = DataPath.of(rel.asRel().first().uriValue());
                if (obj.isNoObj()) {
                    volumeRemove(vp.entry());
                    this.store.write(rel.asRel().first().uriValue(), noobj());
                } else if (obj.isRec()) {
                    volumeCreate(vp.entry(), obj.asRec());
                }
            });
            refreshVolumes();
            return this.read(vid);
        }

        if ("network".equals(dp.collection()) && dp.hasEntry() && !dp.hasField()) {
            if (obj.isNoObj()) {
                networkRemove(dp.entry());
                this.store.write(routed, noobj());
                return noobj();
            } else if (obj.isRec()) {
                networkCreate(dp.entry(), obj.asRec());
                refreshNetworks();
                return this.store.read(routed);
            }
        }

        // Store write value in internal store (compose configs, image metadata, etc.)
        this.store.write(routed, obj);
        return obj;
    }

    @Override
    public Stream<IdObj> readStream(final fURI id) {
        final fURI routed = Space.Helper.routeFromSpace(id, this.routes());
        return this.store.readStream(routed).map(i ->
                IdObj.of(Space.Helper.routeToSpace(i.furi(), this.routes()), i.obj()));
    }

    @Override
    public Stream<IdObj> writeStream(final fURI id, final Obj obj) {
        final fURI routed = Space.Helper.routeFromSpace(id, this.routes());
        return this.store.writeStream(routed, obj).map(i ->
                IdObj.of(Space.Helper.routeToSpace(i.furi(), this.routes()), i.obj()));
    }

    // ===================================================================
    // Docker CLI helpers
    // ===================================================================

    private void composeUp(final String name, final Rec config) {
        try {
            this.pullWidget = Console.getTerminal() == null ? null :
                    (this.progressWidget != null
                            ? this.progressWidget : new ProgressTableWidget());
            final Path dir = COMPOSE_DIR.resolve(name);
            Files.createDirectories(dir);
            final Path yamlFile = dir.resolve("docker-compose.yml");
            Files.writeString(yamlFile, YAML_SERIALIZER.write(config), StandardCharsets.UTF_8);
            LOG.info("wrote compose file {{y}}%s", yamlFile);
            exec(dockerCmd("compose", "--progress=plain", "-f", yamlFile.toString(), "-p", name, "up", "-d"));
            LOG.info("compose {{b}}%s{{X}} up", name);
            if (null != this.pullWidget)
                this.pullWidget.close();
            this.pullWidget = null;
        } catch (final Exception e) {
            if (this.pullWidget != null) {
                this.pullWidget.close();
                this.pullWidget = null;
            }
            throw MTronException.of(e);
        }
    }

    private void composeDown(final String name) {
        try {
            final Path yamlFile = COMPOSE_DIR.resolve(name).resolve("docker-compose.yml");
            if (Files.exists(yamlFile)) {
                exec(dockerCmd("compose", "-f", yamlFile.toString(), "-p", name, "down"));
                LOG.info("compose {{b}}%s{{X}} down", name);
                Files.deleteIfExists(yamlFile);
                Files.deleteIfExists(yamlFile.getParent());
            }
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    private void containerRun(final String name, final Rec config) {
        this.pullWidget = this.progressWidget != null
                ? this.progressWidget : new ProgressTableWidget();
        final String image = objToString(config.at(uri("image"))
                .orThrow(MTronException.of("container config must have an 'image' field")));
        final List<String> cmd = dockerCmdList("run", "-d", "--name", name);
        if (config.has("user")) {
            cmd.add("--user");
            cmd.add(objToString(config.at(uri("user"))));
        }
        if (config.has("ports")) {
            config.at(uri("ports")).asLst().elements()
                    .forEach(p -> {
                        cmd.add("-p");
                        cmd.add(objToString(p));
                    });
        }
        if (config.has("environment")) {
            config.at(uri("environment")).asRec().jvm()
                    .forEach((k, v) -> {
                        cmd.add("-e");
                        cmd.add(k.uriValue().name() + "=" + objToString(v));
                    });
        }
        if (config.has("volumes")) {
            config.at(uri("volumes")).asLst().elements()
                    .forEach(v -> {
                        final String volStr = objToString(v);
                        // Auto-create named volumes (format: "volName:/container/path")
                        final int colonIdx = volStr.indexOf(':');
                        if (colonIdx > 0) {
                            final String source = volStr.substring(0, colonIdx);
                            // Named volumes are simple names (not paths starting with / or .)
                            if (!source.startsWith("/") && !source.startsWith(".") && !source.startsWith("~")) {
                                try {
                                    exec(dockerCmd("volume", "create", source));
                                    LOG.info("auto-created volume {{b}}%s", source);
                                } catch (final Exception e) {
                                    LOG.warn("could not create volume %s: %s", source, e.getMessage());
                                }
                            }
                        }
                        cmd.add("-v");
                        cmd.add(volStr);
                    });
        }
        if (config.has("network")) {
            cmd.add("--network");
            cmd.add(objToString(config.at(uri("network"))));
        }
        cmd.add(image);
        if (config.has("command")) {
            final Obj commandObj = config.at(uri("command"));
            if (commandObj.isStr() || commandObj.isUri()) {
                // Split on whitespace so each token becomes a separate argv entry.
                // Use raw string (not objToString) to preserve leading slashes in paths.
                final String cmdStr = commandObj.isStr() ? commandObj.strValue()
                        : commandObj.uriValue().toString();
                for (final String token : cmdStr.split("\\s+")) {
                    if (!token.isBlank())
                        cmd.add(token);
                }
            } else if (commandObj.isLst()) {
                commandObj.asLst().elements().forEach(c -> {
                    // Use raw string value — objToString() strips leading '/' from
                    // path-like strings (designed for Docker container names), but
                    // command arguments like "/data/dr.sqlite" must preserve the slash.
                    if (c.isStr()) cmd.add(c.strValue());
                    else if (c.isUri()) cmd.add(c.uriValue().toString());
                    else cmd.add(objToString(c));
                });
            }
        }
        try {
            exec(cmd.toArray(new String[0]));
            LOG.info("container {{b}}%s{{X}} started from {{y}}%s", name, image);
        } catch (final Exception e) {
            throw MTronException.of(e);
        } finally {
            if (this.pullWidget != null) {
                this.pullWidget.close();
                this.pullWidget = null;
            }
        }
    }

    private void volumeCreate(final String name, final Rec config) {
        final List<String> cmd = dockerCmdList("volume", "create", name);
        if (config.has("driver")) {
            cmd.add("-d");
            cmd.add(objToString(config.at(uri("driver"))));
        }
        try {
            exec(cmd.toArray(new String[0]));
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    private void volumeRemove(final String name) {
        try {
            exec(dockerCmd("volume", "rm", name));
        } catch (final Exception e) {
            LOG.warn("volume rm %s: %s", name, e.getMessage());
        }
    }

    private void networkCreate(final String name, final Rec config) {
        final List<String> cmd = dockerCmdList("network", "create", name);
        if (config.has("driver")) {
            cmd.add("-d");
            cmd.add(objToString(config.at(uri("driver"))));
        }
        try {
            exec(cmd.toArray(new String[0]));
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    private void networkRemove(final String name) {
        try {
            exec(dockerCmd("network", "rm", name));
        } catch (final Exception e) {
            LOG.warn("network rm %s: %s", name, e.getMessage());
        }
    }

    private void stopAndRemove(final String name) {
        try {
            exec(dockerCmd("stop", name));
            LOG.info("stopped container {{b}}%s", name);
        } catch (final Exception e) {
            LOG.warn("stop %s: %s", name, e.getMessage());
        }
        try {
            exec(dockerCmd("rm", name));
            LOG.info("removed container {{b}}%s", name);
        } catch (final Exception e) {
            LOG.warn("rm %s: %s", name, e.getMessage());
        }
    }

    // ===================================================================
    // Process execution
    // ===================================================================

    private String[] dockerCmd(final String subcommand, final String... args) {
        final List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        if (this.dockerHost != null) {
            cmd.add("-H");
            cmd.add(this.dockerHost);
        }
        cmd.add(subcommand);
        for (final String a : args) cmd.add(a);
        return cmd.toArray(new String[0]);
    }

    private List<String> dockerCmdList(final String subcommand, final String... initialArgs) {
        final List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        if (this.dockerHost != null) {
            cmd.add("-H");
            cmd.add(this.dockerHost);
        }
        cmd.add(subcommand);
        for (final String a : initialArgs) cmd.add(a);
        return cmd;
    }

    /**
     * Parse a Docker progress line into a structured Rec with percent and sizes.
     */
    private static Rec parseDockerProgress(final String layer, final String line) {
        final String rest = line.substring(13).trim(); // strip layer prefix + space
        final String status;
        if (rest.startsWith("Downloading")) status = "Downloading";
        else if (rest.startsWith("Extracting")) status = "Extracting";
        else if (rest.startsWith("Pull complete")) status = "Complete";
        else if (rest.startsWith("Already exists")) status = "Exists";
        else if (rest.startsWith("Waiting")) status = "Waiting";
        else status = rest.split(" ")[0];

        // Extract percentage from progress bar: [====>     ] → count '=' / total width
        int percent = -1;
        final int barStart = rest.indexOf('[');
        final int barEnd = rest.indexOf(']');
        if (barStart >= 0 && barEnd > barStart) {
            final String bar = rest.substring(barStart + 1, barEnd);
            percent = (int) (100.0 * bar.replace(">", "=").chars().filter(c -> c == '=').count()
                    / Math.max(1, bar.length()));
        }
        // Extract sizes: "93.31MB/160MB"
        String downloaded = null, total = null;
        final int slashIdx = rest.lastIndexOf('/');
        if (slashIdx > 0) {
            final int sizeStart = rest.lastIndexOf(' ', slashIdx);
            if (sizeStart >= 0) {
                final String[] parts = rest.substring(sizeStart + 1).split("/");
                downloaded = parts[0].trim();
                total = parts.length > 1 ? parts[1].trim() : null;
            }
        }
        return rec(mutableMap(
                uri("layer"), uri(layer),
                uri("status"), uri(status),
                uri("percent"), jnt(percent),
                uri("downloaded").maybe(), downloaded != null ? uri(downloaded) : noobj(),
                uri("total").maybe(), total != null ? uri(total) : noobj()));
    }

    private record ProcessResult(String stdout, String stderr) {
    }

    private ProcessResult exec(final String... cmd) {
        return exec(300, cmd);  // default 5 min for long-running pulls
    }

    private ProcessResult exec(final int timeoutSecs, final String... cmd) {
        try {
            final ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(false);
            final Process p = pb.start();
            // Stream stderr (Docker progress) in real-time
            // Progress lines update in-place via \r; non-progress lines log normally
            final StringBuilder stderrBuf = new StringBuilder();
            final Thread stderrThread = new Thread(() -> {
                final Map<String, Obj> layerRows = new LinkedHashMap<>();
                try (final BufferedReader reader = new java.io.BufferedReader(
                        new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // stderrBuf.append(line).append('\n');
                        final String trimmed = line.trim();
                        if (trimmed.matches("^[0-9a-f]{12} .*")) {
                            final String layer = trimmed.substring(0, 12);
                            layerRows.put(layer, parseDockerProgress(layer, trimmed));
                            if (Console.getTerminal() != null) {
                                if (pullWidget == null)
                                    pullWidget = this.progressWidget != null ? this.progressWidget : new ProgressTableWidget();
                                layerRows.values().forEach(r ->
                                        pullWidget.addProgressRow(r.asRec()));
                                pullWidget.run();
                            }
                            //pullWidget.close();
                        } else {
                            LOG.info("{{y}}%s", trimmed);
                        }
                    }
                    if (null != pullWidget)
                        this.pullWidget.close();
                } catch (final IOException ignored) { /* stream closed */ }
            }, "docker-stderr");
            stderrThread.setDaemon(true);
            stderrThread.start();
            // Read stdout synchronously
            final String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            final boolean completed = p.waitFor(timeoutSecs, TimeUnit.SECONDS);
            if (!completed) {
                p.destroyForcibly();
                throw MTronException.of("command timed out: %s", String.join(" ", cmd));
            }
            stderrThread.join(5000);
            final String stderr = stderrBuf.toString();
            if (p.exitValue() != 0)
                throw MTronException.of("exit %d: %s\n%s", p.exitValue(), String.join(" ", cmd), stderr);
            return new ProcessResult(stdout, stderr);
        } catch (final IOException e) {
            throw MTronException.of("docker cli not found: %s", e.getMessage());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw MTronException.of(e);
        }
    }
}
