#!/usr/bin/env python3
"""bin/test/hunk-revert-check.py — the test of bin/hunk-revert-check (and of bin/lib/java_lexer.py it reads with).

Builds a throwaway repository, a main checkout and one worktree on a `feature` branch, whose changes under src/main
are the hunks to check. A fake runner stands in for bin/metatron-docker: it prints surefire-shaped output whose
failures follow what the worktree's files say, so reverting a hunk is noticed exactly when a fake test reads it.
The runner also records, for every run, where HEAD was and which files differed from the branch tip. Needs git and
python3; runs no build and touches nothing outside its temporary directory.

    python3 bin/test/hunk-revert-check.py
"""
import importlib.util
import json
import os
import signal
import subprocess
import sys
import tempfile
import textwrap
import time
import unittest
from importlib.machinery import SourceFileLoader
from pathlib import Path

BIN = Path(__file__).resolve().parent.parent
SCRIPT = BIN / 'hunk-revert-check'
sys.path.insert(0, str(BIN))
from lib.java_lexer import IMPORT_LINE, lex  # noqa: E402

TESTS = '-Dtest=LighthouseTest,ReefTest,BuoyTest,CompassTest,WindlassTest,YardarmTest,YawlTest,ZephyrTest'

BASE = {
    '.gitignore': '.*\n!.gitignore\n',  # as in the repository: .docker-target/ (the check's logs) is ignored
    'src/main/java/org/harbor/Lighthouse.java': '''\
        package org.harbor;

        /**
         * A lighthouse: its beam reaches as far as the keeper trims it.
         */
        public class Lighthouse {

            public int beam() {
                return 3;
            }
        }
        ''',
    'src/main/java/org/harbor/Buoy.java': '''\
        package org.harbor;

        public class Buoy {

            public int drift() {
                return 10;
            }
        }
        ''',
    'src/main/java/org/harbor/Tide.java': '''\
        package org.harbor;

        /**
         * The tide rises twice a day.
         */
        public class Tide {

            public String chart() {
                return "http://tides.harbor.test/chart"; // the chart's address
            }
        }
        ''',
    'src/main/java/org/harbor/Chart.java': '''\
        package org.harbor;

        import java.util.List;

        public class Chart {

            public List<String> marks() {
                return List.of("reef", "shoal");
            }
        }
        ''',
    'src/main/java/org/harbor/Compass.java': '''\
        package org.harbor;

        public class Compass {

            public int north() {
                return 0;
            }

            public int south() {
                return 180;
            }
        }
        ''',
    'src/main/java/org/harbor/Signal.java': '''\
        package org.harbor;

        public class Signal {

            public static final String HORN = """
                    // beacon 1
                    """;
        }
        ''',
    'src/main/java/org/harbor/Flare.java': '''\
        package org.harbor;

        public class Flare {

            public static final String MTRON = """
                    *<harbor/beam>.plus(1)
                    """;
        }
        ''',
    'src/main/java/org/harbor/Reef.java': '''\
        package org.harbor;

        public class Reef {

            public int depth() {
                return 0;
            }
        }
        ''',
    'src/main/java/org/harbor/Rope.java': '''\
        package org.harbor;

        import java.awt.Point;
        import java.util.List;
        import java.util.function.Consumer;
        import java.util.function.Function;
        import java.util.Map;

        public class Rope {

            public int length() {
                return 30;
            }

            public Function<String, String> splice() {
                return knot -> knot;
            }

            public static void coil(final Consumer<List<Point>> sink) {
                sink.accept(List.of(new Point()));
            }
        }
        ''',
    'src/main/java/org/harbor/Windlass.java': '''\
        package org.harbor;

        import java.awt.Point;

        public class Windlass {

            public Object drum() {
                return new Point();
            }
        }
        ''',
    'src/main/java/org/harbor/Yardarm.java': '''\
        package org.harbor;

        import org.harbor.geo.Grid;
        import org.harbor.geo.Latitude;
        import org.harbor.geo.Pole;
        import org.harbor.geo.Rhumb;
        import org.harbor.geo.Sextant;
        import org.harbor.geo.Sounding;
        import org.harbor.geo.Tack;
        import org.harbor.geo.Thole;

        import java.awt.Point;

        public class Yardarm {

            public Object post() {
                return new Point();
            }
        }
        ''',
    'src/main/java/org/harbor/Yawl.java': '''\
        package org.harbor;

        import java.awt.Point;
        import java.util.List;
        import java.util.function.Consumer;

        public class Yawl {

            public Object drum() {
                return new Point();
            }

            public int length() {
                return 70;
            }

            public static void coil(final Consumer<List<String>> sink) {
                sink.accept(List.of("clove"));
            }
        }
        ''',
    'src/main/java/org/harbor/Zephyr.java': '''\
        package org.harbor;

        import java.util.List;
        import org.harbor.geo.Grid;
        import org.harbor.geo.Latitude;
        import org.harbor.geo.Pole;
        import org.harbor.geo.Rhumb;
        import org.harbor.geo.Sextant;
        import org.harbor.geo.Sounding;
        import org.harbor.geo.Tack;
        import org.harbor.geo.Thole;

        public class Zephyr {

            public List<String> masts() {
                return List.of("main", "mizzen");
            }
        }
        ''',
    'src/main/java/org/harbor/Zodiac.java': '''\
        package org.harbor;

        import java.util.List;
        import java.util.Map;

        public class Zodiac {

            public Map<String, List<String>> lines() {
                return Map.of("bow", List.of("spring"));
            }
        }
        ''',
    'src/main/resources/harbor/signals.txt': 'beam\nflare\n',
    'src/main/resources/harbor/notes.txt': 'low tide at dawn\n',
}

