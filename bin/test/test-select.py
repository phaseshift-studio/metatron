#!/usr/bin/env python3
"""bin/test/test-select.py — the test of bin/test-select.

Two parts. A throwaway repository whose src/test holds every shape of user the selector must find (or must not:
longer identifiers, a file name inside a longer one, a base class or a helper that names the change only in a
comment). And the three incidents the selector exists for, checked against this repository's own tree: PeerNode's
users reach MariaDBMultiServerSingleSpaceTest through PeerCluster and an abstract base; mParser.m_furi is called
by fURITest; fsSpace is mounted by llmInstSetTest, sysInstSetTest and ObjZipSerializerTest. Needs git and python3.

    python3 bin/test/test-select.py
"""
import os
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

BIN = Path(__file__).resolve().parent.parent
REPO = BIN.parent
SCRIPT = BIN / 'test-select'

TEST_JAVA = 'src/test/java/org/harbor/'
HARBOR = {
    'src/main/java/org/harbor/Beacon.java':
        'package org.harbor;\npublic class Beacon { public static int flash(int n) { return n; } }\n',
    'src/main/java/org/harbor/tideSpace.java': 'package org.harbor;\npublic class tideSpace { }\n',
    'src/main/mtron/harbor/harbor.mtron': 'sound -> |sound?int<=int(){_.plus(1)};\ninst?int<=int(){_.mult(2)}@chime\n',
    'src/test/resources/harbor/chart.csv': 'Beacon(1) % 1\n',
    'boot/harbor.boot.mtron':
        '[-- boot/harbor.boot.mtron: the harbor --]\ntidespace::[pattern=>tide:#]@~/memory/space/tide;\n',
    'docs/beacon.md': '# the beacon\n',
    # users the selector must find
    TEST_JAVA + 'RowTest.java':
        'class RowTest { @CsvSource(delimiter = \'%\', value = {"Beacon(3) % 3"}) void t() {} }',
    TEST_JAVA + 'QuoteTest.java': 'class QuoteTest { String row = "x.as(\'Beacon\')"; }',
    TEST_JAVA + 'PercentTest.java': 'class PercentTest { String row = "%Beacon%"; }',
    TEST_JAVA + 'MemberTest.java': 'class MemberTest { int x = org.harbor.Beacon.flash(1); }',
    TEST_JAVA + 'DriftTest.java': 'class DriftTest {\n    // the drift follows Beacon\'s light\n}',
    TEST_JAVA + 'TestLamp.java': 'class TestLamp { Beacon b; }',  # surefire's other test names
    TEST_JAVA + 'MastTests.java': 'class MastTests { Beacon b; }',
    TEST_JAVA + 'RiggingTestCase.java': 'class RiggingTestCase { Beacon b; }',
    TEST_JAVA + 'KeelTest.java': 'public class KeelTest { Beacon b; }',
    TEST_JAVA + 'DeepKeelTest.java': 'public class DeepKeelTest extends KeelTest { }',
    TEST_JAVA + 'AbstractHarborTest.java': textwrap.dedent('''\
        /** the harbor's base: every test below runs with a beacon */
        public abstract class AbstractHarborTest<T extends Comparable<T>> {
            protected final Beacon beacon = new Beacon();
        }'''),
    TEST_JAVA + 'PierTest.java': 'public class PierTest extends AbstractHarborTest<String> { }',
    TEST_JAVA + 'AbstractDeepHarborTest.java':
        'public abstract class AbstractDeepHarborTest<T extends Comparable<T>> extends AbstractHarborTest<T> { }',
    TEST_JAVA + 'TrenchTest.java':
        'public final class TrenchTest extends org.harbor.AbstractDeepHarborTest<Integer> { }',
    TEST_JAVA + 'HarborCheck.java': 'public class HarborCheck extends AbstractHarborTest<String> { }',
    TEST_JAVA + 'HarborCheckTest.java': 'public class HarborCheckTest extends HarborCheck { }',
    TEST_JAVA + 'Mooring.java': 'public interface Mooring { default int tie() { return Beacon.flash(2); } }',
    TEST_JAVA + 'BollardTest.java': 'public class BollardTest implements java.io.Serializable, Mooring { }',
    TEST_JAVA + 'Lantern.java': 'final class Lantern { int lit() { return Beacon.flash(3); } }',
    TEST_JAVA + 'LanternTest.java': 'class LanternTest { Lantern lantern; }',
    TEST_JAVA + 'Wick.java': 'final class Wick { Lantern lantern; }',
    TEST_JAVA + 'WickTest.java': 'class WickTest { Wick wick; }',
    TEST_JAVA + 'Logbook.java': 'public @interface Logbook { Class<?> value() default Beacon.class; }',
    TEST_JAVA + 'CaptainTest.java': '@Logbook\nclass CaptainTest { }',
    TEST_JAVA + 'ChartTest.java': 'class ChartTest { String data = "harbor/chart.csv"; }',
    TEST_JAVA + 'HarborBootTest.java': 'class HarborBootTest { String boot = "boot/harbor.boot.mtron"; }',
    TEST_JAVA + 'TideSpaceTest.java': 'class TideSpaceTest { Object space = tideSpace.of(); }',
    TEST_JAVA + 'TideStringTest.java': 'class TideStringTest { String row = "tidespace::[pattern=>tide:#]"; }',
    TEST_JAVA + 'SoundTest.java': 'class SoundTest { String row = "3.sound() % 4"; }',
    TEST_JAVA + 'ChimeTest.java': 'class ChimeTest { String row = "chime(2) % 4"; }',
    TEST_JAVA + 'HarborMtronTest.java': 'class HarborMtronTest { String file = "harbor/harbor.mtron"; }',
    TEST_JAVA + 'KelpTest.java': 'class KelpTest { Kelp kelp; }',
    # names the selector must not take for users
    TEST_JAVA + 'LongerNameTest.java': 'class LongerNameTest { BeaconLight a; Object myBeacon, Beacon_2, $Beacon; }',
    TEST_JAVA + 'AbstractCommentedTest.java': '/** unlike {@link Beacon}, nothing here shines */\n'
                                              'public abstract class AbstractCommentedTest { }',
    TEST_JAVA + 'QuietTest.java': 'public class QuietTest extends AbstractCommentedTest { }',
    TEST_JAVA + 'Gangway.java': '/** a gangway, lit like {@link Beacon} */\nfinal class Gangway { }',
    TEST_JAVA + 'GangwayTest.java': 'class GangwayTest { Gangway gangway; }',
    TEST_JAVA + 'NauticalTest.java': 'class NauticalTest { String data = "harbor/nautical.chart.csv"; }',
}
SELECTED_FOR_BEACON = {'RowTest', 'QuoteTest', 'PercentTest', 'MemberTest', 'DriftTest', 'KeelTest', 'DeepKeelTest',
                       'TestLamp', 'MastTests', 'RiggingTestCase',
                       'PierTest', 'TrenchTest', 'HarborCheckTest', 'BollardTest', 'LanternTest', 'WickTest',
                       'CaptainTest', 'ChartTest'}


