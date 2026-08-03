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

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import studio.phaseshift.metatron.TestData;
import studio.phaseshift.metatron.isa.AbstractSpaceTest;
import studio.phaseshift.metatron.isa.m.type.InstSet;
import studio.phaseshift.metatron.isa.m.type.Obj;
import studio.phaseshift.metatron.isa.mach.type.Router;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static studio.phaseshift.metatron.Tokens.PATTERN;
import static studio.phaseshift.metatron.Tokens.ROUTE;
import static studio.phaseshift.metatron.furi.fURI.Singleton.f;
import static studio.phaseshift.metatron.isa.dckr.dckrInstSet.DCKR_ISA_TID;
import static studio.phaseshift.metatron.isa.m.math.mathInstSet.MATH_ISA_TID;
import static studio.phaseshift.metatron.isa.m.type.NoObj.noobj;
import static studio.phaseshift.metatron.isa.m.type.impl.MLst.lst;
import static studio.phaseshift.metatron.isa.m.type.impl.MRec.rec;
import static studio.phaseshift.metatron.isa.m.type.impl.MStr.str;
import static studio.phaseshift.metatron.isa.m.type.impl.MUri.uri;

/**
 * Tests for {@link dckrSpace}.
 * <p>
 * Type-matching and TopicTrie-backed tests run unconditionally.
 * Tests that shell out to the Docker daemon are guarded by
 * {@link #assumeDockerAvailable()} and are silently skipped when
 * Docker is absent.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class dckrSpaceTest extends AbstractSpaceTest {

    private static boolean dockerAvailable;

    public dckrSpaceTest() {
        super(() -> {
            return dckrSpace.of(rec(
                    uri(PATTERN), uri("dtest:#"),
                    uri(ROUTE), rec(uri("dtest:"), uri(""))), f("/sys/space/docker_test"));
        });
        dockerAvailable = probeDocker();
    }

    @BeforeAll
    public static void setupInstSet() throws Exception {
        InstSet.importInstSet(MATH_ISA_TID);
        InstSet.importInstSet(DCKR_ISA_TID, f("d"));
    }

    // ===================================================================
    // Docker availability probe
    // ===================================================================

    private static boolean probeDocker() {
        try {
            final Process p = new ProcessBuilder("docker", "version", "--format", "{{.Client.Version}}")
                    .redirectErrorStream(true).start();
            final String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(5, TimeUnit.SECONDS);
            STATIC_LOG.info("docker probe: {{b}}%s{{X}}", out.trim());
            return p.exitValue() == 0;
        } catch (final Exception e) {
            STATIC_LOG.warn("docker not available — daemon tests will be skippe %s", e.getMessage());
            return false;
        }
    }

    private static void assumeDockerAvailable() {
        Assumptions.assumeTrue(dockerAvailable, "Docker daemon not available");
    }

    // ===================================================================
    // Type matching — docker type hierarchy
    // ===================================================================

    @Override
    public void testMonoReadWrite(final String writeExpression, final String readExpression, final String expectedExpression) {

    }

    @Override
    public void testMonoRootlessReadWrites() {

    }

    @Override
    public void testMonoUpdate() {

    }

    @Override
    public void testMonoDepth(final String writeExpression, final String readExpression) {

    }

    @Override
    public void testUpdateWrite(final UpdateTestCase tc) {
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvSource(value = {
            // resource
            "[id => abc, created => <2024-01-01>]                              % resource::T    % true  % resource matches itself",
            "[id => 'abc']                                                     % resource::T    % false % id type not a resource",
            "[=>]                                                              % resource::T    % false % empty rec not a resource",
            // image
            //  "[id => abc, repo_tags => [nginx:latest], size => mB::142.0]        % image::T       % true  % image matches image",
            //        "[id => abc, repo_tags => 5, size => mB::142.0]                     % image::T       % true  % bad repo_tags ignored by maybe",
            "[id => abc, repo_tags => [nginx:latest], size => mB::142.0]        % resource::T    % true  % image is a resource",
            "[id => abc, repo_tags => [nginx:latest], size => mB::142.0]        % container::T   % false % image not a container",
            "[id => abc, repo_tags => [nginx:latest], size => mB::142.0]        % compose::T     % false % image not compose",
            // container
            "[id => def, image => nginx, state => running, ports => [<8080:80>]] % container::T   % true  % container matches container",
            "[id => def, image => nginx, state => running, ports => [<8080:80>]] % resource::T    % true  % container is a resource",
            "[id => def, image => nginx, state => running]                       % container::T   % true  % ports optional",
            "[id => def, image => nginx, state => stopped]                       % container::T   % false % stopped not in state union",
            // container state union
            "running                                                                   % container_state::T % true  % running is valid state",
            "exited                                                                    % container_state::T % true  % exited is valid state",
            "paused                                                                    % container_state::T % true  % paused is valid state",
            "dead                                                                      % container_state::T % true  % dead is valid state",
            "stopped                                                                   % container_state::T % false % stopped not valid",
            // compose
            "[services => [web => [image => nginx]]]                               % compose::T     % true  % compose matches compose",
            "[services => [web => [image => nginx]]]                               % resource::T    % false % compose missing id",
            //"[services => [web => [image => nginx]]]                               % image::T       % false % compose missing id",
            //"[repo_tags => [nginx:latest], services => [web => [image => nginx]]]  % image::T       % false % compose missing id",
            // volume
            "[id => v1, driver => local, mountpoint => /var/lib/docker/volumes]     % volume::T      % true  % volume matches volume",
            "[id => v1]                                                             % volume::T      % true  % driver and mountpoint optional",
            // network
            "[id => <n1>, driver => bridge, scope => local]                          % network::T     % true  % network matches network",
            "[id => <n1>, driver => bridge, scope => local]                          % resource::T    % true  % network is a resource",
    }, delimiter = '%')
    void testTypeMatching(final String objExpr, final String typeExpr,
                          final boolean shouldMatch, final String description) {
        checkMatches(LOG, objExpr, typeExpr, shouldMatch);
    }

    // ===================================================================
    // Image size — dimensional data type
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvSource(value = {
            "[id => abc, size => bB::142000000.0] % image::T % true  % bytes match",
            "[id => abc, size => kB::142000.0]  % image::T % true  % kilobytes match",
            "[id => abc, size => mB::142.0]     % image::T % true  % megabytes match",
            "[id => abc, size => gB::0.142]     % image::T % true  % gigabytes match",
            "[id => abc, size => tB::0.000142]  % image::T % true  % terabytes match",
            "[id => abc, size => 142.0]         % image::T % true  % plain real ignored by maybe",
            "[id => abc, size => 142]           % image::T % true  % plain int ignored by maybe",
    }, delimiter = '%')
    void testImageSizeTypes(final String objExpr, final String typeExpr,
                            final boolean shouldMatch, final String description) {
        // checkMatches(LOG, objExpr, typeExpr, shouldMatch);
    }

    // ===================================================================
    // TopicTrie-backed write + read (no Docker daemon needed)
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvSource(value = {
            "dtest:image/nginx -> [id => abc, repo_tags => [nginx:latest], size => mB::142.0] % [id => abc, repo_tags => [nginx:latest], size => mB::142.0] % writes and reads back",
            "dtest:image/nginx/nginx:latest -> [disk_usage => mB::777.0, id => <9a4b30ea58d6>]    % [disk_usage => mB::777.0, id => <9a4b30ea58d6>] % tag-level metadata",
    }, delimiter = '%')
    void testImageWriteRead(final String writeExpr, final String readExpr, final String description) {
        checkCodeParseApply(LOG, writeExpr, readExpr);
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @CsvSource(value = {
            "*dtest:image/nginx/nginx:latest/id  %  <9a4b30ea58d6>  % nested field read",
            "*dtest:image/nginx/nginx:latest/disk_usage  %  mB::777.0  % nested field read",
    }, delimiter = '%')
    @TestData(value = {
            "dtest:image/nginx/nginx:latest -> [disk_usage => mB::777.0, id => <9a4b30ea58d6>]"
    })
    void testRecSubPathRead(final String readPath, final String expected, final String description) {
        checkCodeParseApply(LOG, readPath, expected);
    }

    // ===================================================================
    // Compose config write + sub-path read (pure TopicTrie, no Docker)
    // ===================================================================

    @ParameterizedTest(name = "[{index}] {4}")
    @CsvSource(value = {
            "% *dtest:compose/my-stack/services/web % [image => nginx, ports => [<8080:80>]] % service-level access",
            "% *dtest:compose/my-stack/services/web/image % nginx % deep field access",
            "% *dtest:compose/my-stack/services/web/ports/0 % <8080:80> % list element access",
    }, delimiter = '%')
    @TestData(value = {
            "dtest:compose/my-stack -> [services => [web => [image => nginx, ports => [<8080:80>]]]]"
    })
    void testComposeRecNavigation(final String ignore, final String readPath,
                                  final String expected, final String description) {
        checkCodeParseApply(LOG, readPath, expected);
    }

    // ===================================================================
    // Docker daemon integration (skipped when Docker absent)
    // ===================================================================

    @Test
    @Order(1)
    void testComposeUpDown() {
        assumeDockerAvailable();
        final String stackName = "mtron-test-cud-" + System.currentTimeMillis() % 100000;

        final Obj config = rec(uri("services"),
                rec(uri("nginx"), rec(uri("image"), str("nginx:alpine"))));
        space.write(f("dtest:compose/" + stackName), config);

        final Path yamlFile = Path.of("/tmp", "metatron-docker", stackName, "docker-compose.yml");
        assertTrue(Files.exists(yamlFile), "compose file written");

        // read back running containers
        final Obj containers = space.read(f("dtest:container/+"));
        assertFalse(containers.isNoObj());

        // down
        space.write(f("dtest:compose/" + stackName),
                noobj());
        assertFalse(Files.exists(yamlFile), "compose file cleaned up");
    }

    @Test
    @Order(2)
    void testContainerRunStop() {
        assumeDockerAvailable();
        final String name = "mtron-test-run-" + System.currentTimeMillis() % 100000;

        final Obj runConfig = rec(uri("image"), str("nginx:alpine"));
        space.write(f("dtest:container/" + name), runConfig);

        final Obj containers = space.read(f("dtest:container/+"));
        assertTrue(containers.stream().anyMatch(x -> {
            final Obj n = x.asRec().at(uri("names"));
            return !n.isNoObj() && (n.isStr() ? n.strValue() : n.uriValue().name()).equals(name);
        }), "container should be listed");

        final Obj inspected = space.read(f("dtest:container/" + name));
        assertFalse(inspected.isNoObj());
        LOG.info("inspected {{b}}%s", inspected);

        // stop + rm
        space.write(f("dtest:container/" + name),
                noobj());
    }

    @Test
    @Order(3)
    void testContainerRunWithPortsAndEnv() {
        assumeDockerAvailable();
        final String name = "mtron-test-env-" + System.currentTimeMillis() % 100000;

        final Obj runConfig = rec(
                uri("image"), str("nginx:alpine"),
                uri("ports"), lst(str("0:80")),
                uri("environment"), rec(uri("NGINX_HOST"), str("example.com")));
        Router.writeToSpace(f("dtest:container/" + name), runConfig);

        final Obj inspected = space.read(f("dtest:container/" + name));
        assertFalse(inspected.isNoObj());

        Router.writeToSpace(f("dtest:container/" + name),
                noobj());
    }

    @Test
    @Order(5)
    void testDockerGraphLinks() {
        assumeDockerAvailable();
        final long now = System.currentTimeMillis() % 100000;
        final String name1 = "mtron-test-g1-" + now;
        final String name2 = "mtron-test-g2-" + now;

        // -- Run two containers from the same image --
        final Obj runConfig = rec(uri("image"), str("nginx:alpine"));
        space.write(f("dtest:container/" + name1), runConfig);
        space.write(f("dtest:container/" + name2), runConfig);

        try {
            // -- Image-side: containers field is a list of URIs --
            final Obj image = space.read(f("dtest:image/nginx:alpine"));
            assertFalse(image.isNoObj(), "image should be addressable by repository:tag");
            LOG.info("image {{b}}%s", image);

            final Obj imageContainers = image.asRec().at(uri("containers"));
            assertFalse(imageContainers.isNoObj(),
                    "image.containers should not be noobj (Docker 'Containers' count should be overwritten)");
            assertTrue(imageContainers.isLst(),
                    "image.containers should be a list, got: " + imageContainers);
            assertTrue(imageContainers.asLst().count() >= 2,
                    "image.containers should have at least 2 entries, got: " + imageContainers.asLst().count());

            // Each entry should be a URI pointing to a container
            imageContainers.asLst().elements().forEach(ref -> {
                assertTrue(ref.isUri(),
                        "each containers entry should be a URI, got: " + ref);
                final String refStr = ref.uriValue().toString();
                assertTrue(refStr.startsWith("dtest:container/"),
                        "ref should be under dtest:container/, got: " + refStr);
            });

            // Verify both containers are in the list
            final List<String> refStrings = imageContainers.asLst().elements()
                    .map(ref -> ref.uriValue().toString())
                    .toList();
            assertTrue(refStrings.stream().anyMatch(s -> s.contains(name1)),
                    "containers should include " + name1 + ", got: " + refStrings);
            assertTrue(refStrings.stream().anyMatch(s -> s.contains(name2)),
                    "containers should include " + name2 + ", got: " + refStrings);

            // -- Container-side: image field is a URI into dckrSpace --
            for (final String name : List.of(name1, name2)) {
                final Obj container = space.read(f("dtest:container/" + name));
                assertFalse(container.isNoObj(), "container " + name + " should exist");

                final Obj imageField = container.asRec().at(uri("image"));
                assertFalse(imageField.isNoObj(),
                        "container.image should not be noobj");
                assertTrue(imageField.isUri(),
                        "container.image should be a URI, got: " + imageField);
                final String imageRef = imageField.uriValue().toString();
                assertTrue(imageRef.contains("nginx:alpine"),
                        "container.image URI should reference nginx:alpine, got: " + imageRef);
                LOG.info("container {{b}}%s{{X}} -> image ref {{y}}%s", name, imageRef);
            }

            // -- Direct image lookup by repo:tag vs hash works --
            final Obj imageByTag = space.read(f("dtest:image/nginx:alpine"));
            assertFalse(imageByTag.isNoObj(), "image lookup by repo:tag should work");
            final String imageHash = imageByTag.asRec().at(uri("id")).isNoObj()
                    ? null : imageByTag.asRec().at(uri("id")).toString();
            if (imageHash != null) {
                final Obj imageByHash = space.read(f("dtest:image/" + imageHash));
                // May or may not exist depending on key strategy; at minimum tag-lookup works
                LOG.info("image by hash {{b}}%s{{X}}: %s", imageHash,
                        imageByHash.isNoObj() ? "not found" : "found");
            }
        } finally {
            // Clean up both containers
            space.write(f("dtest:container/" + name1), noobj());
            space.write(f("dtest:container/" + name2), noobj());
        }
    }

    @Test
    @Order(6)
    void testNetworkGraphLinks() {
        assumeDockerAvailable();
        final long now = System.currentTimeMillis() % 100000;
        final String name = "mtron-test-net-" + now;

        // Run a container on the default bridge network
        final Obj runConfig = rec(uri("image"), str("nginx:alpine"));
        space.write(f("dtest:container/" + name), runConfig);

        try {
            // -- Network-side: containers field is a list --
            final Obj network = space.read(f("dtest:network/bridge"));
            assertFalse(network.isNoObj(), "bridge network should exist");
            LOG.info("bridge network {{b}}%s", network);

            final Obj netContainers = network.asRec().at(uri("containers"));
            assertFalse(netContainers.isNoObj(),
                    "network.containers should not be noobj");
            assertTrue(netContainers.isLst(),
                    "network.containers should be a list, got: " + netContainers);
            assertTrue(netContainers.asLst().count() >= 1,
                    "network.containers should have at least 1 entry");

            // Each entry is a URI
            netContainers.asLst().elements().forEach(ref -> {
                assertTrue(ref.isUri(),
                        "each network.containers entry should be a URI, got: " + ref);
                assertTrue(ref.uriValue().toString().startsWith("dtest:container/"),
                        "ref should be under dtest:container/, got: " + ref.uriValue().toString());
            });

            // Our container should be in the list
            final boolean found = netContainers.asLst().elements()
                    .anyMatch(ref -> ref.uriValue().toString().contains(name));
            assertTrue(found, "network.containers should include " + name);

            // -- Container-side: networks field is a URI --
            final Obj container = space.read(f("dtest:container/" + name));
            final Obj netField = container.asRec().at(uri("networks"));
            assertFalse(netField.isNoObj(), "container.networks should not be noobj");
            assertTrue(netField.isUri(),
                    "container.networks should be a URI, got: " + netField);
            assertTrue(netField.uriValue().toString().contains("bridge"),
                    "container.networks should reference bridge, got: " + netField);
            LOG.info("container {{b}}%s{{X}} -> network ref {{y}}%s", name, netField);

            // -- Direct network lookup works --
            final Obj bridge = space.read(f("dtest:network/bridge"));
            assertFalse(bridge.isNoObj(), "network lookup by name should work");
            assertTrue(bridge.asRec().at(uri("name")).toString().contains("bridge"),
                    "network should have name=bridge");
        } finally {
            space.write(f("dtest:container/" + name), noobj());
        }
    }

    @Test
    @Order(7)
    void testVolumeGraphLinks() {
        assumeDockerAvailable();
        final long now = System.currentTimeMillis() % 100000;
        final String volName = "mtron-test-vol-" + now;
        final String containerName = "mtron-test-volc-" + now;

        // Create a named volume and run a container with it mounted
        space.write(f("dtest:volume/" + volName), rec());
        space.write(f("dtest:container/" + containerName),
                rec(uri("image"), str("nginx:alpine"),
                        uri("volumes"), lst(str(volName + ":/data"))));

        try {
            // -- Volume-side: containers field is a list --
            final Obj volume = space.read(f("dtest:volume/" + volName));
            assertFalse(volume.isNoObj(), "volume should exist");

            final Obj volContainers = volume.asRec().at(uri("containers"));
            assertFalse(volContainers.isNoObj(),
                    "volume.containers should not be noobj");
            assertTrue(volContainers.isLst(),
                    "volume.containers should be a list, got: " + volContainers);
            assertTrue(volContainers.asLst().count() >= 1,
                    "volume.containers should have at least 1 entry");

            volContainers.asLst().elements().forEach(ref -> {
                assertTrue(ref.isUri(),
                        "each volume.containers entry should be a URI, got: " + ref);
                assertTrue(ref.uriValue().toString().startsWith("dtest:container/"),
                        "ref should be under dtest:container/");
            });

            final boolean found = volContainers.asLst().elements()
                    .anyMatch(ref -> ref.uriValue().toString().contains(containerName));
            assertTrue(found, "volume.containers should include " + containerName);

            // -- Container-side: mounts field is a list of URIs --
            final Obj container = space.read(f("dtest:container/" + containerName));
            final Obj mountsField = container.asRec().at(uri("mounts"));
            assertFalse(mountsField.isNoObj(), "container.mounts should not be noobj");
            assertTrue(mountsField.isLst(),
                    "container.mounts should be a list, got: " + mountsField);

            final boolean volRefFound = mountsField.asLst().elements()
                    .anyMatch(ref -> ref.isUri() && ref.uriValue().toString().contains(volName));
            assertTrue(volRefFound,
                    "container.mounts should contain a ref to " + volName + ", got: " + mountsField);
            LOG.info("container {{b}}%s{{X}} -> volume refs {{y}}%s", containerName, mountsField);

            // -- Direct volume lookup works --
            final Obj volDirect = space.read(f("dtest:volume/" + volName));
            assertFalse(volDirect.isNoObj(), "volume lookup by name should work");
        } finally {
            space.write(f("dtest:container/" + containerName), noobj());
            space.write(f("dtest:volume/" + volName), noobj());
        }
    }
}