FEATURE = {  # path -> (old, new) on the feature branch; old None = a new file
    'src/main/java/org/harbor/Lighthouse.java': ('return 3;', 'return 7;'),
    'src/main/java/org/harbor/Buoy.java': ('return 10;', 'return 12;'),
    'src/main/java/org/harbor/Tide.java': ("twice a day.\n", "twice a lunar day.\n",
                                           "// the chart's address", '// where the chart lives'),
    'src/main/java/org/harbor/Chart.java': ('import java.util.List;\n',
                                            'import java.util.List;\nimport java.util.Map;\n'),
    'src/main/java/org/harbor/Compass.java': ('return 0;', 'return 360;', 'return 180;', 'return 181;'),
    'src/main/java/org/harbor/Signal.java': ('// beacon 1', '// beacon 2'),
    'src/main/java/org/harbor/Flare.java': ('plus(1)', 'plus(2)'),
    'src/main/java/org/harbor/Reef.java': ('return 0;', 'return Depth.of();'),
    'src/main/java/org/harbor/Rope.java': ('import java.awt.Point;', 'import org.harbor.geo.Point;',  # a swap
                                           'import java.util.function.Consumer;\n', '', 'import java.util.Map;\n', '',
                                           '\n    public static void coil(final Consumer<List<Point>> sink) {\n'
                                           '        sink.accept(List.of(new Point()));\n    }\n', ''),
    'src/main/java/org/harbor/Windlass.java': ('import java.awt.Point;',  # a swap whose name drum() still uses
                                               'import org.harbor.geo.Point;'),
    # a swap split over two hunks: the new import sorts into the org block, seven lines above the old one
    'src/main/java/org/harbor/Yardarm.java': ('import org.harbor.geo.Pole;',
                                              'import org.harbor.geo.Point;\nimport org.harbor.geo.Pole;',
                                              'import java.awt.Point;\n', ''),
    # a used name swapped in the same hunk as an import that only the deleted coil() needed
    'src/main/java/org/harbor/Yawl.java': ('import java.awt.Point;', 'import org.harbor.geo.Point;',
                                           'import java.util.function.Consumer;\n', '',
                                           '\n    public static void coil(final Consumer<List<String>> sink) {\n'
                                           '        sink.accept(List.of("clove"));\n    }\n', ''),
    # the same import moved to the end of its block, into the hunk that changes masts(): reverting that hunk takes
    # the moved line out, so the line at the old place has to come back with it
    'src/main/java/org/harbor/Zephyr.java': ('import java.util.List;\nimport org.harbor.geo.Grid;',
                                            'import org.harbor.geo.Grid;',
                                            'import org.harbor.geo.Thole;\n',
                                            'import org.harbor.geo.Thole;\nimport java.util.List;\n',
                                            '"mizzen"', '"jigger"'),
    'src/main/java/org/harbor/Zodiac.java': ('import java.util.List;\nimport java.util.Map;',  # the imports reordered
                                             'import java.util.Map;\nimport java.util.List;'),
    'src/main/resources/harbor/signals.txt': ('flare\n', 'flare\nhorn\n'),
    'src/main/resources/harbor/notes.txt': ('dawn\n', 'dawn\n\n'),
}

DEPTH = '''\
    package org.harbor;

    public final class Depth {

        public static int of() {
            return 40;
        }
    }
    '''