def selected(stdout: str) -> set[str]:
    lines = stdout.splitlines()
    assert len(lines) == 1 and lines[0].startswith('-Dtest='), stdout  # stdout is the argument and nothing else
    return set(lines[0][len('-Dtest='):].split(','))


class SyntheticHarborTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='test-select-test-')
        self.root = Path(self.tmp.name)
        (self.root / 'gitconfig').write_text('[user]\n\tname = Harbor Keeper\n\temail = keeper@harbor.test\n'
                                             '[init]\n\tdefaultBranch = main\n')
        self.env = dict(os.environ, GIT_CONFIG_GLOBAL=str(self.root / 'gitconfig'), GIT_CONFIG_NOSYSTEM='1')
        self.repo = self.root / 'harbor'
        for path, text in HARBOR.items():
            (self.repo / path).parent.mkdir(parents=True, exist_ok=True)
            (self.repo / path).write_text(text)
        (self.repo / 'src/test/resources/harbor/flag.bin').write_bytes(b'\x89Beacon\x00\x01')  # binary: no user
        self.git('init', '-q')
        self.git('add', '-A')
        self.git('commit', '-qm', 'the harbor')

    def tearDown(self):
        self.tmp.cleanup()

    def git(self, *args: str) -> None:
        subprocess.run(['git', *args], cwd=self.repo, env=self.env, check=True, capture_output=True)

    def select(self, *args: str) -> subprocess.CompletedProcess:
        return subprocess.run([sys.executable, str(SCRIPT), *args], cwd=self.repo, env=self.env, capture_output=True,
                              text=True, timeout=120)

    def test_every_shape_of_user_and_no_lookalike(self):
        r = self.select('Beacon')
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertEqual(selected(r.stdout), SELECTED_FOR_BEACON)
        for trail in ('Lantern (followed)', 'Wick (followed)', 'Logbook (followed)',
                      'AbstractHarborTest (abstract: 3 test subclasses)', 'Mooring (abstract: 1 test subclass)',
                      '  chart.csv: ChartTest\n', '  Lantern: LanternTest, Wick (followed)\n',
                      '  Logbook: CaptainTest\n'):
            self.assertIn(trail, r.stderr)
        self.assertNotIn('flag.bin', r.stderr)

    def test_a_file_a_class_and_a_member_name_the_same_users(self):
        by_file = self.select('src/main/java/org/harbor/Beacon.java')
        by_path = self.select(str(self.repo / 'src/main/java/org/harbor/Beacon.java'))
        by_member = self.select('org.harbor.Beacon')
        self.assertEqual(selected(by_file.stdout), SELECTED_FOR_BEACON)
        self.assertEqual(selected(by_path.stdout), SELECTED_FOR_BEACON)
        self.assertEqual(selected(by_member.stdout), SELECTED_FOR_BEACON)

    def test_a_lowercase_class_is_also_searched_by_its_mtron_spelling(self):
        r = self.select('src/main/java/org/harbor/tideSpace.java')
        self.assertEqual(selected(r.stdout), {'TideSpaceTest', 'TideStringTest', 'HarborBootTest'})
        self.assertIn('boot files that use them: boot/harbor.boot.mtron', r.stderr)
        self.assertIn('  harbor.boot.mtron: HarborBootTest\n', r.stderr)  # the boot file names itself: not a user
        self.assertEqual(selected(self.select('Beacon').stdout) & {'TideStringTest'}, set())  # PascalCase: no spelling

    def test_an_mtron_file_is_searched_by_the_names_it_defines(self):
        r = self.select('src/main/mtron/harbor/harbor.mtron')
        self.assertEqual(selected(r.stdout), {'SoundTest', 'ChimeTest', 'HarborMtronTest'})
        self.assertNotIn('  inst:', r.stderr)

    def test_the_default_is_the_branch_against_main_with_the_working_tree(self):
        self.git('checkout', '-qb', 'feature')
        beacon = self.repo / 'src/main/java/org/harbor/Beacon.java'
        beacon.write_text(beacon.read_text().replace('return n;', 'return n + 1;'))
        (self.repo / 'docs/beacon.md').write_text('# the beacon, brighter\n')
        self.git('commit', '-qam', 'feat(harbor): a brighter beacon')
        harbor = self.repo / 'src/main/mtron/harbor/harbor.mtron'
        harbor.write_text(harbor.read_text() + '\n')  # uncommitted
        (self.repo / 'src/main/java/org/harbor/Kelp.java').write_text('class Kelp { }\n')  # untracked
        r = self.select()
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertEqual(selected(r.stdout), SELECTED_FOR_BEACON | {'SoundTest', 'ChimeTest', 'HarborMtronTest',
                                                                    'KelpTest'})
        self.assertIn('names of 3 inputs (1 outside src/ and boot/ ignored: docs/beacon.md)', r.stderr)

    def test_no_user_prints_nothing_and_fails(self):
        r = self.select('Lighthouse')
        self.assertEqual(r.returncode, 1)
        self.assertEqual(r.stdout, '')  # never `-Dtest=` alone: that runs the whole suite
        self.assertIn('no test class uses these names', r.stderr)
        self.assertIn('  Lighthouse: no users', r.stderr)

    def test_explain_names_the_chain_and_a_wide_list_is_flagged(self):
        r = self.select('--explain', 'Beacon')
        self.assertIn('WickTest <- Wick <- Lantern <- Beacon', r.stderr)
        self.assertIn('TrenchTest <- AbstractHarborTest <- Beacon', r.stderr)
        self.assertNotIn('wide radius', r.stderr)
        for k in range(41):
            (self.repo / TEST_JAVA / f'Gull{k:02d}Test.java').write_text(f'class Gull{k:02d}Test {{ Gull gull; }}')
        r = self.select('Gull')
        self.assertEqual(len(selected(r.stdout)), 41)
        self.assertIn('41 test classes: a wide radius', r.stderr)


