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

package studio.phaseshift.metatron.isa.m.dev.dckr;

import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;
import studio.phaseshift.metatron.furi.DataPath;
import studio.phaseshift.metatron.furi.fURI;
import studio.phaseshift.metatron.isa.Space;
import studio.phaseshift.metatron.isa.m.mInstSet;
import studio.phaseshift.metatron.isa.m.space.AbstractMemorySpace;
import studio.phaseshift.metatron.isa.m.space.TopicTrie;
import studio.phaseshift.metatron.isa.m.space.memSpace;
import studio.phaseshift.metatron.isa.m.sys.ui.console.Console;
import studio.phaseshift.metatron.isa.m.sys.ui.widget.ProgressTableWidget;
import studio.phaseshift.metatron.isa.m.type.*;
import studio.phaseshift.metatron.isa.m.web.type.ObjYAMLSerializer;
import studio.phaseshift.metatron.isa.m.web.type.ObjmtronSerializer;
import studio.phaseshift.metatron.util.MTronException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import static studio.phaseshift.metatron.Tokens.*;
import static studio.phaseshift.metatron.furi.fURI.Singleton.ALL;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.m.dev.dckr.dckrInstSet.DCKR_ISA_TID;
import static studio.phaseshift.metatron.isa.m.mInstSet.URI_TYPE;
import static studio.phaseshift.metatron.isa.m.parser.mFluent.StartLess.auto_from_;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MInst.instC;
import static studio.phaseshift.metatron.isa.m.type.impl.MInt.jnt;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MObjs.objs;
import static studio.phaseshift.metatron.isa.m.type.impl.MRel.rel;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
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
                    uri("progress").maybe(), mInstSet.REC_TYPE))
            .constructor(
                    instC(INST_CTOR_TID.dom(ALL.maybe()).rng(DCKR_SPACE_TID),
                            lst(T(DCKR_SPACE_TID)),
                            (lhs, inst) -> dckrSpace.of(inst.arg(0).asRec(), inst.arg(0).vid())))
            .create();

    private static final Path COMPOSE_DIR = Path.of("/tmp", "metatron-docker");
    private static final ObjYAMLSerializer YAML_SERIALIZER = ObjYAMLSerializer.single();
    private static final ObjDockerSerializer DOCKER_SERIALIZER = ObjDockerSerializer.single();
    /**
     * The docker label this space stamps on everything it creates when its config declares an
     * {@code owner}.  It is what makes reaping ownership-driven rather than time-based: the label
     * outlives the VM, so a later boot -- or the close of the frame that asked for it -- can ask
     * "what belongs to me?" ({@code *docker:container/+.?[owner=></usr/avatar>]}) instead of
     * guessing from age or state.
     */
    public static final String OWNER_LABEL = "com.phaseshift.metatron.owner";

    private final memSpace cache;
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
        // unregistered: a private cache mirroring this space's q-procs, not a `#` mount of its own
        this.cache = memSpace.unregistered(f("#"), null);
        this.qs().lstValue().forEach(qq -> this.cache.addQ(qq.as()));
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
            final Iterator<IdObj> storeResults = this.cache.directReader().apply(routed);
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
                    this.cache.sjvm().remove(routed);
                } else {
                    if (obj.isRec())
                        Rec.Helper.stripNone(obj.asRec());
                    this.cache.sjvm().put(routed, obj);
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
            return objs(this.cache.read(routed).stream().map(i -> rel(Space.Helper.routeToSpace(i.asRel().first().uriValue(), this.routes()).toUri(), i.asRel().second())));
        return this.cache.read(routed);
    }

    private void refreshContainers() {
        refresh("ps", "-a", "--no-trunc", "--format", "json", "names", "id", "container");
        // Build graph: link each container under its image + add !* refs
        final Obj containers = this.cache.read(f("container"));
        if (!containers.isNoObj()) {
            final Map<String, List<Obj>> byImage = new LinkedHashMap<>();
            containers.asRec().jvm().forEach((name, obj) -> {
                if (!obj.isRec()) return;
                final Rec c = obj.asRec();
                // Surface the owner label as a first-class field, so a reaper selects on it directly
                // (`*docker:container/+.?[owner=></usr/avatar>]`) instead of digging through labels.
                final Obj labels = c.at(uri("labels"));
                if (labels.isRec()) {
                    final Obj owner = labels.asRec().at(uri(OWNER_LABEL));
                    if (!owner.isNoObj())
                        // cleanString, not objToString: a label value is a str now, and a str's mtron text carries
                        // its quotes — the owner field is compared against the plain authority the reaper passes
                        c.jvm().put(uri("owner"), uri(Str.Helper.cleanString(owner, true)));
                }
                final String imageName = c.at(uri("image")).isNoObj() ? null : objToString(c.at(uri("image")));
                if (imageName != null) {
                    // Replace string image name with !* ref into dckrSpace image
                    final Obj imageRef = auto_from_(uri(this.pattern().retractPattern().toString() + "image/" + imageName)).tryToInst();
                    c.jvm().put(uri("image"), imageRef);
                    this.cache.write(f("container").extend(name.uriValue().name()), c);
                    byImage.computeIfAbsent(imageName, k -> new ArrayList<>()).add(name);
                }
            });
            // Add containers refs to each image rec
            byImage.forEach((imageName, containerNames) -> {
                final Obj image = this.cache.read(f("image").extend(imageName));
                if (!image.isNoObj() && image.isRec()) {
                    final List<Obj> refs = containerNames.stream()
                            .map(n -> uri(this.pattern().retractPattern().toString() + "container/"
                                    + n.uriValue().name()))
                            .map(i -> (Obj) auto_from_(i).tryToInst())
                            .toList();
                    image.asRec().jvm().put(uri("container"), lst(refs));
                    this.cache.write(f("image").extend(imageName), image);
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
                    this.cache.write(f("container").extend(name.uriValue().name()), c);
                    byNetwork.computeIfAbsent(netName, k -> new ArrayList<>()).add(name);
                }
            });
            byNetwork.forEach((netName, containerNames) -> {
                final Obj network = this.cache.read(f("network").extend(netName));
                if (!network.isNoObj() && network.isRec()) {
                    final List<Obj> refs = containerNames.stream()
                            .map(n -> uri(this.pattern().retractPattern().toString() + "container/"
                                    + n.uriValue().name()))
                            .map(i -> (Obj) auto_from_(i).tryToInst())
                            .toList();
                    network.asRec().at(uri("container"), lst(refs), Poly.MUTABLE);
                    this.cache.write(f("network").extend(netName), network);
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
                    this.cache.write(f("container").extend(name.uriValue().name()), c);
                    containerVolRefs.forEach(ref -> {
                        final String volName = ref.asInst().arg(0).uriValue().name();
                        byVolume.computeIfAbsent(volName, k -> new ArrayList<>()).add(name);
                    });
                }
            });
            // Path-based lookup may need fallback to Rec scan for memSpace individual writes
            final Obj allVols = this.cache.read(f("volume"));
            byVolume.forEach((volName, containerNames) -> {
                Obj volume = this.cache.read(f("volume").extend(volName));
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
                    this.cache.write(f("volume").extend(volName), volume);
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
                this.cache.write(f("image").extend(key), img);
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
                    this.cache.write(f("volume").extend(name), vol);
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
                    this.cache.write(f("network").extend(name), net);
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
            this.cache.write(f(storePathKey), rec(results));
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
        else s = new String(ObjmtronSerializer.single().outputBytes(obj).array());
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
            this.cache.readStream(parentRouted.asBranch()).forEach(kv -> {
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
                this.cache.write(routed, noobj());
                return noobj();
            } else if (obj.isRec()) {
                this.cache.write(routed, obj);
                composeUp(name, obj.asRec());
                refreshContainers();
                return this.cache.read(routed);
            }
        }

        if ("container".equals(dp.collection()) && dp.hasEntry() && !dp.hasField()) {
            if (obj.isNoObj()) {
                stopAndRemove(dp.entry());
                this.cache.write(routed, noobj());
                return noobj();
            } else if (obj.isRec()) {
                containerRun(dp.entry(), obj.asRec());
                refreshContainers();
                return this.cache.read(routed);
            }
        }

        if ("volume".equals(dp.collection()) && dp.hasEntry() && !dp.hasField()) {
            this.read(vid.asBranch()).stream().forEach(rel -> {
                final DataPath vp = DataPath.of(rel.asRel().first().uriValue());
                if (obj.isNoObj()) {
                    volumeRemove(vp.entry());
                    this.cache.write(rel.asRel().first().uriValue(), noobj());
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
                this.cache.write(routed, noobj());
                return noobj();
            } else if (obj.isRec()) {
                networkCreate(dp.entry(), obj.asRec());
                refreshNetworks();
                return this.cache.read(routed);
            }
        }

        // Store write value in internal store (compose configs, image metadata, etc.)
        this.cache.write(routed, obj);
        return obj;
    }

    @Override
    public Stream<IdObj> readStream(final fURI id) {
        final fURI routed = Space.Helper.routeFromSpace(id, this.routes());
        return this.cache.readStream(routed).map(i ->
                IdObj.of(Space.Helper.routeToSpace(i.furi(), this.routes()), i.obj()));
    }

    @Override
    public Stream<IdObj> writeStream(final fURI id, final Obj obj) {
        final fURI routed = Space.Helper.routeFromSpace(id, this.routes());
        return this.cache.writeStream(routed, obj).map(i ->
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
            Files.writeString(yamlFile, YAML_SERIALIZER.write(this.withContainerName(name, this.withOwnerLabels(config))), StandardCharsets.UTF_8);
            LOG.info("wrote compose file {{y}}%s", yamlFile);
            // --wait: block until the stack reports healthy (per-service
            // healthchecks) so a boot file can register spaces that must be
            // accepting connections right after the up returns.  --timeout:
            // compose's default wait timeout (10s) is too short for long-
            // starting backends.  exec timeout (30m): covers cold pulls +
            // healthcheck retries.
            exec(1800, dockerCmd("compose", "--progress=plain", "-f", yamlFile.toString(),
                    "-p", name, "up", "-d", "--wait", "--timeout", "600"));
            LOG.info("compose {{b}}%s{{X}} up (healthy)", name);
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

    /**
     * The scope that owns what this space creates, from the config's {@code owner} field.  Absent
     * means unowned: nothing is labelled, and nothing is ever reaped on this space's behalf.
     */
    private Obj owner() {
        return this.at(uri(OWNER));
    }

    /**
     * A copy of a compose config with this space's {@code owner} label added to every service.
     * <p>
     * Copied rather than mutated: the rec belongs to the caller that wrote it, and a space reaching
     * into it would change the caller's value under it.  Services that are not recs pass through.
     */
    private Rec withOwnerLabels(final Rec config) {
        if (this.owner().isNoObj() || !config.has("services"))
            return config;
        return withServices(config, (service, copy) -> {
            final Rec labels = rec();
            if (copy.has("labels") && copy.at(uri("labels")).isRec())
                labels.jvm().putAll(copy.at(uri("labels")).asRec().jvm());
            // THE CONFIG SIDE STAYS PLAIN TEXT: this rec is written to the compose YAML, where a str would be
            // quoted ('/usr/me') and docker would then report that quoting as part of the label's value. The
            // labels read BACK from docker are strs (ObjDockerSerializer.parseLabels) — the two sides meet in
            // text, which is what a docker label is.
            labels.jvm().put(uri(OWNER_LABEL), this.owner());
            copy.jvm().put(uri("labels"), labels);
        });
    }

    /**
     * A copy of a compose config whose single service is named exactly the stack.
     * <p>
     * The adoption guard a boot writes is {@code from(docker:container/<stack>).else(… up …)}, and
     * compose's default naming — {@code <stack>-<service>-1} — can never match it.  The guard then
     * misses the stack it just created, so every boot re-runs the {@code up}, and a drifted config
     * hash turns that into the Recreate path: slow, and deadlocked by a second concurrent compose run
     * on the same project.  Naming the container after the stack lets the second boot adopt it.
     * <p>
     * Only for a single-service stack (a multi-service stack has no one name to be asked for), and a
     * {@code container_name} the caller set itself wins.
     */
    private Rec withContainerName(final String stack, final Rec config) {
        if (!config.has("services") || !config.at(uri("services")).isRec())
            return config;
        if (config.at(uri("services")).asRec().jvm().size() != 1)
            return config;
        return withServices(config, (service, copy) -> {
            if (!copy.has("container_name"))
                copy.jvm().put(uri("container_name"), str(stack));
        });
    }

    /**
     * Copy a compose config, handing each service rec (already copied) to {@code edit}.  The caller's
     * recs are never touched — a space that mutated them would change the value its writer still holds.
     */
    private Rec withServices(final Rec config, final BiConsumer<Obj, Rec> edit) {
        final Rec out = rec();
        out.jvm().putAll(config.jvm());
        final Rec services = rec();
        config.at(uri("services")).asRec().jvm().forEach((service, value) -> {
            if (!value.isRec()) {
                services.jvm().put(service, value);
                return;
            }
            final Rec copy = rec();
            copy.jvm().putAll(value.asRec().jvm());
            edit.accept(service, copy);
            services.jvm().put(service, copy);
        });
        out.jvm().put(uri("services"), services);
        return out;
    }

    /**
     * Tear a stack down.  The compose file is only one of two ways to reach it: it lives under
     * {@link #COMPOSE_DIR} ({@code /tmp/metatron-docker}), which does not survive a VM restart --
     * nor a container recreate, where {@code /tmp} belongs to the container.  A stack brought up
     * by an earlier run therefore has no yaml left to {@code -p <name> down} with, and leaving its
     * containers running while the write reports {@code noobj} is the worst possible answer: the
     * caller believes the stack is gone.  So the yaml path is an optimization and the labels
     * compose stamps on the objects themselves are the durable one.
     */
    private void composeDown(final String name) {
        try {
            final Path yamlFile = COMPOSE_DIR.resolve(name).resolve("docker-compose.yml");
            boolean composedDown = false;
            if (Files.exists(yamlFile)) {
                try {
                    exec(dockerCmd("compose", "-f", yamlFile.toString(), "-p", name, "down"));
                    composedDown = true;
                    LOG.info("compose {{b}}%s{{X}} down", name);
                } catch (final Exception e) {
                    // fall through to the label sweep -- it tears down what the yaml described
                    LOG.warn("compose %s down failed (%s) — falling back to compose labels", name, e.getMessage());
                }
            } else {
                LOG.info("no compose file for {{b}}%s{{X}} (ephemeral /tmp) — tearing down by compose labels", name);
            }
            // Sweep only when compose could not: a successful `down` has already removed the stack, and a
            // docker CLI spawned from this JVM is not cheap here, so do not spawn two more queries for a
            // stack that is provably gone.  The sweep is the fix for the missing-file case, and it is idempotent.
            if (!composedDown)
                composeDownByLabel(name);
            Files.deleteIfExists(yamlFile);
            Files.deleteIfExists(yamlFile.getParent());
        } catch (final Exception e) {
            throw MTronException.of(e);
        }
    }

    /**
     * Remove every container and network compose stamped {@code com.docker.compose.project=<name>}.
     * <p>
     * Volumes are deliberately kept, matching {@code docker compose down} without {@code -v}: a
     * stack's data outlives its containers.  Idempotent, so it can follow a successful
     * {@code compose down} as well as replace a missing compose file.
     */
    private void composeDownByLabel(final String name) {
        final String project = "com.docker.compose.project=" + name;
        final List<String> containers = execLines(dockerCmd("ps", "-aq", "--filter", "label=" + project));
        if (!containers.isEmpty()) {
            final List<String> cmd = dockerCmd("rm", "-f");
            cmd.addAll(containers);
            exec(cmd);
            LOG.info("removed %d container(s) of compose stack {{b}}%s{{X}}", containers.size(), name);
        }
        for (final String network : execLines(dockerCmd("network", "ls", "-q", "--filter", "label=" + project))) {
            try {
                exec(dockerCmd("network", "rm", network));
                LOG.info("removed network {{b}}%s{{X}} of compose stack {{b}}%s{{X}}", network, name);
            } catch (final Exception e) {
                LOG.warn("network rm %s: %s", network, e.getMessage());
            }
        }
    }

    /**
     * The non-blank stdout lines of a docker command — an id list, one id per line.
     */
    private List<String> execLines(final List<String> cmd) {
        return exec(cmd).stdout().lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
    }

    private void containerRun(final String name, final Rec config) {
        // headless (no terminal) means nothing to draw on: composeUp already guards this way, and
        // a widget constructed with nowhere to render is exactly what breaks a headless docker space
        this.pullWidget = Console.getTerminal() == null ? null :
                (this.progressWidget != null ? this.progressWidget : new ProgressTableWidget());
        final String image = objToString(config.at(uri("image"))
                .orThrow(MTronException.of("container config must have an 'image' field")));
        final List<String> cmd = dockerCmd("run", "-d", "--name", name);
        if (!this.owner().isNoObj()) {
            cmd.add("--label");
            cmd.add(OWNER_LABEL + "=" + objToString(this.owner()));
        }
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
            exec(cmd);
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
        final List<String> cmd = dockerCmd("volume", "create", name);
        if (config.has("driver")) {
            cmd.add("-d");
            cmd.add(objToString(config.at(uri("driver"))));
        }
        try {
            exec(cmd);
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
        final List<String> cmd = dockerCmd("network", "create", name);
        if (config.has("driver")) {
            cmd.add("-d");
            cmd.add(objToString(config.at(uri("driver"))));
        }
        try {
            exec(cmd);
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

    /**
     * The docker command line, host prefix included.  A List rather than an array because half the
     * callers append to it (a `--label`, container ids to `rm`) before running it.
     */
    private List<String> dockerCmd(final String subcommand, final String... args) {
        final List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        if (this.dockerHost != null) {
            cmd.add("-H");
            cmd.add(this.dockerHost);
        }
        cmd.add(subcommand);
        for (final String a : args) cmd.add(a);
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

    private ProcessResult exec(final List<String> cmd) {
        return exec(300, cmd);  // default 5 min for long-running pulls
    }

    /**
     * Run a docker command, capturing stdout and streaming stderr.
     * <p>
     * zt-exec rather than a hand-rolled {@link ProcessBuilder}: the previous version drained stderr on
     * a thread of its own — rendering a progress widget from it whenever a terminal existed — and then
     * blocked in {@code readAllBytes()} on stdout.  A drain that dies or blocks leaves the child writing
     * into a full pipe with the caller waiting forever, which is exactly how a boot came to hang on
     * {@code Container … Recreate} and how a test run wedged on {@code docker network ls}.  A progress
     * bar is not worth a shutdown: {@code ProcessExecutor} owns both streams and the timeout, and kills
     * the child when it expires.
     * <p>
     * stderr is streamed line by line (so it is always being read) and kept only for the failure
     * message.  Docker writes progress there, and layer-progress lines are noise we drop.
     */
    private ProcessResult exec(final int timeoutSecs, final List<String> cmd) {
        final StringBuilder stderr = new StringBuilder();
        try {
            final org.zeroturnaround.exec.ProcessResult result = new ProcessExecutor()
                    .command(cmd)
                    .readOutput(true)
                    .exitValueAny()
                    .timeout(timeoutSecs, TimeUnit.SECONDS)
                    .redirectError(new LogOutputStream() {
                        @Override
                        protected void processLine(final String line) {
                            stderr.append(line).append('\n');
                            final String trimmed = line.trim();
                            // `a1b2c3d4e5f6 Pulling fs layer` is pull progress, not news
                            if (!trimmed.isEmpty() && !trimmed.matches("^[0-9a-f]{12} .*"))
                                LOG.info("{{y}}%s", trimmed);
                        }
                    })
                    .execute();
            if (result.getExitValue() != 0)
                throw MTronException.of("exit %d: %s\n%s", result.getExitValue(), String.join(" ", cmd), stderr);
            return new ProcessResult(result.outputUTF8(), stderr.toString());
        } catch (final MTronException e) {
            throw e;
        } catch (final Exception e) {
            throw MTronException.of("docker cli failed: %s", e.getMessage());
        }
    }
}