FAKE_RUNNER = r'''
import json, os, subprocess, sys, time
from pathlib import Path

MODE = os.environ.get('FAKE_RUNNER_MODE', '')
LOG = Path(os.environ['FAKE_RUNNER_LOG'])
args = sys.argv[1:]
record = {'args': args, 'start': time.time()}


def git(*a):
    return subprocess.run(['git', *a], capture_output=True, text=True).stdout.strip()


def src(path):
    f = Path('src/main') / path
    return f.read_text() if f.exists() else ''


record.update(head=git('rev-parse', 'HEAD'), branch=git('symbolic-ref', '-q', 'HEAD'),
              parent=git('rev-parse', 'HEAD^'), changed=git('diff', '--name-only', 'feature').split(),
              rope_imports=[line for line in src('java/org/harbor/Rope.java').splitlines()
                            if line.startswith('import')])
earlier = LOG.read_text().count('\n') if LOG.exists() else 0


def finish(code):
    record['end'] = time.time()
    with LOG.open('a') as out:
        out.write(json.dumps(record) + '\n')
    sys.exit(code)


if MODE == 'sleep' and earlier == 1:
    Path(os.environ['FAKE_RUNNER_MARK']).touch()
    time.sleep(60)
if MODE == 'edit' and 'return 10;' in src('java/org/harbor/Buoy.java'):
    with open('src/main/java/org/harbor/Buoy.java', 'a') as f:
        f.write("// the keeper's note\n")
print('[INFO] Scanning for projects...')
reverting = not record['branch']  # HEAD is detached only while a revert runs
if MODE == 'no-summary' or MODE == 'silent-revert' and reverting:
    print('[INFO] BUILD FAILURE')
    finish(1)
print('[INFO] Nothing to compile - all classes are up to date.' if MODE == 'stale' else
      '[INFO] Compiling 9 source files with javac [debug release 25] to target/classes')
rope, yardarm, yawl, zephyr = (src(f'java/org/harbor/{name}.java') for name in ('Rope', 'Yardarm', 'Yawl', 'Zephyr'))
if ('Depth.of()' in src('java/org/harbor/Reef.java') and not src('java/org/harbor/Depth.java')
        or any('Consumer<' in text and 'import java.util.function.Consumer;' not in text for text in (rope, yawl))
        or rope.count('Point;') > 1 or yardarm.count('Point;') != 1 or yawl.count('Point;') != 1
        or 'import java.util.List;' not in zephyr):
    print('[ERROR] COMPILATION ERROR : ')
    print('[ERROR] /work/src/main/java/org/harbor/Reef.java:[6,16] cannot find symbol')
    print('[INFO] BUILD FAILURE')
    finish(1)
TESTS = {
    'LighthouseTest': {'testBeam': lambda: 'return 7;' in src('java/org/harbor/Lighthouse.java'),
                       'testKeeperLog': lambda: MODE != 'baseline-red'},
    'ReefTest': {'testDepth(String)[1]': lambda: 'Depth.of()' in src('java/org/harbor/Reef.java')},
    'BuoyTest': {'testFloats': lambda: True},
    'CompassTest': {'testNorth': lambda: 'return 360;' in src('java/org/harbor/Compass.java')},
    'TideTest': {'testTurns': lambda: True},
    'WindlassTest': {'testDrum': lambda: 'import org.harbor.geo.Point;' in src('java/org/harbor/Windlass.java')},
    'YardarmTest': {'testPost': lambda: 'import org.harbor.geo.Point;' in src('java/org/harbor/Yardarm.java')},
    'YawlTest': {'testDrum': lambda: 'import org.harbor.geo.Point;' in src('java/org/harbor/Yawl.java')},
    'ZephyrTest': {'testMasts': lambda: '"jigger"' in src('java/org/harbor/Zephyr.java')},
}
ELAPSED = os.environ.get('FAKE_RUNNER_ELAPSED', '0.03')
named = [t for a in args if a.startswith('-Dtest=') for t in a[len('-Dtest='):].split(',')]
ran = [] if MODE == 'zero' else [c for c in named if c in TESTS] + (['TideTest'] if MODE == 'extra-class' else [])
if MODE == 'silent-buoy' and 'return 10;' in src('java/org/harbor/Buoy.java'):
    ran.remove('BuoyTest')
total = failures = 0
for cls in ran:
    failed = [name for name, passes in TESTS[cls].items() if not passes()]
    total += len(TESTS[cls])
    failures += len(failed)
    if failed:
        print(f'[ERROR] Tests run: {len(TESTS[cls])}, Failures: {len(failed)}, Errors: 0, Skipped: 0, '
              f'Time elapsed: {ELAPSED} s <<< FAILURE! -- in org.harbor.{cls}')
        for name in failed:
            print(f'[ERROR] org.harbor.{cls}.{name} -- Time elapsed: 0.01 s <<< FAILURE!')
            print('org.opentest4j.AssertionFailedError: expected: <true> but was: <false>')
    else:
        print(f'[INFO] Tests run: {len(TESTS[cls])}, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: {ELAPSED} s '
              f'-- in org.harbor.{cls}')
if (MODE == 'timeout-buoy' and 'return 10;' in src('java/org/harbor/Buoy.java')
        or MODE == 'baseline-fork' and not reverting):
    print('[ERROR] There was a timeout in the fork')
if MODE == 'unnamed-failure' and 'return 10;' in src('java/org/harbor/Buoy.java'):
    failures += 1  # counted in the summary, named on no line
print('[INFO] Results:')
print(f'[{"ERROR" if failures else "INFO"}] Tests run: {total}, Failures: {failures}, Errors: 0, Skipped: 0')
print('[INFO] BUILD ' + ('FAILURE' if failures else 'SUCCESS'))
finish(1 if failures else 0)
'''


