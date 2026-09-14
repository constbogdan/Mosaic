"""Offline I05 presentation and preserved delivery-contract checks."""
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
import unittest
from unittest.mock import Mock, patch

import hosted_upstream as upstream
import mosaic_delivery_output as output
import mosaic_development_release as development
import mosaic_signing_exercise as transport
import mosaic_stable as stable
import test_mosaic_development_release as fixtures


ROOT = Path(__file__).resolve().parent.parent


class DeliveryOutputTests(unittest.TestCase):
    def setUp(self):
        fixture = fixtures.PublisherTests()
        fixture.setUp()
        self.m, self.apk = fixture.m, fixture.apk

    def test_release_titles_still_match_installed_updater_and_asset_contract(self):
        checker = (ROOT / 'app/src/main/java/com/github/damontecres/wholphin/services/UpdateChecker.kt').read_text(encoding='utf-8')
        version = (ROOT / 'app/src/main/java/com/github/damontecres/wholphin/util/Version.kt').read_text(encoding='utf-8')
        self.assertIn('result.jsonObject["name"]', checker)
        self.assertIn('Version.tryFromString(name)', checker)
        self.assertIn('const val ASSET_NAME = "Wholphin"', checker)
        self.assertIn('add("$ASSET_NAME$releaseSuffix.apk")', checker)
        pattern = re.search(r'VERSION_REGEX = Regex\("(.+)"\)', version)[1].replace('\\\\', '\\')
        for fields in (development.release_fields(self.m, False),
                       development.release_fields(self.m, False, archive=True), stable.fields(self.m, False)):
            self.assertEqual('v1.0.5', fields['name'])
            self.assertIsNotNone(re.fullmatch(pattern, fields['name']))
        self.assertIsNone(re.fullmatch(pattern, 'Mosaic v1.0.5 — Development'))
        self.assertEqual('Wholphin-release.apk', development.APK_NAME)
        self.assertEqual('Wholphin-release.apk', self.m['assetName'])

    def test_release_bodies_distinguish_channels_without_changing_identity(self):
        before = copy.deepcopy(self.m)
        baseline = 'b' * 40
        bodies = [development.release_fields(self.m, False, compare_from=baseline)['body'],
                  development.release_fields(self.m, False, archive=True, compare_from=baseline)['body'],
                  stable.fields(self.m, False, 'mosaic-v1.0.4')['body']]
        for body in bodies:
            for value in (self.m['versionName'], self.m['immutableIdentity'], self.m['sourceSha'],
                          self.m['signedApkSha256'], 'mosaic-release.json', 'Wholphin-release.apk'):
                self.assertIn(value, body)
            self.assertIn('/commit/' + self.m['sourceSha'], body)
            self.assertIn('<summary>Technical provenance</summary>', body)
            self.assertIn('Compare changes', body)
        self.assertIn('# Development v1.0.5', bodies[0])
        self.assertIn('Latest automatically published validated build.', bodies[0])
        self.assertIn('# Immutable Development build v1.0.5', bodies[1])
        self.assertIn('not a separate update channel', bodies[1])
        self.assertIn('# Stable v1.0.5', bodies[2])
        self.assertIn('Explicitly promoted trusted build for normal consumption.', bodies[2])
        self.assertIn('/compare/' + baseline + '...' + self.m['sourceSha'], bodies[0])
        self.assertIn('/compare/mosaic-v1.0.4...mosaic-v1.0.5', bodies[2])
        self.assertEqual(before, self.m)

    def test_compare_links_accept_only_immutable_authenticated_identities(self):
        self.assertEqual(
            output.REPOSITORY_URL + '/compare/' + 'a' * 40 + '...' + 'b' * 40,
            output.compare_link('a' * 40, 'b' * 40),
        )
        self.assertEqual(
            output.REPOSITORY_URL + '/compare/mosaic-v1.0.4...mosaic-v1.0.5',
            output.compare_link('mosaic-v1.0.4', 'mosaic-v1.0.5'),
        )
        for hostile in ('develop', 'main', 'mosaic-v1.0.0', 'x](https://example.invalid)', 'a' * 39):
            with self.subTest(hostile=hostile):
                self.assertIsNone(output.compare_link(hostile, self.m['sourceSha']))
                body = development.release_fields(self.m, False, compare_from=hostile)['body']
                self.assertNotIn('Compare changes', body)
                self.assertNotIn('/compare/', body)

    def test_existing_published_bodies_are_not_backfilled_on_retry(self):
        api = fixtures.FakeGitHub()
        development.publish(api, self.m, self.apk)
        for release in api.releases.values():
            release['body'] = 'Historical body retained'
        before = copy.deepcopy((api.refs, api.tags, api.releases, api.uploads))
        api.calls.clear()
        development.publish(api, self.m, self.apk, compare_from='b' * 40)
        self.assertEqual(before, (api.refs, api.tags, api.releases, api.uploads))
        self.assertFalse(any(method in ('POST', 'PATCH', 'DELETE') for method, _, _ in api.calls))

    def test_development_publication_uses_authenticated_baseline_only_for_navigation(self):
        api = fixtures.FakeGitHub()
        previous = dict(self.m, versionCode=4, versionName='1.0.4', immutableIdentity='downstream-build-4',
                        sourceSha='4' * 40)
        annotation = api.call('POST', 'git/tags', dict(tag='mosaic-v1.0.4', message=development.canonical(previous).decode(),
                                                      object=previous['sourceSha'], type='commit'))
        api.call('POST', 'git/refs', dict(ref='refs/tags/mosaic-v1.0.4', sha=annotation['sha']))
        api.call('POST', 'releases', dict(tag_name='mosaic-v1.0.4', name='v1.0.4', draft=False,
                                         prerelease=False, make_latest='true', body='fixture'))
        self.assertEqual('mosaic-v1.0.4', development.publish(api, self.m, self.apk))
        self.assertEqual({'mosaic-v1.0.4', 'downstream-build-5', 'develop'},
                         {item['tag_name'] for item in api.releases.values()})
        for item in (release for release in api.releases.values() if release['prerelease']):
            self.assertEqual('v1.0.5', item['name'])
            self.assertIn('/compare/mosaic-v1.0.4...' + self.m['sourceSha'], item['body'])

    def test_development_omits_untrusted_stable_comparison_without_blocking_publication(self):
        api = fixtures.FakeGitHub()
        api.call('POST', 'releases', dict(tag_name='mosaic-v1.0.4', name='v1.0.4', draft=False,
                                         prerelease=False, make_latest='true', body='fixture'))
        self.assertIsNone(development.publish(api, self.m, self.apk))
        rolling = next(item for item in api.releases.values() if item['tag_name'] == 'develop')
        self.assertNotIn('Compare changes', rolling['body'])

    def test_publication_summaries_preserve_original_identity(self):
        env = {}
        for operation, heading, baseline in [('publish', 'Development v1.0.5 published', 'b' * 40),
                                             ('promote', 'Stable v1.0.5 released', 'mosaic-v1.0.4')]:
            text = output.publication_summary(self.m, operation, env, compare_from=baseline)
            self.assertTrue(text.startswith('## ' + heading))
            for value in (self.m['sourceSha'], self.m['signedApkSha256'], self.m['immutableIdentity']):
                self.assertIn(value, text)
            self.assertIn('Original build run: ' + self.m['buildRunId'], text)
            self.assertIn('<summary>Technical details</summary>', text)
            self.assertIn('Compare changes', text)
            self.assertLess(text.index('Compare changes'), text.index('<details>'))
        published = output.publication_summary(self.m, 'publish', env, compare_from='b' * 40)
        self.assertIn('Latest automatically published validated build.', published)
        promoted = output.publication_summary(self.m, 'promote', env, compare_from='mosaic-v1.0.4')
        self.assertIn('Explicitly promoted trusted build for normal consumption.', promoted)
        self.assertIn('Exact Development bytes reused', promoted)
        self.assertIn('/releases/tag/mosaic-v1.0.5', promoted)

    def test_non_apk_summary_preserves_machine_outputs_and_immutable_compare(self):
        result = dict(releaseRequired=False, outcome='skipped_non_apk', releaseRelevance='tooling-only',
                      validationRisk='high', baselineSha='a' * 40, currentSha='b' * 40,
                      paths=[dict(path=f'scripts/fixture{i}.py') for i in range(24)], reason='fixture')
        with tempfile.TemporaryDirectory() as temp:
            env = dict(GITHUB_OUTPUT=str(Path(temp) / 'outputs'), GITHUB_STEP_SUMMARY=str(Path(temp) / 'summary'))
            development.record_eligibility(result, None, env)
            values = dict(line.split('=', 1) for line in Path(env['GITHUB_OUTPUT']).read_text().splitlines())
            summary = Path(env['GITHUB_STEP_SUMMARY']).read_text(encoding='utf-8')
        self.assertEqual('skipped_non_apk', values['outcome'])
        self.assertEqual('false', values['release_required'])
        self.assertTrue(summary.startswith('## No build required'))
        self.assertIn('This change does not affect the application.', summary)
        self.assertIn('tooling-only', summary)
        self.assertIn('high', summary)
        self.assertIn('Changed paths: 24', summary)
        self.assertIn('Development build, signing, and publication are skipped', summary)
        self.assertIn('/compare/' + 'a' * 40 + '...' + 'b' * 40, summary)
        self.assertIn('<summary>Technical details</summary>', summary)
        self.assertLess(summary.index('This change does not affect the application.'),
                        summary.index('Release relevance:'))

    def test_sync_heading_distinguishes_conflict_and_operational_failure_without_changing_json(self):
        for outcome, conflict, error, label in [('no_delta', False, False, 'No upstream changes'),
                                               ('ready', False, False, '0 upstream changes · none require attention'),
                                               ('blocked', True, False, '0 upstream changes · review required'),
                                               ('blocked', False, True, 'Upstream publication failed')]:
            record = dict(outcome=outcome, textual_conflicts=conflict, reason='<untrusted>')
            before = copy.deepcopy(record)
            summary = upstream.upstream_summary(record, publication=True, operation_error=error)
            self.assertTrue(summary.startswith('## ' + label))
            if outcome == 'blocked' or error:
                self.assertIn('&lt;untrusted&gt;', summary)
            else:
                operator_text = summary.split('<summary>Technical details</summary>', maxsplit=1)[0]
                self.assertNotIn('&lt;untrusted&gt;', operator_text)
            self.assertNotIn('<untrusted>', summary)
            self.assertIn('<summary>Operator navigation</summary>', summary)
            self.assertIn('<summary>Technical details</summary>', summary)
            self.assertEqual(before, record)
        with patch.object(upstream.subprocess, 'run', return_value=Mock(returncode=1)):
            with self.assertRaises(upstream.OperationError) as error:
                upstream.command(['gh', 'api'])
        self.assertIsInstance(error.exception, upstream.Blocked)

    def test_mapping_is_exact_existing_output_with_source_and_digest(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            mapping = root / 'app/build/outputs/mapping/defaultRelease/mapping.txt'
            mapping.parent.mkdir(parents=True)
            mapping.write_bytes(b'original.Class -> a:\n')
            unsigned = root / 'unsigned'
            unsigned.mkdir()
            (unsigned / 'provenance.json').write_text(json.dumps(self.m['source']))
            destination = root / 'diagnostic'
            transport.prepare_mapping(root, unsigned, destination)
            record = json.loads((destination / 'mapping.json').read_text())
            self.assertEqual(mapping.read_bytes(), (destination / 'mapping.txt').read_bytes())
            self.assertEqual(self.m['source'], record['source'])
            self.assertEqual(self.m['immutableIdentity'], record['immutableIdentity'])
            self.assertEqual(hashlib.sha256(mapping.read_bytes()).hexdigest(), record['mappingSha256'])
            self.assertEqual('.github/workflows/ci.yml', record['buildWorkflow'])
            self.assertEqual({'mapping.txt', 'mapping.json'}, {p.name for p in destination.iterdir()})
            mapping.write_bytes(b'')
            with self.assertRaises(ValueError):
                transport.prepare_mapping(root, unsigned, root / 'empty')
            mapping.unlink()
            with self.assertRaises(FileNotFoundError):
                transport.prepare_mapping(root, unsigned, root / 'missing')

    def test_workflow_display_changes_preserve_authenticated_jobs_and_names(self):
        workflows = ROOT / '.github/workflows'
        self.assertEqual(
            {'ci.yml', 'hold-release.yml', 'mosaic-signing-exercise.yml',
             'mosaic-stable-promotion.yml', 'upstream-sync.yml'},
            {path.name for path in workflows.glob('*.yml')},
        )
        expected = {'mosaic-stable-promotion.yml': 'Stable Promotion',
                    'mosaic-signing-exercise.yml': 'Signing Diagnostic',
                    'upstream-sync.yml': 'Upstream Synchronization'}
        for file, name in expected.items():
            source = (ROOT / '.github/workflows' / file).read_text(encoding='utf-8')
            self.assertEqual('name: ' + name, source.splitlines()[0])
            run_name = source.splitlines()[1]
            self.assertTrue(run_name.startswith('run-name:'))
            run_name_source = source.split('\non:\n', 1)[0]
            for forbidden in ('needs.', 'steps.', 'github.run_number'):
                self.assertNotIn(forbidden, run_name_source)
        ci = (ROOT / development.CI_WORKFLOW).read_text(encoding='utf-8')
        self.assertEqual('name: CI', ci.splitlines()[0])
        hold = (workflows / 'hold-release.yml').read_text(encoding='utf-8')
        self.assertEqual('name: Hold Release', hold.splitlines()[0])
        self.assertIn('    name: Full validation', ci)
        self.assertIn('    name: Build Development Release', ci)
        self.assertIn('    name: Sign Development', ci)
        self.assertIn('    name: Publish Development', ci)
        self.assertEqual('Full validation', development.CI_JOB)
        diagnostic = (ROOT / '.github/workflows/mosaic-signing-exercise.yml').read_text(encoding='utf-8')
        self.assertIn('Nothing will be published.', diagnostic)
        self.assertNotIn('publication is a separate job', diagnostic.lower())
        setup = (ROOT / '.github/actions/setup/action.yml').read_text(encoding='utf-8')
        self.assertEqual('name: Set up Android build', setup.splitlines()[0])
        self.assertIn('name: Set up JDK', setup)
        self.assertIn('name: Set up Android SDK', setup)
        self.assertIn('name: Resolve current Stable release', hold)

    def test_lifecycle_run_names_use_best_trigger_time_human_identity(self):
        ci = (ROOT / '.github/workflows/ci.yml').read_text(encoding='utf-8')
        ci_run_name = ci.split('\non:\n', 1)[0]
        self.assertIn("format('PR #{0} · {1}', github.event.pull_request.number, github.head_ref)", ci_run_name)
        self.assertIn("format('Validate · {0}', github.ref_name)", ci_run_name)
        self.assertIn("|| ' '", ci_run_name)

        self.assertFalse((ROOT / '.github/workflows/mosaic-development-release.yml').exists())
        self.assertFalse((ROOT / '.github/workflows/mosaic-development-resume.yml').exists())

        stable = (ROOT / '.github/workflows/mosaic-stable-promotion.yml').read_text(encoding='utf-8')
        self.assertEqual('run-name: Stable Promotion', stable.splitlines()[1])
        signing = (ROOT / '.github/workflows/mosaic-signing-exercise.yml').read_text(encoding='utf-8')
        self.assertEqual('run-name: Signing Diagnostic', signing.splitlines()[1])
        upstream_workflow = (ROOT / '.github/workflows/upstream-sync.yml').read_text(encoding='utf-8')
        self.assertIn("github.event_name == 'schedule' && 'Scheduled' || 'Manual'",
                      upstream_workflow.split('\non:\n', 1)[0])

    def test_mapping_workflow_is_conditional_separate_and_never_rebuilds(self):
        ci = (ROOT / development.CI_WORKFLOW).read_text(encoding='utf-8')
        mapping = ci.split('      - name: Prepare Release mapping diagnostic')[1].split('\n  sign-development:')[0]
        self.assertEqual(3, mapping.count("if: steps.eligibility.outputs.release_required == 'true'"))
        self.assertIn('mapping-${{ steps.prepared.outputs.name }}', mapping)
        self.assertIn('compression-level: 6', mapping)
        self.assertIn('retention-days: 7', mapping)
        self.assertNotIn('gradlew', mapping)
        self.assertIn('mosaic-main-mapping', mapping)
        self.assertIn('mosaic-main-release', mapping)

if __name__ == '__main__':
    unittest.main()
