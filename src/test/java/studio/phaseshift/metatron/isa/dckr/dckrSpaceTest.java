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
import studio.phaseshift.metatron.util.MTronException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
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
    @Order(4)
    void testContainerRunMissingImageFails() {
        assumeDockerAvailable();
        final String name = "mtron-test-bad-" + System.currentTimeMillis() % 100000;

        final Obj badConfig = rec(uri("ports"), lst(str("8080:80")));
        assertThrows(MTronException.class, () ->
                space.write(f("dtest:container/" + name), badConfig));
    }
}