class Harbor:
    """A main checkout (origin) and a worktree (wt) on `feature`, whose src/main changes are the hunks."""

    def __init__(self, tmp: Path):
        self.tmp = tmp
        self.origin = tmp / 'origin'
        self.wt = tmp / 'wt'
        self.log = tmp / 'runs.jsonl'
        self.mark = tmp / 'sleeping'
        (tmp / 'gitconfig').write_text('[user]\n\tname = Harbor Keeper\n\temail = keeper@harbor.test\n'
                                       '[init]\n\tdefaultBranch = main\n[advice]\n\tdetachedHead = false\n')
        (tmp / 'fake-runner.py').write_text(FAKE_RUNNER)
        self.env = dict(os.environ, GIT_CONFIG_GLOBAL=str(tmp / 'gitconfig'), GIT_CONFIG_NOSYSTEM='1',
                        FAKE_RUNNER_LOG=str(self.log), FAKE_RUNNER_MARK=str(self.mark))
        self.git(tmp, 'init', '-q', str(self.origin))
        for path, text in BASE.items():
            self.write(self.origin / path, textwrap.dedent(text))
        self.git(self.origin, 'add', '-A')
        self.git(self.origin, 'commit', '-qm', 'the harbor')
        self.git(self.origin, 'worktree', 'add', '-q', '-b', 'feature', str(self.wt))
        self.write(self.origin / 'src/main/java/org/harbor/Pier.java', 'package org.harbor;\n\nclass Pier {}\n')
        self.git(self.origin, 'add', '-A')
        self.git(self.origin, 'commit', '-qm', 'main moves on after the branch point')
        for path, swaps in FEATURE.items():
            text = (self.wt / path).read_text()
            for old, new in zip(swaps[::2], swaps[1::2]):
                assert text.count(old) == 1, (path, old)
                text = text.replace(old, new)
            self.write(self.wt / path, text)
        self.write(self.wt / 'src/main/java/org/harbor/Depth.java', textwrap.dedent(DEPTH))
        (self.wt / 'src/main/resources/harbor/flag.bin').write_bytes(b'\x89FLAG\x00\x01\x02')
        self.write(self.wt / 'src/test/java/org/harbor/LighthouseTest.java', 'package org.harbor;\n')
        self.write(self.wt / 'docs/harbor.md', '# the harbor\n')
        self.git(self.wt, 'add', '-A')
        self.git(self.wt, 'commit', '-qm', 'feat(harbor): the beam reaches farther')
        self.tip = self.git(self.wt, 'rev-parse', 'HEAD')

    @staticmethod
    def write(path: Path, text: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def git(self, cwd: Path, *args: str) -> str:
        return subprocess.run(['git', *args], cwd=cwd, env=self.env, check=True, capture_output=True,
                              text=True).stdout.strip()

    def check(self, *args: str, mode: str = '', cwd: Path | None = None) -> subprocess.CompletedProcess:
        env = dict(self.env, FAKE_RUNNER_MODE=mode)
        return subprocess.run([sys.executable, str(SCRIPT), '--runner',
                               f'{sys.executable} {self.tmp / "fake-runner.py"}', *args],
                              cwd=cwd or self.wt, env=env, capture_output=True, text=True, timeout=120)

    def runs(self) -> list[dict]:
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def lock(self) -> Path:
        return Path(self.git(self.wt, 'rev-parse', '--absolute-git-dir')) / 'hunk-revert-check.lock'

    def assert_restored(self, case: unittest.TestCase) -> None:
        case.assertEqual(self.git(self.wt, 'symbolic-ref', 'HEAD'), 'refs/heads/feature')
        case.assertEqual(self.git(self.wt, 'rev-parse', 'HEAD'), self.tip)
        case.assertEqual(self.git(self.wt, 'rev-parse', 'feature'), self.tip)
        case.assertEqual(self.git(self.wt, 'status', '--porcelain'), '')
        case.assertFalse(self.lock().exists())


def lines_with(output: str, path_part: str) -> list[str]:
    return [line for line in output.splitlines() if path_part in line]


class HunkRevertCheckTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='hunk-revert-check-test-')
        self.harbor = Harbor(Path(self.tmp.name))

    def tearDown(self):
        self.tmp.cleanup()

    def test_list_classifies_every_hunk_and_runs_nothing(self):
        r = self.harbor.check('--list')
        self.assertEqual(r.returncode, 0, r.stderr)
        expected = {
            'Buoy.java': 'behavior', 'Chart.java': 'skip (imports only)', 'Compass.java': 'behavior',
            'Depth.java': 'behavior', 'Flare.java': 'behavior', 'Lighthouse.java': 'behavior',
            'Reef.java': 'behavior', 'Signal.java': 'behavior', 'Rope.java': 'skip (imports only)',
            'Windlass.java': 'behavior',  # an import swap whose name drum() still uses
            'Zodiac.java': 'skip (imports only)',  # the same imports reordered: no name points elsewhere
            'Zephyr.java': 'skip (imports only)',  # the moved import's old place; its new place rides with masts()
            'Tide.java': 'skip (comments or whitespace only)',
            'notes.txt': 'skip (blank lines only)', 'signals.txt': 'behavior',
        }
        for name, status in expected.items():
            found = lines_with(r.stdout, '/' + name + ':')
            two = name in ('Rope.java', 'Yardarm.java', 'Yawl.java', 'Zephyr.java')
            self.assertEqual(len(found), 2 if two else 1, (name, r.stdout))
            self.assertIn(status, found[0])
        self.assertIn('behavior', lines_with(r.stdout, '/Rope.java:')[1])  # the deleted coil()
        for name in ('Yardarm.java', 'Yawl.java'):  # each side of the split swap; the swap hunk and the deleted coil()
            self.assertEqual([line.split()[1] for line in lines_with(r.stdout, name)], ['behavior', 'behavior'])
        self.assertEqual([line.split()[1] for line in lines_with(r.stdout, 'Zephyr.java')], ['skip', 'behavior'])
        self.assertIn('15 behavior hunks of main...feature under src/main (6 skipped, 1 files without hunks)', r.stdout)
        # the changed line, past the hunk's leading context
        self.assertIn('Lighthouse.java:9 ', lines_with(r.stdout, 'Lighthouse.java')[0])
        self.assertIn('no hunks (binary or mode only)', lines_with(r.stdout, 'flag.bin')[0])
        for absent in ('Pier.java', 'LighthouseTest.java', 'harbor.md'):  # main's own change; outside src/main
            self.assertEqual(lines_with(r.stdout, absent), [])
        self.assertEqual(self.harbor.runs(), [])

    def test_reports_each_hunk_no_test_turned_red(self):
        r = self.harbor.check(TESTS)
        self.assertEqual(r.returncode, 1, r.stdout + r.stderr)
        status = {name: lines_with(r.stdout, '/' + name + ':')[0].split()[0]
                  for name in ('Buoy.java', 'Compass.java', 'Depth.java', 'Flare.java', 'Lighthouse.java',
                               'Reef.java', 'Rope.java', 'Signal.java', 'Windlass.java', 'signals.txt')}
        self.assertEqual(status, {'Buoy.java': 'green', 'Compass.java': 'red', 'Depth.java': 'no',
                                  'Flare.java': 'green', 'Lighthouse.java': 'red', 'Reef.java': 'red',
                                  'Rope.java': 'green', 'Signal.java': 'green', 'Windlass.java': 'red',
                                  'signals.txt': 'green'})
        self.assertIn('LighthouseTest.testBeam', lines_with(r.stdout, 'Lighthouse.java:')[0])
        self.assertIn('ReefTest.testDepth(String)[1]', lines_with(r.stdout, 'Reef.java:')[0])
        self.assertIn('does not compile', lines_with(r.stdout, 'Depth.java:')[0])
        self.assertIn('WindlassTest.testDrum', lines_with(r.stdout, 'Windlass.java:')[0])
        yardarm, yawl = lines_with(r.stdout, 'Yardarm.java:')[:2], lines_with(r.stdout, 'Yawl.java:')[:2]
        self.assertEqual([line.split()[0] for line in yardarm], ['red', 'red'])  # each half reverts with its partner
        self.assertEqual([line.split()[0] for line in yawl], ['red', 'green'])  # coil() takes back its Consumer import
        self.assertEqual(lines_with(r.stdout, 'Zephyr.java:')[0].split()[0], 'red')  # List comes back to its old place
        self.assertIn('15 hunks checked: 8 red, 6 green, 1 no run', r.stdout)
        report = r.stdout.split('no test went red for:')[1]
        for name in ('Buoy.java', 'Depth.java', 'Flare.java', 'Rope.java', 'Signal.java', 'signals.txt'):
            self.assertIn(name, report)
        for name in ('Lighthouse.java', 'Reef.java', 'Compass.java', 'Windlass.java'):
            self.assertNotIn(name, report)
        self.harbor.assert_restored(self)
        self.assertEqual(self.harbor.git(self.harbor.wt, 'rev-list', '--count', 'main..feature'), '1')

    def test_each_run_sees_the_tip_with_one_hunk_reverted_on_a_scratch_commit(self):
        self.harbor.check(TESTS)
        runs = self.harbor.runs()
        self.assertEqual(len(runs), 16)  # the baseline and 15 behavior hunks
        baseline, hunks = runs[0], runs[1:]
        self.assertEqual((baseline['branch'], baseline['head'], baseline['changed']),
                         ('refs/heads/feature', self.harbor.tip, []))
        self.assertFalse(any(a.startswith('-Dsurefire.timeout') for a in baseline['args']))
        for run in hunks:
            self.assertEqual(run['branch'], '')  # detached
            self.assertNotEqual(run['head'], self.harbor.tip)
            self.assertEqual(run['parent'], self.harbor.tip)
            self.assertEqual(len(run['changed']), 1, run)
            self.assertIn('-Dsurefire.timeout=300', run['args'])
        for run in runs:
            self.assertEqual(run['args'][:3], ['build', 'test', TESTS])
        for earlier, later in zip(runs, runs[1:]):  # one run at a time
            self.assertLessEqual(earlier['end'], later['start'])
        self.assertEqual([run['changed'][0].rsplit('/', 1)[1] for run in hunks],
                         ['Buoy.java', 'Compass.java', 'Depth.java', 'Flare.java', 'Lighthouse.java', 'Reef.java',
                          'Rope.java', 'Signal.java', 'Windlass.java', 'Yardarm.java', 'Yardarm.java', 'Yawl.java',
                          'Yawl.java', 'Zephyr.java', 'signals.txt'])
        rope = next(run for run in hunks if run['changed'][0].endswith('Rope.java'))['rope_imports']
        self.assertIn('import java.util.function.Consumer;', rope)  # coil() compiles back
        self.assertNotIn('import java.util.Map;', rope)  # removed in the same hunk, but coil() never names it
        self.assertIn('import org.harbor.geo.Point;', rope)  # the swap stays: java.awt.Point would clash
        self.assertNotIn('import java.awt.Point;', rope)

    def test_zero_context_splits_a_hunk_and_finds_its_untested_half(self):
        compass = lines_with(self.harbor.check('--list', '-U0').stdout, 'Compass.java')
        self.assertEqual(len(compass), 2)
        numbers = ','.join(line.split()[0] for line in compass)
        r = self.harbor.check('-U0', '--hunk', numbers, '-Dtest=CompassTest')
        self.assertEqual(r.returncode, 1, r.stdout + r.stderr)
        self.assertEqual([line.split()[0] for line in lines_with(r.stdout, 'Compass.java:')[:2]], ['red', 'green'])
        self.assertIn('CompassTest.testNorth', lines_with(r.stdout, 'Compass.java:6')[0])
        self.harbor.assert_restored(self)

    def test_a_deleted_method_takes_back_only_the_imports_it_uses(self):
        rope = lines_with(self.harbor.check('--list', '-U0').stdout, 'Rope.java')
        self.assertEqual([line.split()[1] for line in rope], ['skip', 'skip', 'skip', 'behavior'])
        r = self.harbor.check('-U0', '--hunk', rope[3].split()[0], '-Dtest=BuoyTest')
        self.assertEqual(lines_with(r.stdout, 'Rope.java:')[0].split()[0], 'green', r.stdout + r.stderr)
        imports = self.harbor.runs()[1]['rope_imports']
        self.assertIn('import java.util.function.Consumer;', imports)
        self.assertNotIn('import java.util.Map;', imports)  # removed too, but coil() never names it
        self.assertEqual([i for i in imports if i.endswith('Point;')], ['import org.harbor.geo.Point;'])
        self.harbor.assert_restored(self)

    def test_a_tested_hunk_alone_exits_zero(self):
        number = lines_with(self.harbor.check('--list').stdout, 'Lighthouse.java')[0].split()[0]
        r = self.harbor.check('--hunk', number, 'LighthouseTest')
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn('1 hunks checked: 1 red, 0 green, 0 no run', r.stdout)
        self.harbor.assert_restored(self)

    def test_refuses_test_lists_that_run_more_than_was_named(self):
        for entry in ('-Dtest=', '-Dtest=*Test', '-Dtest=org/harbor/LighthouseTest', '-Dtest=LighthouseTest#',
                      '-Dtest=LighthouseTest,,ReefTest', '-Dtest=**'):
            r = self.harbor.check(entry)
            self.assertEqual(r.returncode, 2, (entry, r.stdout, r.stderr))
            self.assertIn('refused test entry', r.stderr)
        r = self.harbor.check()
        self.assertEqual(r.returncode, 2)
        self.assertIn('name the classes to run', r.stderr)
        forty = ','.join(f'Gull{k:02d}Test' for k in range(40))
        r = self.harbor.check(f'-Dtest={forty},Gull40Test')
        self.assertEqual(r.returncode, 2)
        self.assertIn('41 classes: a revert check runs them once per hunk, so it takes 40 at most', r.stderr)
        self.assertEqual(self.harbor.check('--list', f'-Dtest={forty}').returncode, 0)  # 40 is allowed
        self.assertEqual(self.harbor.runs(), [])

    def test_refuses_the_main_checkout_the_base_branch_a_dirty_tree_and_unknown_hunks(self):
        r = self.harbor.check(TESTS, cwd=self.harbor.origin)
        self.assertEqual(r.returncode, 2)
        self.assertIn('main checkout', r.stderr)
        r = self.harbor.check(TESTS, '--base', 'feature')
        self.assertEqual(r.returncode, 2)
        self.assertIn('base branch', r.stderr)
        r = self.harbor.check(TESTS, '--hunk', '2')  # hunk 2 is the import-only skip
        self.assertEqual(r.returncode, 2)
        self.assertIn('no behavior hunk numbered 2', r.stderr)
        buoy = self.harbor.wt / 'src/main/java/org/harbor/Buoy.java'
        buoy.write_text(buoy.read_text() + '// unsaved\n')
        r = self.harbor.check(TESTS)
        self.assertEqual(r.returncode, 2)
        self.assertIn('uncommitted changes', r.stderr)
        self.assertEqual(self.harbor.runs(), [])

    def test_a_baseline_whose_filter_did_not_hold_stops_before_any_revert(self):
        for mode, args, code, message in (
                ('extra-class', [TESTS], 2, 'classes ran that were not named (TideTest)'),
                ('', ['-Dtest=LighthouseTest,GhostTest'], 2, 'named classes did not report (GhostTest)'),
                ('zero', [TESTS], 3, 'the baseline ran 0 tests'),
                ('no-summary', [TESTS], 3, 'no `Tests run:` summary')):
            r = self.harbor.check(*args, mode=mode)
            self.assertEqual(r.returncode, code, (mode, r.stdout, r.stderr))
            self.assertIn(message, r.stderr)
            self.harbor.assert_restored(self)
        self.assertEqual(len(self.harbor.runs()), 4)  # four baselines, no revert run

    def test_a_test_red_on_the_baseline_does_not_make_a_hunk_red(self):
        r = self.harbor.check(TESTS, mode='baseline-red')
        self.assertEqual(r.returncode, 1)
        self.assertIn('1 tests already fail', r.stdout)
        self.assertEqual(lines_with(r.stdout, 'Buoy.java:')[0].split()[0], 'green')
        self.assertEqual(lines_with(r.stdout, 'Lighthouse.java:')[0].split()[0], 'red')

    def test_a_run_that_did_not_recompile_or_whose_fork_died_is_no_run(self):
        r = self.harbor.check(TESTS, mode='stale')
        self.assertIn('did not recompile', lines_with(r.stdout, 'Lighthouse.java:')[0])
        self.assertEqual(lines_with(r.stdout, 'signals.txt:')[0].split()[0], 'green')  # a resource needs no javac
        r = self.harbor.check(TESTS, mode='timeout-buoy')
        self.assertIn('timed out or died', lines_with(r.stdout, 'Buoy.java:')[0])
        r = self.harbor.check(TESTS, mode='silent-buoy')
        self.assertIn('no run  hunk   1', r.stdout)
        self.assertIn('did not report: BuoyTest', lines_with(r.stdout, 'Buoy.java:')[0])
        self.harbor.assert_restored(self)

    def test_help_prints_the_usage(self):
        r = subprocess.run([sys.executable, str(SCRIPT), '--help'], capture_output=True, text=True)
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertTrue(r.stdout.startswith('bin/hunk-revert-check — revert each behavior hunk'), r.stdout[:80])
        self.assertIn('Exit: 0 when every behavior hunk is red', r.stdout)
        self.assertIn('40 at most', r.stdout)  # MOST_CLASSES's value, from bin/lib/test_runs.py

    def test_failures_no_line_names_still_make_a_hunk_red(self):
        r = self.harbor.check('--hunk', '1', TESTS, mode='unnamed-failure')
        self.assertEqual(lines_with(r.stdout, 'Buoy.java:')[0].split()[0], 'red', r.stdout + r.stderr)
        self.assertIn('more failures than the baseline, not attributed to a test', r.stdout)

    def test_a_baseline_fork_death_stops_and_a_revert_run_without_a_summary_is_no_run(self):
        r = self.harbor.check(TESTS, mode='baseline-fork')
        self.assertEqual(r.returncode, 3, r.stdout + r.stderr)
        self.assertIn('a test fork timed out or died in the baseline', r.stderr)
        r = self.harbor.check('--hunk', '1', TESTS, mode='silent-revert')
        self.assertEqual(lines_with(r.stdout, 'Buoy.java:')[0].split()[0], 'no', r.stdout + r.stderr)
        self.assertIn('no `Tests run:` summary', lines_with(r.stdout, 'Buoy.java:')[0])
        self.harbor.assert_restored(self)

    def test_the_fork_timeout_follows_the_time_the_baseline_classes_took(self):
        for elapsed, timeout in (('50', '2000'), ('100', '2700')):  # 8 classes: 5x400 s; 5x800 s is past the ceiling
            self.harbor.env['FAKE_RUNNER_ELAPSED'] = elapsed
            self.harbor.check('--hunk', '1', TESTS)
            self.assertIn(f'-Dsurefire.timeout={timeout}', self.harbor.runs()[-1]['args'])
        self.harbor.check('--hunk', '1', '--timeout', '900', TESTS)
        baseline, revert = self.harbor.runs()[-2:]
        self.assertIn('-Dsurefire.timeout=900', baseline['args'])
        self.assertIn('-Dsurefire.timeout=900', revert['args'])

    def test_a_lock_from_a_finished_check_is_cleared_and_a_live_one_refuses(self):
        finished = subprocess.Popen(['true'])
        finished.wait()
        self.harbor.lock().write_text(f'{finished.pid} feature {self.harbor.tip}\n')
        r = self.harbor.check(TESTS)
        self.assertEqual(r.returncode, 1, r.stdout + r.stderr)
        self.harbor.assert_restored(self)
        self.harbor.lock().write_text(f'{os.getpid()} feature {self.harbor.tip}\n')
        r = self.harbor.check(TESTS)
        self.assertEqual(r.returncode, 2)
        self.assertIn(f'another check (pid {os.getpid()}) is running', r.stderr)
        self.assertEqual(len(self.harbor.runs()), 16)  # the refused check ran nothing

    def test_an_edit_during_a_run_stops_the_check_and_keeps_the_edit(self):
        r = self.harbor.check(TESTS, mode='edit')
        self.assertEqual(r.returncode, 2, r.stdout + r.stderr)
        self.assertIn('Buoy.java was edited during the run', r.stderr)
        wt = self.harbor.wt
        self.assertEqual(subprocess.run(['git', 'symbolic-ref', '-q', 'HEAD'], cwd=wt, env=self.harbor.env)
                         .returncode, 1)  # still detached, at the scratch commit
        self.assertEqual(self.harbor.git(wt, 'rev-parse', 'HEAD^'), self.harbor.tip)
        self.assertEqual(self.harbor.git(wt, 'rev-parse', 'feature'), self.harbor.tip)
        self.assertIn("the keeper's note", (wt / 'src/main/java/org/harbor/Buoy.java').read_text())
        again = self.harbor.check(TESTS)
        self.assertEqual(again.returncode, 2)
        self.assertIn('save any edit in the tree', again.stderr)  # before the checkout that would discard it
        self.assertIn('git checkout -f feature', again.stderr)

    def test_an_interrupt_restores_the_branch(self):
        env = dict(self.harbor.env, FAKE_RUNNER_MODE='sleep')
        check = subprocess.Popen([sys.executable, str(SCRIPT), '--runner',
                                  f'{sys.executable} {self.harbor.tmp / "fake-runner.py"}', TESTS],
                                 cwd=self.harbor.wt, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                 text=True)
        deadline = time.monotonic() + 30
        while not self.harbor.mark.exists() and time.monotonic() < deadline:
            time.sleep(0.1)
        self.assertTrue(self.harbor.mark.exists(), 'the first revert run never started')
        self.assertEqual(self.harbor.git(self.harbor.wt, 'diff', '--name-only', 'feature'),
                         'src/main/java/org/harbor/Buoy.java')  # mid-run, a hunk is reverted
        check.send_signal(signal.SIGTERM)
        out, err = check.communicate(timeout=30)
        self.assertEqual(check.returncode, 130, out + err)
        self.assertIn('feature is restored', err)
        self.assertIn('bin/metatron-remote status', err)
        self.harbor.assert_restored(self)