class RecordedIncidentsTest(unittest.TestCase):
    """The incidents of observations 0020 and 0033, on this repository's tree."""

    def select(self, *args: str) -> set[str]:
        r = subprocess.run([sys.executable, str(SCRIPT), *args], cwd=REPO, capture_output=True, text=True,
                           timeout=300)
        self.assertEqual(r.returncode, 0, r.stderr)
        return selected(r.stdout)

    def test_peer_node_reaches_the_mariadb_multi_server_test(self):
        self.assertIn('MariaDBMultiServerSingleSpaceTest', self.select('PeerNode'))
        self.assertIn('MariaDBMultiServerSingleSpaceTest',
                      self.select('src/test/java/studio/phaseshift/metatron/isa/m/mach/network/PeerNode.java'))

    def test_the_bare_uri_parser_reaches_furi_test(self):
        self.assertIn('fURITest', self.select('mParser.m_furi'))

    def test_fs_space_reaches_the_classes_that_mount_it(self):
        mounts = {'llmInstSetTest', 'sysInstSetTest', 'ObjZipSerializerTest'}
        self.assertEqual(self.select('fsSpace') & mounts, mounts)
        self.assertEqual(self.select('src/main/java/studio/phaseshift/metatron/isa/m/sys/space/fsSpace.java')
                         & mounts, mounts)


if __name__ == '__main__':
    unittest.main(verbosity=2)
