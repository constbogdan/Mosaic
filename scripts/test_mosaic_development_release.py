"""Offline publisher state-machine and workflow boundary fixtures; never calls GitHub."""
import copy
import json
from pathlib import Path
import unittest
import tempfile
from unittest.mock import Mock, patch

import mosaic_development_release as release

ROOT = Path(__file__).resolve().parent.parent


class FakeGitHub:
    def __init__(self, repository=release.REPOSITORY):
        self.repository = repository
        self.refs, self.tags, self.releases, self.uploads = {}, {}, {}, {}
        self.calls = []
        self.fail_upload = False
        self.fail_after_delete_count = None
        self.delete_count = 0
        self.generated_tag_count = 0
        self.fail_final_rolling_publish = False

    def pages(self, path, key=None):
        if path == 'releases':
            return list(self.releases.values())
        if path.endswith('/assets'):
            rid = int(path.split('/')[1])
            return [a for a in self.uploads.values() if a['release'] == rid]
        raise AssertionError(path)

    def call(self, method, path, data=None, missing=False, upload=False):
        self.calls.append((method, path, copy.deepcopy(data)))
        if method == 'GET' and path.startswith('git/ref/tags/'):
            return self.refs.get(path.removeprefix('git/ref/tags/'))
        if method == 'POST' and path == 'git/tags':
            sha = str(len(self.tags) + 1) * 40
            self.tags[sha] = dict(tag=data['tag'], message=data['message'], object=dict(type='commit', sha=data['object'], url='fixture'))
            return dict(sha=sha)
        if method == 'GET' and path.startswith('git/tags/'):
            return self.tags[path.split('/')[-1]]
        if method == 'POST' and path == 'git/refs':
            tag = data['ref'].removeprefix('refs/tags/')
            if tag in self.refs:
                raise ValueError('ref conflict')
            self.refs[tag] = dict(object=dict(type='tag' if data['sha'] in self.tags else 'commit', sha=data['sha']))
            return self.refs[tag]
        if method == 'PATCH' and path == 'git/refs/tags/develop':
            self.refs['develop'] = dict(object=dict(type='commit', sha=data['sha']))
            return self.refs['develop']
        if method == 'DELETE' and path.startswith('git/refs/tags/'):
            self.refs.pop(path.removeprefix('git/refs/tags/'), None)
            return None
        if method == 'POST' and path == 'releases':
            rid = len(self.releases) + 1
            self.releases[rid] = dict(data, id=rid, immutable=False)
            return self.releases[rid]
        if method == 'GET' and path.startswith('releases/tags/'):
            tag = path.removeprefix('releases/tags/')
            matches = [item for item in self.releases.values()
                       if item['tag_name'] == tag and not item['draft']]
            if len(matches) > 1:
                raise AssertionError('duplicate fixture release tag')
            return matches[0] if matches else None
        if method == 'GET' and path.startswith('releases/'):
            return self.releases.get(int(path.split('/')[1]))
        if method == 'PATCH' and path.startswith('releases/'):
            if (self.fail_final_rolling_publish and data.get('draft') is False
                    and data.get('tag_name') == 'develop'):
                self.fail_final_rolling_publish = False
                raise ValueError('interrupted after rolling asset replacement')
            item = self.releases[int(path.split('/')[1])]
            was_draft = item.get('draft') is True
            item.update(data)
            develop_ref = self.refs.get('develop', {}).get('object', {})
            detached_develop = (item.get('tag_name') == 'develop'
                                and develop_ref.get('type') == 'commit'
                                and develop_ref.get('sha') != item.get('target_commitish'))
            if (was_draft and data.get('draft') is False and 'tag_name' not in data
                    and detached_develop):
                # Relevant live GitHub behavior: publishing the detached draft
                # without an explicit tag produced a generated untagged ref.
                self.generated_tag_count += 1
                tag = f"untagged-{self.generated_tag_count:020x}"
                item['tag_name'] = tag
                self.refs[tag] = dict(object=dict(
                    type='commit', sha=item.get('target_commitish', '0' * 40)
                ))
            elif data.get('draft') is False and item.get('tag_name') not in self.refs:
                self.refs[item['tag_name']] = dict(object=dict(
                    type='commit', sha=item.get('target_commitish', '0' * 40)
                ))
            return item
        if method == 'POST' and upload:
            if self.fail_upload:
                raise ValueError('interrupted upload')
            aid = max(self.uploads, default=0) + 1
            a = dict(id=aid, release=int(path.split('/')[1]), name=path.split('name=')[1],
                     size=len(data), digest='sha256:' + release.digest(data), state='uploaded')
            self.uploads[aid] = a
            return a
        if method == 'DELETE' and path.startswith('releases/assets/'):
            del self.uploads[int(path.split('/')[-1])]
            self.delete_count += 1
            if self.fail_after_delete_count == self.delete_count:
                raise ValueError('interrupted asset deletion')
            return None
        raise AssertionError((method, path))