class ParseRunTest(unittest.TestCase):
    """The surefire lines are copied from a real bin/metatron-docker run of this repository."""

    def setUp(self):
        spec = importlib.util.spec_from_loader('hunk_revert_check', SourceFileLoader('hunk_revert_check', str(SCRIPT)))
        self.module = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = self.module
        spec.loader.exec_module(self.module)

    def test_reads_the_summary_the_classes_and_each_failing_test(self):
        text = '\n'.join([
            '[INFO] Compiling 400 source files with javac [debug release 25] to target/test-classes',
            '[ERROR] Tests run: 14, Failures: 6, Errors: 0, Skipped: 0, Time elapsed: 7.158 s <<< FAILURE! -- in '
            'studio.phaseshift.metatron.isa.m.mach.network.BranchLaneTest',
            '[ERROR] studio.phaseshift.metatron.isa.m.mach.network.BranchLaneTest.testEveryLaneIsAVirtualThread -- '
            'Time elapsed: 0.649 s <<< FAILURE!',
            '[ERROR] studio.phaseshift.metatron.isa.m.mach.network.ParallelFanOutTest.testEachPeerReceivesItsOwnPart'
            '(String, String, int)[1] -- Time elapsed: 0.586 s <<< FAILURE!',
            '[WARNING] Tests run: 5, Failures: 0, Errors: 0, Skipped: 1, Time elapsed: 4.327 s -- in '
            'studio.phaseshift.metatron.isa.m.mach.machInstSetTest',
            '[INFO] Results:',
            '[ERROR] Tests run: 113, Failures: 41, Errors: 0, Skipped: 1',
            '[INFO] BUILD FAILURE',
        ])
        run = self.module.parse_run(text, Path('baseline.log'), 1.0)
        self.assertEqual(run.total, (113, 41, 0, 1))
        self.assertEqual(run.classes, {'BranchLaneTest', 'machInstSetTest'})
        self.assertEqual(run.failed, {
            'studio.phaseshift.metatron.isa.m.mach.network.BranchLaneTest.testEveryLaneIsAVirtualThread',
            'studio.phaseshift.metatron.isa.m.mach.network.ParallelFanOutTest.testEachPeerReceivesItsOwnPart'
            '(String, String, int)[1]'})
        self.assertFalse(run.main_compiled)  # test-classes is not the main tree
        self.assertFalse(run.compile_error or run.fork_died)
        self.assertEqual(self.module.short(sorted(run.failed)[1]),
                         'ParallelFanOutTest.testEachPeerReceivesItsOwnPart(String, String, int)[1]')

    def test_no_summary_is_none_not_zero(self):
        run = self.module.parse_run('[ERROR] COMPILATION ERROR : \n[INFO] BUILD FAILURE\n', Path('x.log'), 1.0)
        self.assertIsNone(run.total)
        self.assertTrue(run.compile_error)


