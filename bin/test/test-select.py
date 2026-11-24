#!/usr/bin/env python3
"""bin/test/test-select.py — the test of bin/test-select.

Two parts. A throwaway repository whose src/test holds every shape of user the selector must find (or must not:
longer identifiers, a file name inside a longer one, a base class or a helper that names the change only in a
comment, a class whose only annotation is no test annotation, a class or interface with tests named after an @ in
a string). And rows on this repository's own tree: the three
incidents the selector exists for (PeerNode's users reach MariaDBMultiServerSingleSpaceTest through PeerCluster
and an abstract base; mParser.m_furi is called by fURITest; fsSpace is mounted by llmInstSetTest, sysInstSetTest
and ObjZipSerializerTest), the classes whose tests carry a composed annotation, and the test-class rule over every
class it could misjudge. Needs git and python3.

    python3 bin/test/test-select.py
"""
import os
import re
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

BIN = Path(__file__).resolve().parent.parent
REPO = BIN.parent
SCRIPT = BIN / 'test-select'
sys.path.insert(0, str(BIN))
from lib.java_lexer import lex  # noqa: E402
from lib.test_runs import MOST_CLASSES  # noqa: E402

TEST_JAVA = 'src/test/java/org/harbor/'
HARBOR = {
    'src/main/java/org/harbor/Beacon.java':
        'package org.harbor;\npublic class Beacon { public static int flash(int n) { return n; } }\n',
    'src/main/java/org/harbor/tideSpace.java': 'package org.harbor;\npublic class tideSpace { }\n',
    'src/main/mtron/harbor/harbor.mtron': 'sound -> |sound?int<=int(){_.plus(1)};\ninst?int<=int(){_.mult(2)}@chime;\n'
                                          '[knots => |inst?int<=int(){_.mult(3)}];\nfathom?int<=int(){_.mult(6)};\n',
    'src/test/resources/harbor/chart.csv': 'Beacon(1) % 1\n',
    'src/test/resources/scratch/EchoTest.java': 'class EchoTest { Beacon b; @Test void t() {} }',  # data, not a class
    'boot/harbor.boot.mtron':
        '[-- boot/harbor.boot.mtron: the harbor --]\ntidespace::[pattern=>tide:#]@~/memory/space/tide;\n',
    'docs/beacon.md': '# the beacon\n',
    # users the selector must find
    TEST_JAVA + 'RowTest.java':
        'class RowTest { @ParameterizedTest @CsvSource(delimiter = \'%\', value = {"Beacon(3) % 3"}) void t() {} }',
    TEST_JAVA + 'QuoteTest.java': 'class QuoteTest { String row = "x.as(\'Beacon\')"; @Test void t() {} }',
    TEST_JAVA + 'PercentTest.java': 'class PercentTest { String row = "%Beacon%"; @Test void t() {} }',
    TEST_JAVA + 'MemberTest.java': 'class MemberTest { int x = org.harbor.Beacon.flash(1); @Test void t() {} }',
    TEST_JAVA + 'DriftTest.java':
        'class DriftTest {\n    // the drift follows Beacon\'s light\n    @Test void t() {}\n}',
    TEST_JAVA + 'TestLamp.java': 'class TestLamp { Beacon b; @Test void t() {} }',  # surefire's other test names
    TEST_JAVA + 'MastTests.java': 'class MastTests { Beacon b; @RepeatedTest(2) void t() {} }',
    TEST_JAVA + 'RiggingTestCase.java': 'class RiggingTestCase { Beacon b; @TestFactory Object t() { return null; } }',
    TEST_JAVA + 'KeelTest.java': 'public class KeelTest { Beacon b; @Test void t() {} }',
    TEST_JAVA + 'Squall.java': '@TestTemplate\npublic @interface Squall { }',  # a composed test annotation
    TEST_JAVA + 'Gust.java': '@Squall\npublic @interface Gust { }',  # composed from a composed one
    TEST_JAVA + 'GaleTest.java': 'class GaleTest { Beacon b; @Squall void t() {} }',
    TEST_JAVA + 'GustTest.java': 'class GustTest { Beacon b; @Gust void t() {} }',
    TEST_JAVA + 'DeepKeelTest.java': 'public class DeepKeelTest extends KeelTest { }',
    TEST_JAVA + 'AbstractHarborTest.java': textwrap.dedent('''\
        /** the harbor's base: every test below runs with a beacon */
        public abstract class AbstractHarborTest<T extends Comparable<T>> {
            protected final Beacon beacon = new Beacon();

            @Test
            void moors() {}
        }'''),
    TEST_JAVA + 'PierTest.java': 'public class PierTest extends AbstractHarborTest<String> { }',
    TEST_JAVA + 'AbstractDeepHarborTest.java':
        'public abstract class AbstractDeepHarborTest<T extends Comparable<T>> extends AbstractHarborTest<T> { }',
    TEST_JAVA + 'TrenchTest.java':
        'public final class TrenchTest extends org.harbor.AbstractDeepHarborTest<Integer> { }',
    TEST_JAVA + 'HarborCheck.java': 'public class HarborCheck extends AbstractHarborTest<String> { }',
    TEST_JAVA + 'HarborCheckTest.java': 'public class HarborCheckTest extends HarborCheck { }',
    TEST_JAVA + 'Mooring.java': 'public interface Mooring { @Test default void tie() { Beacon.flash(2); } }',
    TEST_JAVA + 'BollardTest.java': 'public class BollardTest implements java.io.Serializable, Mooring { }',
    TEST_JAVA + 'Lantern.java': 'final class Lantern { int lit() { return Beacon.flash(3); } }',
    TEST_JAVA + 'LanternTest.java': 'class LanternTest { Lantern lantern; @Test void t() {} }',
    TEST_JAVA + 'Wick.java': 'final class Wick { Lantern lantern; }',
    TEST_JAVA + 'WickTest.java': 'class WickTest { Wick wick; @Test void t() {} }',
    TEST_JAVA + 'Logbook.java': 'public @interface Logbook { Class<?> value() default Beacon.class; }',
    TEST_JAVA + 'CaptainTest.java': '@Logbook\nclass CaptainTest { @Test void t() {} }',
    TEST_JAVA + 'ChartTest.java': 'class ChartTest { String data = "harbor/chart.csv"; @Test void t() {} }',
    TEST_JAVA + 'ScratchTest.java': 'class ScratchTest { String data = "scratch/EchoTest.java"; @Test void t() {} }',
    TEST_JAVA + 'HarborBootTest.java':
        'class HarborBootTest { String boot = "boot/harbor.boot.mtron"; @Test void t() {} }',
    TEST_JAVA + 'TideSpaceTest.java': 'class TideSpaceTest { Object space = tideSpace.of(); @Test void t() {} }',
    TEST_JAVA + 'TideStringTest.java':
        'class TideStringTest { String row = "tidespace::[pattern=>tide:#]"; @Test void t() {} }',
    TEST_JAVA + 'SoundTest.java': 'class SoundTest { String row = "3.sound() % 4"; @Test void t() {} }',
    TEST_JAVA + 'ChimeTest.java': 'class ChimeTest { String row = "chime(2) % 4"; @Test void t() {} }',
    TEST_JAVA + 'KnotsTest.java': 'class KnotsTest { String row = "2.knots() % 6"; @Test void t() {} }',
    TEST_JAVA + 'FathomTest.java': 'class FathomTest { String row = "fathom(1) % 6"; @Test void t() {} }',
    TEST_JAVA + 'HarborMtronTest.java':
        'class HarborMtronTest { String file = "harbor/harbor.mtron"; @Test void t() {} }',
    TEST_JAVA + 'KelpTest.java': 'class KelpTest { Kelp kelp; @Test void t() {} }',
    # names the selector must not take for users
    TEST_JAVA + 'LongerNameTest.java':
        'class LongerNameTest { BeaconLight a; Object myBeacon, Beacon_2, $Beacon; @Test void t() {} }',
    TEST_JAVA + 'FlagsTest.java': 'class FlagsTest { Beacon b; @TestReport void notATest() {} }',  # no test method
    # a class or an interface named after an @ is no annotation: only an annotation type makes a test annotation
    TEST_JAVA + 'LogTest.java': 'class LogTest { Beacon b; String vids = "1@KeelTest 2@Mooring"; void entry() {} }',
    TEST_JAVA + 'TestReport.java':  # an annotation that carries no test annotation: FlagsTest stays no test
        '@Retention(RetentionPolicy.RUNTIME)\npublic @interface TestReport { }',
    TEST_JAVA + 'AbstractCommentedTest.java': '/** unlike {@link Beacon}, nothing here shines */\n'
                                              'public abstract class AbstractCommentedTest { }',
    TEST_JAVA + 'QuietTest.java': 'public class QuietTest extends AbstractCommentedTest { @Test void t() {} }',
    TEST_JAVA + 'Gangway.java': '/** a gangway, lit like {@link Beacon} */\nfinal class Gangway { }',
    TEST_JAVA + 'GangwayTest.java': 'class GangwayTest { Gangway gangway; @Test void t() {} }',
    TEST_JAVA + 'NauticalTest.java':
        'class NauticalTest { String data = "harbor/nautical.chart.csv"; @Test void t() {} }',
}
SELECTED_FOR_BEACON = {'RowTest', 'QuoteTest', 'PercentTest', 'MemberTest', 'DriftTest', 'KeelTest', 'DeepKeelTest',
                       'TestLamp', 'MastTests', 'RiggingTestCase',
                       'PierTest', 'TrenchTest', 'HarborCheckTest', 'BollardTest', 'LanternTest', 'WickTest',
                       'CaptainTest', 'ChartTest', 'ScratchTest', 'GaleTest', 'GustTest'}


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
        r = self.select('--explain', 'Beacon')
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertEqual(selected(r.stdout), SELECTED_FOR_BEACON)
        for chain in ('LanternTest <- Lantern <- Beacon', 'WickTest <- Wick <- Lantern <- Beacon',  # helpers followed
                      'CaptainTest <- Logbook <- Beacon',  # an annotation followed
                      'PierTest <- AbstractHarborTest <- Beacon', 'BollardTest <- Mooring <- Beacon',  # subclasses
                      'ChartTest <- chart.csv <- Beacon',  # a resource followed by its file name
                      'ScratchTest <- EchoTest.java <- Beacon'):  # a .java under resources is data, followed too
            self.assertIn(f'  {chain}\n', r.stderr)
        for line in ('  Lantern: LanternTest, Wick (followed)', '  Logbook: CaptainTest', '  chart.csv: ChartTest',
                     '  EchoTest.java: ScratchTest'):  # a followed name's own trail line: a user or two, never cut
            self.assertIn(line + '\n', r.stderr)
        self.assertNotIn('flag.bin', r.stderr)
        unused = next((line for line in r.stderr.splitlines() if line.startswith('  followed, with no users:')), '')
        for helper in ('FlagsTest', 'LogTest'):  # a test's name, but no test method: followed as helpers
            self.assertIn(helper, unused, r.stderr)

    def test_the_trail_labels_each_user(self):  # inputs narrow enough that their trail line is never cut
        for name, line in (('flash', 'Lantern (followed), MemberTest, Mooring (abstract: 1 test subclass)'),
                           ('beacon', 'AbstractHarborTest (abstract: 3 test subclasses)'),
                           ('AbstractHarborTest', 'AbstractDeepHarborTest (abstract: 1 test subclass), '
                                                  'HarborCheck (followed, 1 test subclass), PierTest')):
            self.assertIn(f'  {name}: {line}\n', self.select(name).stderr)

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
        # a file user's label: the other producer of "(followed)", beside a helper class's
        self.assertIn('  tidespace: TideStringTest, boot/harbor.boot.mtron (followed)\n', r.stderr)
        self.assertEqual(selected(self.select('Beacon').stdout) & {'TideStringTest'}, set())  # PascalCase: no spelling

    def test_an_mtron_file_is_searched_by_the_names_it_defines(self):
        r = self.select('src/main/mtron/harbor/harbor.mtron')
        self.assertEqual(selected(r.stdout), {'SoundTest', 'ChimeTest', 'HarborMtronTest', 'KnotsTest', 'FathomTest'})
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
                                                                    'KnotsTest', 'FathomTest', 'KelpTest'})
        self.assertIn('names of 3 inputs (1 outside src/ and boot/ ignored: docs/beacon.md)', r.stderr)

    def test_help_prints_the_usage(self):
        r = self.select('--help')
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertTrue(r.stdout.startswith('bin/test-select — the test classes that use'), r.stdout[:80])
        self.assertIn(f'longer than {MOST_CLASSES}\nclasses is flagged', r.stdout)  # the limit's value, from one place

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
        first = ', '.join(f'Gull{k:02d}Test' for k in range(12))  # a trail line shows a name's first 12 users
        for k in range(MOST_CLASSES + 1):
            (self.repo / TEST_JAVA / f'Gull{k:02d}Test.java').write_text(
                f'class Gull{k:02d}Test {{ Gull gull; @Test void t() {{}} }}')
            if k in (11, 12):  # where the cut starts: 12 users show whole, a 13th is counted
                tail = ' and 1 more' if k == 12 else ''
                self.assertIn(f'  Gull: {first}{tail}\n', self.select('Gull').stderr)
            if k == MOST_CLASSES - 1:  # the limit itself is not wide
                r = self.select('Gull')
                self.assertEqual(len(selected(r.stdout)), MOST_CLASSES)
                self.assertNotIn('wide radius', r.stderr)
        r = self.select('Gull')
        self.assertEqual(len(selected(r.stdout)), MOST_CLASSES + 1)
        self.assertIn(f'{MOST_CLASSES + 1} test classes: a wide radius', r.stderr)
        self.assertIn(f'  Gull: {first} and {MOST_CLASSES + 1 - 12} more\n', r.stderr)