class PublisherTests(unittest.TestCase):
    def setUp(self):
        self.apk = b'public fixture, not a signed APK'
        self.identity = dict(publication=True, dirty=False, sourceSha='a' * 40, sourceTree='b' * 40,
                             upstreamBaseline='c' * 40, epoch='d' * 40, versionName='1.0.5', versionCode=5, buildTime=123)
        self.env = dict(GITHUB_ACTIONS='true', GITHUB_REPOSITORY=release.REPOSITORY,
                        GITHUB_REF='refs/heads/main', GITHUB_REF_PROTECTED='true', GITHUB_SHA='a' * 40,
                        GITHUB_EVENT_NAME='push', GITHUB_RUN_ID='123', GITHUB_RUN_ATTEMPT='1',
                        GITHUB_WORKFLOW_REF=f'{release.REPOSITORY}/{release.CI_WORKFLOW}@refs/heads/main')
        self.policy = json.loads((ROOT / 'scripts/mosaic-signing.json').read_text())
        self.record = dict(schemaVersion=1, applicationId=self.policy['applicationId'],
                           certificateSha256=self.policy['expectedCertificateSha256'], signedApkSha256=release.digest(self.apk),
                           source=dict(self.identity, apkSha256='e' * 64, runId='123', runAttempt='1'))
        self.m = release.verified_manifest(
            self.record, self.apk, self.identity, '123', '1', self.policy, release.CI_WORKFLOW,
        )

    def newer_manifest(self, code=6, source='f' * 40):
        result = copy.deepcopy(self.m)
        result.update(
            immutableIdentity=f'downstream-build-{code}',
            versionCode=code,
            versionName=f'1.0.{code}',
            sourceSha=source,
        )
        result['source'].update(versionCode=code, versionName=f'1.0.{code}', sourceSha=source)
        return result

    def detach_rolling_as_authenticated_incident(self, api):
        rolling = next(item for item in api.releases.values() if item['tag_name'] == 'develop')
        tag = 'untagged-' + '1' * 20
        target = '9' * 40
        rolling['tag_name'] = tag
        rolling['target_commitish'] = target
        api.refs[tag] = dict(object=dict(type='commit', sha=target))
        assets = {
            item['name']: (item['size'], item['digest'])
            for item in api.uploads.values() if item['release'] == rolling['id']
        }
        profile = {
            'releaseId': rolling['id'],
            'tagName': tag,
            'targetCommitish': target,
            'versionCode': self.m['versionCode'],
            'versionName': self.m['versionName'],
            'sourceSha': self.m['sourceSha'],
            'apkSize': assets[release.APK_NAME][0],
            'apkDigest': assets[release.APK_NAME][1],
            'manifestSize': assets[release.MANIFEST_NAME][0],
            'manifestDigest': assets[release.MANIFEST_NAME][1],
        }
        return rolling, profile

    def test_manifest_mismatches(self):
        for field, value in [('applicationId', 'upstream'), ('certificateSha256', '0' * 64),
                             ('signedApkSha256', '0' * 64), ('schemaVersion', 2)]:
            with self.subTest(field=field), self.assertRaises(ValueError):
                release.verified_manifest(dict(self.record, **{field: value}), self.apk,
                                          self.identity, '123', '1', self.policy)
        for field, value in [('versionCode', 6), ('versionName', '1.0.6'), ('runId', '124'), ('runAttempt', '2'),
                             ('sourceSha', 'f' * 40), ('sourceTree', 'f' * 40), ('publication', False), ('dirty', True)]:
            record = copy.deepcopy(self.record)
            record['source'][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                release.verified_manifest(record, self.apk, self.identity, '123', '1', self.policy)
        with self.assertRaises(ValueError):
            release.verified_manifest(self.record, self.apk + b'tampered', self.identity,
                                      '123', '1', self.policy)

    def test_manifest_records_authoritative_ci_producer(self):
        record = copy.deepcopy(self.record)
        record['source']['runId'] = '100'
        record['source']['runAttempt'] = '2'
        manifest = release.verified_manifest(record, self.apk, self.identity,
                                             '100', '2', self.policy)
        self.assertEqual(manifest['buildWorkflow'], release.CI_WORKFLOW)
        self.assertEqual(manifest['buildRunId'], '100')
        self.assertEqual(manifest['buildRunAttempt'], '2')

    def test_same_run_artifact_accepts_prior_attempt_and_rejects_other_run(self):
        workflow = (ROOT / release.CI_WORKFLOW).read_text()
        signer = workflow.split('\n  sign-development:\n', 1)[1].split('\n  publish-development:\n', 1)[0]
        publisher = workflow.split('\n  publish-development:\n', 1)[1]
        self.assertIn('needs: release-build', signer)
        self.assertIn('needs: [release-build, sign-development]', publisher)
        self.assertIn('artifact-ids: ${{ needs.release-build.outputs.artifact_id }}', signer)
        self.assertIn('digest-mismatch: error', signer)
        self.assertNotIn('run-id:', signer)
        env = dict(self.env,
                   GITHUB_EVENT_NAME='push',
                   GITHUB_WORKFLOW_REF=f'{release.REPOSITORY}/{release.CI_WORKFLOW}@refs/heads/main',
                   GITHUB_RUN_ID='123', GITHUB_RUN_ATTEMPT='2',
                   MOSAIC_BUILD_RUN_ID='123', MOSAIC_BUILD_RUN_ATTEMPT='1',
                   MOSAIC_ARTIFACT_ID='456',
                   MOSAIC_ARTIFACT_NAME=release.ci_artifact_name(self.identity, '123', '1'))
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            (directory / 'provenance.json').write_text('{}')
            with patch.object(release, 'shallow_checkout_identity', return_value=self.identity), \
                    patch.object(release, 'verify_ci_artifact_directory') as verify:
                identity, ci = release.verify_same_run_artifact_directory(directory, env)
                self.assertEqual(identity, self.identity)
                self.assertEqual(ci, dict(workflow=release.CI_WORKFLOW, runId='123', runAttempt='1'))
                verify.assert_called_once()
        for field, value in [('MOSAIC_BUILD_RUN_ID', '124'),
                             ('MOSAIC_BUILD_RUN_ATTEMPT', '3'),
                             ('MOSAIC_ARTIFACT_ID', ''),
                             ('MOSAIC_ARTIFACT_ID', 'not-numeric'),
                             ('MOSAIC_ARTIFACT_NAME', 'wrong')]:
            with self.subTest(field=field), tempfile.TemporaryDirectory() as tmp:
                directory = Path(tmp)
                (directory / 'provenance.json').write_text('{}')
                with patch.object(release, 'shallow_checkout_identity', return_value=self.identity), \
                        self.assertRaises(ValueError):
                    release.verify_same_run_artifact_directory(directory, dict(env, **{field: value}))

    def test_same_run_artifact_transport_preserves_exact_id_digest_and_retention(self):
        workflow = (ROOT / release.CI_WORKFLOW).read_text()
        build = workflow.split('\n  release-build:\n', 1)[1].split('\n  sign-development:\n', 1)[0]
        signer = workflow.split('\n  sign-development:\n', 1)[1].split('\n  publish-development:\n', 1)[0]
        publisher = workflow.split('\n  publish-development:\n', 1)[1]
        self.assertIn('id: unsigned', build)
        self.assertIn('retention-days: 7', build)
        self.assertIn('artifact-ids: ${{ needs.release-build.outputs.artifact_id }}', signer)
        self.assertIn('digest-mismatch: error', signer)
        self.assertIn('MOSAIC_ARTIFACT_ID: ${{ needs.release-build.outputs.artifact_id }}', signer)
        self.assertIn('id: signed', signer)
        self.assertIn('retention-days: 7', signer)
        self.assertIn('artifact-ids: ${{ needs.sign-development.outputs.artifact_id }}', publisher)
        self.assertIn('digest-mismatch: error', publisher)

    def test_same_run_publication_requires_current_protected_main(self):
        api = Mock()
        api.call.return_value = {'protected': True, 'commit': {'sha': self.identity['sourceSha']}}
        release.require_current_protected_main(api, self.identity['sourceSha'])
        api.call.return_value = {'protected': True, 'commit': {'sha': 'f' * 40}}
        with self.assertRaises(ValueError):
            release.require_current_protected_main(api, self.identity['sourceSha'])

    def test_single_workflow_delivery_has_exactly_one_automatic_authority(self):
        ci = (ROOT / release.CI_WORKFLOW).read_text()
        release_build = ci.split('\n  release-build:\n', 1)[1].split('\n  sign-development:\n', 1)[0]
        signer = ci.split('\n  sign-development:\n', 1)[1].split('\n  publish-development:\n', 1)[0]
        publish = ci.split('\n  publish-development:\n', 1)[1]
        self.assertNotIn('false &&', release_build)
        self.assertNotIn('secrets.', release_build + publish)
        self.assertNotIn('contents: write', release_build + signer)
        self.assertNotIn('gradlew', signer + publish)
        self.assertIn('environment: release-sign', signer)
        self.assertEqual(signer.count('secrets.MOSAIC_'), 4)
        self.assertIn('contents: write', publish)
        self.assertNotIn('environment:', publish)
        self.assertIn('artifact-ids: ${{ needs.release-build.outputs.artifact_id }}', signer)
        self.assertIn('artifact-ids: ${{ needs.sign-development.outputs.artifact_id }}', publish)
        self.assertFalse((ROOT / release.LEGACY_DEVELOPMENT_WORKFLOW).exists())

    def test_contract_and_exact_rerun(self):
        api = FakeGitHub()
        with patch.object(api, 'pages', wraps=api.pages) as pages:
            release.publish(api, self.m, self.apk)
        self.assertGreaterEqual(
            sum(call.args[0] == 'releases' for call in pages.call_args_list), 1
        )
        self.assertEqual(set(api.refs), {'downstream-build-5', 'develop'})
        self.assertEqual({r['tag_name'] for r in api.releases.values()}, {'downstream-build-5', 'develop'})
        for r in api.releases.values():
            self.assertEqual(r['name'], 'v1.0.5')
            self.assertTrue(r['prerelease'])
            self.assertFalse(r['draft'])
            self.assertEqual(r['make_latest'], 'false')
        rolling = next(r for r in api.releases.values() if r['tag_name'] == 'develop')
        self.assertEqual(rolling['target_commitish'], self.m['sourceSha'])
        final_publish = [data for method, path, data in api.calls
                         if method == 'PATCH' and path == f"releases/{rolling['id']}"
                         and data.get('draft') is False]
        self.assertEqual(final_publish[-1]['tag_name'], 'develop')
        self.assertEqual(final_publish[-1]['target_commitish'], self.m['sourceSha'])
        self.assertTrue(any(method == 'GET' and path == 'releases/tags/develop'
                            for method, path, _ in api.calls))
        self.assertEqual(api.generated_tag_count, 0)
        self.assertEqual({a['name'] for a in api.uploads.values()}, {'Mosaic-release.apk', 'mosaic-release.json'})
        self.assertEqual(self.m['signedApkSha256'], release.digest(self.apk))
        self.assertEqual(self.m['immutableIdentity'], 'downstream-build-5')
        count = len(api.calls)
        release.publish(api, self.m, self.apk)
        self.assertTrue(all(method == 'GET' for method, _, _ in api.calls[count:]))

    def test_conflicting_bytes_or_provenance_never_replace_identity(self):
        api = FakeGitHub()
        release.publish(api, self.m, self.apk)
        for field, value in [('signedApkSha256', '0' * 64), ('sourceSha', 'f' * 40), ('buildRunAttempt', '2')]:
            count = len(api.calls)
            with self.assertRaises(ValueError):
                release.publish(api, dict(self.m, **{field: value}), self.apk)
            self.assertTrue(all(method == 'GET' for method, _, _ in api.calls[count:]))

    def test_rolling_reuse_and_no_immutable_mutation(self):
        api = FakeGitHub()
        release.publish(api, self.m, self.apk)
        rid = next(r['id'] for r in api.releases.values() if r['tag_name'] == 'develop')
        newer = self.newer_manifest()
        count = len(api.calls)
        release.publish(api, newer, self.apk)
        self.assertEqual(api.releases[rid]['name'], 'v1.0.6')
        self.assertEqual(api.refs['downstream-build-5']['object']['sha'], '1' * 40)
        changes = api.calls[count:]
        self.assertTrue(any(method == 'PATCH' and path == f'releases/{rid}' and data.get('draft') is True for method, path, data in changes))
        self.assertFalse(any(method == 'DELETE' and re_path == f'releases/{rid}' for method, re_path, _ in changes))
        with self.assertRaises(ValueError):
            release.publish(api, self.m, self.apk)

    def test_interruption_reserves_identity_and_exact_resume(self):
        api = FakeGitHub()
        api.fail_upload = True
        with self.assertRaises(ValueError):
            release.publish(api, self.m, self.apk)
        self.assertIn('downstream-build-5', api.refs)
        self.assertNotIn('develop', api.refs)
        self.assertTrue(all(r['draft'] for r in api.releases.values()))
        api.fail_upload = False
        with self.assertRaises(ValueError):
            release.publish(api, dict(self.m, signedApkSha256='f' * 64), self.apk)
        release.publish(api, self.m, self.apk)
        self.assertFalse(any(r['draft'] for r in api.releases.values()))

    def test_remote_asset_tampering_fails(self):
        api = FakeGitHub()
        release.publish(api, self.m, self.apk)
        api.uploads[1]['digest'] = 'sha256:' + '0' * 64
        with self.assertRaises(ValueError):
            release.publish(api, self.m, self.apk)

    def test_immutable_rolling_setting_fails_before_mutation(self):
        api = FakeGitHub()
        release.publish(api, self.m, self.apk)
        next(r for r in api.releases.values() if r['tag_name'] == 'develop')['immutable'] = True
        count = len(api.calls)
        with self.assertRaises(ValueError):
            release.publish(api, self.m, self.apk)

    def test_unexpected_rolling_asset_refuses_before_any_mutation(self):
        api = FakeGitHub()
        release.publish(api, self.m, self.apk)
        rolling = next(item for item in api.releases.values() if item['tag_name'] == 'develop')
        aid = max(api.uploads) + 1
        api.uploads[aid] = dict(
            id=aid, release=rolling['id'], name='Wholphin-release.apk', state='uploaded',
            size=3, digest='sha256:' + release.digest(b'old'),
        )
        count = len(api.calls)
        with self.assertRaisesRegex(ValueError, 'Unexpected rolling asset inventory'):
            release.publish(api, self.newer_manifest(), self.apk)
        self.assertTrue(all(method == 'GET' for method, _, _ in api.calls[count:]))
        self.assertIn(aid, api.uploads)
        self.assertEqual(api.refs['develop']['object']['sha'], self.m['sourceSha'])

    def test_interruption_after_one_asset_deletion_resumes_exactly(self):
        api = FakeGitHub()
        release.publish(api, self.m, self.apk)
        rolling = next(item for item in api.releases.values() if item['tag_name'] == 'develop')
        newer = self.newer_manifest()
        api.fail_after_delete_count = 1
        with self.assertRaisesRegex(ValueError, 'interrupted asset deletion'):
            release.publish(api, newer, self.apk)
        self.assertTrue(api.releases[rolling['id']]['draft'])
        self.assertEqual(api.refs['develop']['object']['sha'], newer['sourceSha'])
        self.assertEqual(
            1, len([item for item in api.uploads.values() if item['release'] == rolling['id']])
        )

        api.fail_after_delete_count = None
        release.publish(api, newer, self.apk)
        final = api.releases[rolling['id']]
        self.assertEqual(final['tag_name'], 'develop')
        self.assertFalse(final['draft'])
        self.assertEqual(final['name'], 'v1.0.6')

    def test_interruption_after_asset_replacement_resumes_exactly(self):
        api = FakeGitHub()
        release.publish(api, self.m, self.apk)
        rolling = next(item for item in api.releases.values() if item['tag_name'] == 'develop')
        newer = self.newer_manifest()
        api.fail_final_rolling_publish = True
        with self.assertRaisesRegex(ValueError, 'interrupted after rolling asset replacement'):
            release.publish(api, newer, self.apk)
        self.assertTrue(api.releases[rolling['id']]['draft'])
        self.assertEqual(api.releases[rolling['id']]['name'], 'v1.0.5')
        current = {
            item['name']: (item['size'], item['digest'])
            for item in api.uploads.values() if item['release'] == rolling['id']
        }
        self.assertEqual(current, release._expected_asset_identities({
            release.APK_NAME: self.apk,
            release.MANIFEST_NAME: release.canonical(newer),
        }))

        release.publish(api, newer, self.apk)
        self.assertFalse(api.releases[rolling['id']]['draft'])
        self.assertEqual(api.releases[rolling['id']]['name'], 'v1.0.6')

    def test_fake_models_omitted_tag_as_generated_untagged_publication(self):
        api = FakeGitHub()
        api.refs['develop'] = dict(object=dict(type='commit', sha=self.m['sourceSha']))
        draft = api.call('POST', 'releases', dict(
            tag_name='develop', target_commitish='9' * 40,
            **release.release_fields(self.m, True, compare_from=None),
        ))
        result = api.call('PATCH', f"releases/{draft['id']}", release.release_fields(
            self.m, False, compare_from=None,
        ))
        self.assertRegex(result['tag_name'], r'^untagged-[0-9a-f]{20}$')
        self.assertEqual(api.refs[result['tag_name']]['object']['sha'], '9' * 40)
        self.assertEqual(api.generated_tag_count, 1)

    def test_exact_detached_r2_state_is_rebound_and_published(self):
        api = FakeGitHub()
        release.publish(api, self.m, self.apk)
        rolling, profile = self.detach_rolling_as_authenticated_incident(api)
        newer = self.newer_manifest()
        with patch.object(release, 'R2_DETACHED_RECOVERY', profile):
            release.publish(api, newer, self.apk)
            self.assertEqual(api.releases[rolling['id']]['tag_name'], 'develop')
            self.assertEqual(api.releases[rolling['id']]['name'], 'v1.0.6')
            self.assertNotIn(profile['tagName'], api.refs)
            self.assertEqual(api.refs['develop']['object']['sha'], newer['sourceSha'])
            self.assertEqual(api.generated_tag_count, 0)

            count = len(api.calls)
            release.publish(api, newer, self.apk)
            self.assertTrue(all(method == 'GET' for method, _, _ in api.calls[count:]))

    def test_detached_recovery_interruption_after_rebind_resumes_exactly(self):
        api = FakeGitHub()
        release.publish(api, self.m, self.apk)
        rolling, profile = self.detach_rolling_as_authenticated_incident(api)
        newer = self.newer_manifest()
        api.fail_after_delete_count = 1
        with patch.object(release, 'R2_DETACHED_RECOVERY', profile), \
                self.assertRaisesRegex(ValueError, 'interrupted asset deletion'):
            release.publish(api, newer, self.apk)
        self.assertEqual(rolling['tag_name'], 'develop')
        self.assertTrue(rolling['draft'])
        self.assertIn(profile['tagName'], api.refs)
        self.assertEqual(api.refs['develop']['object']['sha'], newer['sourceSha'])

        api.fail_after_delete_count = None
        with patch.object(release, 'R2_DETACHED_RECOVERY', profile):
            release.publish(api, newer, self.apk)
        self.assertEqual(rolling['tag_name'], 'develop')
        self.assertEqual(rolling['target_commitish'], newer['sourceSha'])
        self.assertFalse(rolling['draft'])
        self.assertNotIn(profile['tagName'], api.refs)
        self.assertEqual(api.generated_tag_count, 0)

    def test_detached_recovery_wrong_digest_or_source_refuses_without_mutation(self):
        for defect in ('digest', 'source'):
            with self.subTest(defect=defect):
                api = FakeGitHub()
                release.publish(api, self.m, self.apk)
                rolling, profile = self.detach_rolling_as_authenticated_incident(api)
                if defect == 'digest':
                    apk = next(item for item in api.uploads.values()
                               if item['release'] == rolling['id'] and item['name'] == release.APK_NAME)
                    apk['digest'] = 'sha256:' + '0' * 64
                    expected = 'asset/provenance mismatch'
                else:
                    api.refs['develop']['object']['sha'] = '8' * 40
                    expected = 'source does not match develop'
                count = len(api.calls)
                with patch.object(release, 'R2_DETACHED_RECOVERY', profile), \
                        self.assertRaisesRegex(ValueError, expected):
                    release.publish(api, self.newer_manifest(), self.apk)
                self.assertTrue(all(method == 'GET' for method, _, _ in api.calls[count:]))

    def test_multiple_detached_candidates_refuse_without_mutation(self):
        api = FakeGitHub()
        release.publish(api, self.m, self.apk)
        rolling, profile = self.detach_rolling_as_authenticated_incident(api)
        duplicate = copy.deepcopy(rolling)
        duplicate.update(id=99, tag_name='untagged-' + '2' * 20)
        api.releases[99] = duplicate
        count = len(api.calls)
        with patch.object(release, 'R2_DETACHED_RECOVERY', profile), \
                self.assertRaisesRegex(ValueError, 'Ambiguous detached rolling recovery state'):
            release.publish(api, self.newer_manifest(), self.apk)
        self.assertTrue(all(method == 'GET' for method, _, _ in api.calls[count:]))

    def test_orphan_develop_without_authenticated_candidate_refuses(self):
        api = FakeGitHub()
        api.refs['develop'] = dict(object=dict(type='commit', sha=self.m['sourceSha']))
        count = len(api.calls)
        with self.assertRaisesRegex(ValueError, 'Orphan develop tag without an authenticated recovery candidate'):
            release.publish(api, self.newer_manifest(), self.apk)
        self.assertTrue(all(method == 'GET' for method, _, _ in api.calls[count:]))

    def test_published_development_source_requires_matching_release_tag_and_assets(self):
        api = FakeGitHub()
        release.publish(api, self.m, self.apk)
        with patch.object(api, 'pages', wraps=api.pages) as pages:
            self.assertEqual(release.published_development_source(api), self.m['sourceSha'])
        self.assertEqual(1, sum(call.args[0] == 'releases' for call in pages.call_args_list))

        rolling_asset = next(
            asset for asset in api.uploads.values()
            if asset['release'] == next(
                item['id'] for item in api.releases.values() if item['tag_name'] == 'develop'
            ) and asset['name'] == release.APK_NAME
        )
        rolling_asset['digest'] = 'sha256:' + '0' * 64
        with self.assertRaisesRegex(ValueError, 'differs'):
            release.published_development_source(api)

    def test_unresolved_published_baseline_requires_conservative_release(self):
        result = release.release_eligibility(FakeGitHub(), ROOT, 'a' * 40)
        self.assertEqual(result['outcome'], 'ready')
        self.assertEqual(result['releaseRelevance'], release.change_classification.UNKNOWN)
        self.assertEqual(result['validationRisk'], release.change_classification.HIGH)
        self.assertTrue(result['releaseRequired'])

    def test_unreadable_changed_path_requires_conservative_release(self):
        api = FakeGitHub()
        release.publish(api, self.m, self.apk)
        with patch.object(
            release.change_classification,
            'classify_range',
            side_effect=UnicodeError('unreadable path'),
        ):
            result = release.release_eligibility(api, ROOT, 'f' * 40)
        self.assertEqual(result['releaseRelevance'], release.change_classification.UNKNOWN)
        self.assertEqual(result['validationRisk'], release.change_classification.HIGH)
        self.assertTrue(result['releaseRequired'])

    def test_eligibility_classifies_from_authenticated_published_source(self):
        api = FakeGitHub()
        release.publish(api, self.m, self.apk)
        classified = dict(
            releaseRelevance=release.change_classification.DOCS_ONLY,
            validationRisk=release.change_classification.LOW,
            releaseRequired=False,
            paths=[],
            baselineSha=self.m['sourceSha'],
            currentSha='f' * 40,
        )
        with patch.object(release.change_classification, 'classify_range', return_value=classified) as classify:
            result = release.release_eligibility(api, ROOT, 'f' * 40)
        classify.assert_called_once_with(ROOT, self.m['sourceSha'], 'f' * 40)
        self.assertEqual(result['outcome'], 'skipped_non_apk')
        self.assertFalse(result['releaseRequired'])

    def test_ci_guard_requires_exact_protected_main_push(self):
        env = dict(GITHUB_ACTIONS='true', GITHUB_REPOSITORY=release.REPOSITORY,
                   GITHUB_REF='refs/heads/main', GITHUB_REF_PROTECTED='true',
                   GITHUB_EVENT_NAME='push', GITHUB_SHA='a' * 40,
                   GITHUB_WORKFLOW_REF=f'{release.REPOSITORY}/{release.CI_WORKFLOW}@refs/heads/main')
        self.assertEqual(release.ci_guard(env), 'a' * 40)
        mosaic = dict(
            env,
            GITHUB_REPOSITORY='constbogdan/Mosaic',
            GITHUB_WORKFLOW_REF=(
                f'constbogdan/Mosaic/{release.CI_WORKFLOW}@refs/heads/main'
            ),
        )
        self.assertEqual(release.ci_guard(mosaic), 'a' * 40)
        for field, value in [('GITHUB_REPOSITORY', 'fork/Wholphin'), ('GITHUB_REF', 'refs/heads/feature'),
                             ('GITHUB_REF_PROTECTED', 'false'), ('GITHUB_EVENT_NAME', 'pull_request'),
                             ('GITHUB_SHA', 'main'), ('GITHUB_WORKFLOW_REF', 'other')]:
            with self.subTest(field=field), self.assertRaises(ValueError):
                release.ci_guard(dict(env, **{field: value}))
        for repository in ('constbogdan/Mosaic2', 'other/Mosaic', 'constbogdan/mosaic', ''):
            with self.subTest(repository=repository), self.assertRaises(ValueError):
                release.ci_guard(dict(
                    env,
                    GITHUB_REPOSITORY=repository,
                    GITHUB_WORKFLOW_REF=(
                        f'{repository}/{release.CI_WORKFLOW}@refs/heads/main'
                    ),
                ))

    def test_github_api_target_requires_authenticated_exact_repository(self):
        with patch.dict('os.environ', GITHUB_TOKEN='fixture'):
            self.assertEqual('constbogdan/Mosaic', release.GitHub('constbogdan/Mosaic').repository)
            for repository in ('constbogdan/Mosaic2', 'other/Mosaic', ''):
                with self.subTest(repository=repository), self.assertRaises(ValueError):
                    release.GitHub(repository)

    def test_downloaded_ci_artifact_requires_exact_provenance_and_bytes(self):
        import zipfile
        artifact = dict(artifactId='456', artifactName='fixture', artifactDigest='sha256:' + 'f' * 64,
                        runId='100', runAttempt='2', versionName=self.identity['versionName'])
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            with zipfile.ZipFile(directory / 'unsigned.apk', 'w') as archive:
                archive.writestr('classes.dex', b'public fixture')
            from mosaic_version import artifact_record
            provenance = artifact_record(self.identity, directory / 'unsigned.apk')
            provenance.update(runId='100', runAttempt='2')
            (directory / 'provenance.json').write_text(json.dumps(provenance))
            self.assertEqual(release.verify_ci_artifact_directory(directory, self.identity, artifact), provenance)
            provenance['runId'] = '101'
            (directory / 'provenance.json').write_text(json.dumps(provenance))
            with self.assertRaises(ValueError):
                release.verify_ci_artifact_directory(directory, self.identity, artifact)

    def test_workflow_boundaries_main_owns_release_and_development_has_zero_gradle(self):
        ci = (ROOT / release.CI_WORKFLOW).read_text()
        validation, remainder = ci.split('\n  release-build:\n')
        build, remainder = remainder.split('\n  sign-development:\n')
        signer, publish = remainder.split('\n  publish-development:\n')
        self.assertIn('needs: full-validation', build)
        self.assertIn("github.event_name == 'push'", build)
        self.assertIn("github.ref == 'refs/heads/main' && github.ref_protected", build)
        self.assertIn('mosaic_development_release.py ci-eligibility', build)
        self.assertIn('Build unsigned Development Release APK', build)
        self.assertNotIn('secrets.', build + publish)
        self.assertNotIn('contents: write', build + signer)
        self.assertNotIn('gradlew', signer + publish)
        self.assertIn('environment: release-sign', signer)
        self.assertEqual(signer.count('secrets.MOSAIC_'), 4)
        self.assertIn('contents: write', publish)
        self.assertNotIn('environment:', publish)
        self.assertIn('artifact-ids: ${{ needs.release-build.outputs.artifact_id }}', signer)
        self.assertNotIn('run-id:', signer)
        self.assertIn('mosaic_development_release.py ci-artifact', signer)
        self.assertIn('verify_mosaic_apk.py', signer)
        self.assertIn('artifact-ids: ${{ needs.sign-development.outputs.artifact_id }}', publish)
        self.assertIn('mosaic_development_release.py ci-manifest', publish)
        self.assertIn('mosaic_development_release.py ci-publish', publish)
        self.assertNotIn('Build unsigned Development Release APK', validation)
        self.assertEqual(ci.count('./gradlew '), 2)
        self.assertIn(
            "steps.main-validation-reuse.outputs.reuse_validation != 'true'", ci
        )
        self.assertNotIn('Run targeted Android validation', ci)
        self.assertIn(
            "steps.pr-validation.outputs.validation_mode != 'non-android'", ci
        )
        self.assertIn(':app:assembleDefaultRelease -PmosaicPublication=true --no-daemon --no-parallel --max-workers=1', ci)
        self.assertIn('MOSAIC_ARTIFACT_PREFIX: mosaic-main-ci', ci)

        self.assertFalse((ROOT / '.github/workflows/mosaic-development-release.yml').exists())
        self.assertFalse((ROOT / '.github/workflows/mosaic-development-resume.yml').exists())


if __name__ == '__main__':
    unittest.main()