class JavaLexerTest(unittest.TestCase):

    def code(self, source: str) -> list[str]:
        return [line.code for line in lex(textwrap.dedent(source))]

    def test_comment_markers_inside_literals_are_code(self):
        self.assertEqual(self.code('''\
            String chart = "http://tides.harbor.test"; // the address
            char quote = '"'; /* a quote */ int knots = 4;
            String slash = "a\\"//b"; // escaped quote, then a comment
            '''), ['Stringchart="http://tides.harbor.test";', 'charquote=\'"\';intknots=4;',
                   'Stringslash="a\\"//b";', ''])

    def test_a_block_comment_spans_lines_and_a_text_block_keeps_its_lines(self):
        self.assertEqual(self.code('''\
            int tide = 1; /* rises
               * twice */ int ebb = 2;
            String horn = """
                // beacon
                *<harbor/beam>
                \\""" not the end
                """;
            '''), ['inttide=1;', 'intebb=2;', 'Stringhorn="""', '    // beacon', '    *<harbor/beam>',
                   '    \\""" not the end', '    """;', ''])

    def test_import_lines(self):
        for line in ('import java.util.Map;', 'import static org.harbor.Depth.of;', 'import org.harbor.*;',
                     '  import  java . util . List ;'):
            self.assertTrue(IMPORT_LINE.match(line), line)
        for line in ('int important;', 'importance = 3;', 'import java.util.Map; int x;'):
            self.assertFalse(IMPORT_LINE.match(line), line)


if __name__ == '__main__':
    unittest.main(verbosity=2)