class RecordedIncidentsTest(unittest.TestCase):
    """On this repository's tree: the incidents of observations 0020 and 0033, the classes whose tests carry a
    composed annotation, and the test-class rule over every class it could misjudge."""

    def select(self, *args: str) -> set[str]:
        r = subprocess.run([sys.executable, str(SCRIPT), *args], cwd=REPO, capture_output=True, text=True,
                           timeout=300)
        self.assertEqual(r.returncode, 0, r.stderr)
        return selected(r.stdout)

    def test_peer_node_reaches_the_mariadb_multi_server_test(self):
        self.assertIn('MariaDBMultiServerSingleSpaceTest', self.select('PeerNode'))
        self.assertIn('MariaDBMultiServerSingleSpaceTest',
                      self.select('src/test/java/studio/phaseshift/metatron/isa/m/mach/network/PeerNode.java'))

    def test_a_composed_test_annotation_makes_a_test_class(self):  # @StatefulParametrizedTest is a @TestTemplate
        scripted = {'MachineComponentScriptTest', 'MachineIsolationScriptTest', 'InstSetIsolationScriptTest',
                    'NetworkComponentScriptTest', 'SysIsolationScriptTest', 'MemoryScriptTest', 'ProcessorScriptTest'}
        self.assertEqual(self.select('StatefulParametrizedTest') & scripted, scripted)

    def test_every_runnable_test_class_on_this_tree_is_a_test(self):
        # the classes the rule can misjudge: test-named, with no JUnit test annotation in their own code, read as the
        # rule reads it (comments out). Each one judged a test selects itself (its file names it), so a name missing
        # from the list is one the rule rejects
        sources = REPO / 'src' / 'test' / 'java'
        named = {p.stem: p for p in sources.rglob('*.java')
                 if re.fullmatch(r'Test\w*|\w*(Test|Tests|TestCase)', p.stem)}
        at_risk = {name: p for name, p in named.items() if not re.search(
            r'@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b',
            '\n'.join(line.text for line in lex(p.read_text())))}
        self.assertGreater(len(named), 300)  # the population was read, not an empty tree
        self.assertIn('MachineIsolationScriptTest', at_risk)  # a composed annotation's users are among them
        self.assertIn('tbleInstSetTest', at_risk)  # its @ParameterizedTest is commented out; its tests are inherited
        rejected = set(at_risk) - self.select(*sorted(at_risk))
        cannot_run = {name for name in rejected if re.search(
            r'\b(abstract\s+class|interface|@interface)\s+' + name + r'\b', at_risk[name].read_text())}
        # TestCategory holds @Tag names; DcmntSpaceLLMMemoryIntegrationTest's extends clause is commented out
        self.assertLessEqual(rejected - cannot_run, {'TestCategory', 'DcmntSpaceLLMMemoryIntegrationTest'})

    def test_the_bare_uri_parser_reaches_furi_test(self):
        self.assertIn('fURITest', self.select('mParser.m_furi'))

    def test_fs_space_reaches_the_classes_that_mount_it(self):
        mounts = {'llmInstSetTest', 'sysInstSetTest', 'ObjZipSerializerTest'}
        self.assertEqual(self.select('fsSpace') & mounts, mounts)
        self.assertEqual(self.select('src/main/java/studio/phaseshift/metatron/isa/m/sys/space/fsSpace.java')
                         & mounts, mounts)


if __name__ == '__main__':
    unittest.main(verbosity=2)
