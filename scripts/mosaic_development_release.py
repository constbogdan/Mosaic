"""Fail-closed Mosaic development publisher. No signing keys or build commands."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request

import mosaic_change_classification as change_classification
from mosaic_version import EPOCH, UPSTREAM_BASELINE, allocate, git
from mosaic_signing_exercise import artifact_name, payload, shallow_checkout_identity, validate_record
from verify_mosaic_apk import fingerprint
from mosaic_delivery_output import append_summary, publication_summary, release_body
from mosaic_repository import (
    MOSAIC_DOWNSTREAM_REPOSITORY,
    authenticate_downstream_repository,
    authenticate_workflow_repository,
)

# Current publication defaults to the canonical repository; authentication retains the R1 bridge.
REPOSITORY = MOSAIC_DOWNSTREAM_REPOSITORY
LEGACY_DEVELOPMENT_WORKFLOW = '.github/workflows/mosaic-development-release.yml'
CI_WORKFLOW = '.github/workflows/ci.yml'
CI_JOB = 'Full validation'
APK_NAME = 'Mosaic-release.apk'
MANIFEST_NAME = 'mosaic-release.json'

# One bounded recovery profile for the live R2 interruption.  This is deliberately
# not a generic "adopt an untagged release" mechanism: every stable identity and
# byte-level fact observed after run 35361944415 attempt 2 must authenticate.
R2_DETACHED_RECOVERY = {
    'releaseId': 385461835,
    'tagName': 'untagged-0d30e3a083c52d7b0ee5',
    'targetCommitish': '41f9f83c36b8866211c9680d3b416d5ebede4888',
    'versionCode': 72,
    'versionName': '1.0.72',
    'sourceSha': '44fcf58043272ce07b200fab74cb869b21d720d6',
    'apkSize': 27821299,
    'apkDigest': 'sha256:f3e33b7f70488bbd8ac171f3cac69b318046bad9c63eafef928b37eb8b8864a3',
    'manifestSize': 1347,
    'manifestDigest': 'sha256:746a975ba919809f420f796e853bd8075d08563d351c53e2fab65edda1b9b8eb',
}


def canonical(value):
    return (json.dumps(value, sort_keys=True, indent=2) + '\n').encode('utf-8')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def ci_guard(env):
    authenticate_workflow_repository(env, CI_WORKFLOW)
    expected = dict(GITHUB_ACTIONS='true', GITHUB_REF='refs/heads/main', GITHUB_REF_PROTECTED='true',
                    GITHUB_EVENT_NAME='push')
    sha = env.get('GITHUB_SHA', '')
    if (any(env.get(k) != v for k, v in expected.items())
            or not re.fullmatch('[0-9a-f]{40}', sha)
            ):
        raise ValueError('Release classification requires exact protected-main push CI')
    return sha


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class GitHub:
    """Fixed repository/hosts only; credentials and response bodies never enter errors."""
    def __init__(self, repository):
        self.repository = authenticate_downstream_repository(repository)

    def call(self, method, path, data=None, missing=False, upload=False):
        host = 'uploads.github.com' if upload else 'api.github.com'
        url = f'https://{host}/repos/{self.repository}/{path}'
        body = data if upload else (canonical(data) if data is not None else None)
        request = urllib.request.Request(url, data=body, method=method, headers={
            'Authorization': 'Bearer ' + os.environ['GH_TOKEN'],
            'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28',
            'Content-Type': 'application/octet-stream' if upload else 'application/json',
            'User-Agent': 'mosaic-development-publisher'})
        try:
            with urllib.request.build_opener(NoRedirect).open(request, timeout=180) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            if missing and error.code == 404:
                return None
            raise ValueError(f'GitHub {method} failed (HTTP {error.code}); stopped without fallback') from None
        except OSError:
            raise ValueError('GitHub transport failed; inspect public state before retry') from None

    def pages(self, path, key=None):
        result = []
        for page in range(1, 101):
            data = self.call('GET', path + ('&' if '?' in path else '?') + f'per_page=100&page={page}')
            entries = data[key] if key else data
            result.extend(entries)
            if len(entries) < 100:
                return result
        raise ValueError('Pagination limit reached; refusing incomplete inspection')


def trusted_ci(api, sha, *, require_tip=True):
    branch = api.call('GET', 'branches/main')
    if branch.get('protected') is not True or (require_tip and branch['commit']['sha'] != sha):
        raise ValueError('Approved SHA is no longer protected main tip')
    workflow = api.call('GET', 'actions/workflows/ci.yml')
    runs = api.pages(f'actions/workflows/ci.yml/runs?head_sha={sha}&event=push', 'workflow_runs')
    candidates = [r for r in runs if r.get('head_sha') == sha and r.get('head_branch') == 'main'
                  and r.get('event') == 'push' and r.get('workflow_id') == workflow['id']
                  and r.get('head_repository', {}).get('full_name') == api.repository]
    if not candidates:
        raise ValueError('No authoritative push CI for exact main SHA; wait for CI, then authorize again')
    run = max(candidates, key=lambda r: r['id'])
    if run.get('status') != 'completed' or run.get('conclusion') != 'success':
        raise ValueError('Latest exact-main push CI has not completed successfully')
    jobs = api.pages(f"actions/runs/{run['id']}/attempts/{run['run_attempt']}/jobs", 'jobs')
    full = [j for j in jobs if j.get('name') == CI_JOB]
    if len(full) != 1 or full[0].get('conclusion') != 'success' or full[0].get('status') != 'completed' or full[0].get('head_sha') != sha:
        raise ValueError('Required exact-SHA Full validation did not succeed')
    return {'workflow': CI_WORKFLOW, 'runId': str(run['id']), 'runAttempt': str(run['run_attempt'])}


def historical_identity(root, source, execution_sha):
    """Reconstruct a published first-parent identity without checking out old source."""
    if git(root, 'rev-parse', '--is-shallow-repository') != 'false':
        raise ValueError('Stable promotion requires full history')
    if git(root, 'rev-parse', 'HEAD') != execution_sha or git(root, 'status', '--porcelain', '--untracked-files=normal'):
        raise ValueError('Stable tooling checkout must be clean and exact')
    chain = git(root, 'rev-list', '--first-parent', 'HEAD').splitlines()
    if source not in chain or EPOCH not in chain or chain.index(source) >= chain.index(EPOCH):
        raise ValueError('Source is not a publishable protected-main first-parent ancestor')
    number = chain.index(EPOCH) - chain.index(source)
    if not 1 <= number <= 2100000000:
        raise ValueError('Invalid historical version allocation')
    return dict(versionCode=number, versionName=f'1.0.{number}', sourceSha=source,
                sourceTree=git(root, 'rev-parse', source + '^{tree}'),
                buildTime=int(git(root, 'show', '-s', '--format=%ct', source)) * 1000,
                dirty=False, publication=True, epoch=EPOCH, upstreamBaseline=UPSTREAM_BASELINE)


def run_identity(run, sha, workflow, repository=REPOSITORY):
    repository = authenticate_downstream_repository(repository)
    allowed_events = ('push',) if workflow == CI_WORKFLOW else ('workflow_dispatch', 'workflow_run')
    if (run.get('repository', {}).get('full_name') != repository
            or run.get('head_repository', {}).get('full_name') != repository
            or run.get('event') not in allowed_events
            or run.get('head_branch') != 'main'
            or run.get('head_sha') != sha or run.get('path') != workflow
            or run.get('status') != 'completed'):
        raise ValueError('Untrusted artifact-producing workflow/run')


def successful_job(api, run, attempt, names):
    jobs = api.pages(f"actions/runs/{run}/attempts/{attempt}/jobs", 'jobs')
    matches = [job for job in jobs if job.get('name') in names]
    if len(matches) != 1 or matches[0].get('status') != 'completed' or matches[0].get('conclusion') != 'success':
        raise ValueError('Artifact-producing job did not succeed in the original attempt')
    return matches[0]


def validate_original_source(api, record, identity):
    """Authenticate the producer retained in current or historical Development manifests."""
    run, attempt = record.get('runId', ''), record.get('runAttempt', '')
    if not all(re.fullmatch('[1-9][0-9]*', str(value)) for value in (run, attempt)):
        raise ValueError('Missing original build identity')
    original = api.call('GET', f'actions/runs/{run}')
    workflow = original.get('path')
    if workflow == CI_WORKFLOW:
        run_identity(original, identity['sourceSha'], CI_WORKFLOW, api.repository)
        if original.get('conclusion') != 'success':
            raise ValueError('Original main CI did not complete successfully')
        successful_job(api, run, attempt, [CI_JOB])
    elif workflow == LEGACY_DEVELOPMENT_WORKFLOW:
        run_identity(original, identity['sourceSha'], LEGACY_DEVELOPMENT_WORKFLOW, api.repository)
        successful_job(api, run, attempt, ['build'])
    else:
        raise ValueError('Original source provenance is not from an approved build workflow')
    expected = dict(identity, apkSha256=record.get('apkSha256'), runId=run, runAttempt=attempt)
    if record != expected or not re.fullmatch('[0-9a-f]{64}', record.get('apkSha256', '')):
        raise ValueError('Original source provenance mismatch')
    return workflow


def ci_artifact_name(identity, run, attempt):
    return 'unsigned-' + artifact_name(identity, run, attempt, 'mosaic-main-ci')


def verify_ci_artifact_directory(directory, identity, artifact):
    record = json.loads((directory / 'provenance.json').read_text(encoding='utf-8'))
    validate_record(record, identity, directory / 'unsigned.apk', artifact['runId'], artifact['runAttempt'])
    if b'APK Sig Block 42' in (directory / 'unsigned.apk').read_bytes():
        raise ValueError('Expected unsigned APK')
    payload(directory / 'unsigned.apk')
    return record


def ci_identity_from_env(env):
    ci = {
        'workflow': CI_WORKFLOW,
        'runId': env.get('MOSAIC_BUILD_RUN_ID'),
        'runAttempt': env.get('MOSAIC_BUILD_RUN_ATTEMPT'),
    }
    if any(not re.fullmatch('[1-9][0-9]*', str(ci[field] or '')) for field in ('runId', 'runAttempt')):
        raise ValueError('Missing authoritative CI run identity')
    return ci


def same_run_ci_identity(env):
    """Authenticate a build job from this protected-main workflow run.

    A failed downstream job may be retried in a later run attempt while consuming the
    successful build artifact from an earlier attempt of the same immutable run.
    """
    ci = ci_identity_from_env(env)
    current_run = env.get('GITHUB_RUN_ID', '')
    current_attempt = env.get('GITHUB_RUN_ATTEMPT', '')
    if (ci['runId'] != current_run
            or not re.fullmatch('[1-9][0-9]*', current_attempt)
            or int(ci['runAttempt']) > int(current_attempt)):
        raise ValueError('Build artifact is not from this workflow run or an accepted prior attempt')
    return ci


def verify_same_run_artifact_directory(directory, env):
    ci_guard(env)
    record = json.loads((directory / 'provenance.json').read_text(encoding='utf-8'))
    identity = shallow_checkout_identity(Path(__file__).resolve().parent.parent, record)
    ci = same_run_ci_identity(env)
    expected_name = ci_artifact_name(identity, ci['runId'], ci['runAttempt'])
    if (not re.fullmatch('[1-9][0-9]*', env.get('MOSAIC_ARTIFACT_ID', ''))
            or env.get('MOSAIC_ARTIFACT_NAME') != expected_name):
        raise ValueError('Downloaded same-run artifact selection differs from build output')
    verify_ci_artifact_directory(directory, identity, {
        'runId': ci['runId'],
        'runAttempt': ci['runAttempt'],
    })
    return identity, ci


def require_current_protected_main(api, sha):
    branch = api.call('GET', 'branches/main')
    if branch.get('protected') is not True or branch.get('commit', {}).get('sha') != sha:
        raise ValueError('Publication source is no longer the protected main tip')


def verified_manifest(record, apk, identity, run, attempt, policy, build_workflow=CI_WORKFLOW):
    """Pure byte/provenance validation shared after normal or recovery trust gates."""
    sha = identity['sourceSha']
    source = record.get('source', {})
    expected = dict(identity, apkSha256=source.get('apkSha256'),
                    runId=run, runAttempt=attempt)
    if source != expected or identity.get('sourceSha') != sha or identity.get('publication') is not True or identity.get('dirty') is not False:
        raise ValueError('Signed artifact source/version/run provenance mismatch')
    for field in ('sourceSha', 'sourceTree', 'upstreamBaseline', 'epoch'):
        if not re.fullmatch('[0-9a-f]{40}', str(source.get(field, ''))):
            raise ValueError('Invalid source provenance')
    code = source.get('versionCode')
    if type(code) is not int or not 1 <= code <= 2100000000 or source.get('versionName') != f'1.0.{code}':
        raise ValueError('Invalid version identity')
    for field in ('runId', 'runAttempt'):
        if not re.fullmatch('[1-9][0-9]*', source[field]):
            raise ValueError('Invalid run identity')
    if not re.fullmatch('[0-9a-f]{64}', str(source.get('apkSha256', ''))):
        raise ValueError('Missing unsigned APK hash')
    cert = fingerprint(policy.get('expectedCertificateSha256'))
    if (policy.get('schemaVersion') != 1 or policy.get('applicationId') != 'io.github.constbogdan.mosaic'
            or record.get('schemaVersion') != 1 or record.get('applicationId') != policy['applicationId']
            or record.get('certificateSha256') != cert or record.get('signedApkSha256') != digest(apk)):
        raise ValueError('Signed APK hash/package/certificate mismatch')
    return dict(schemaVersion=1, applicationId=record['applicationId'], versionName=source['versionName'],
                versionCode=code, sourceSha=sha, sourceTree=source['sourceTree'], upstreamBaseline=source['upstreamBaseline'],
                buildWorkflow=build_workflow, buildRunId=source['runId'], buildRunAttempt=source['runAttempt'],
                publication=True, unsignedApkSha256=source['apkSha256'], signedApkSha256=digest(apk),
                signerSha256=cert, immutableIdentity=f'downstream-build-{code}', rollingChannel='develop',
                assetName=APK_NAME, source=source)


def release_fields(m, draft, *, archive=False, compare_from=None, repository=REPOSITORY):
    return dict(name='v' + m['versionName'], draft=draft, prerelease=True, make_latest='false',
                body=release_body(m, 'Development', archive=archive, compare_from=compare_from,
                                  repository=repository))


def check_asset(asset, name, data):
    if (asset.get('name') != name or asset.get('state') != 'uploaded'
            or asset.get('size') != len(data) or asset.get('digest') != 'sha256:' + digest(data)):
        raise ValueError('Release asset differs from immutable recorded bytes')


def find_release(api, tag, releases=None):
    # Listing includes drafts; GET by tag alone may not recover a partially created draft.
    matches = [r for r in (api.pages('releases') if releases is None else releases) if r['tag_name'] == tag]
    if len(matches) > 1:
        raise ValueError('Duplicate release identity')
    return matches[0] if matches else None


def _release_assets(api, release, manifest):
    found = api.pages(f"releases/{release['id']}/assets")
    if len(found) != 2 or {asset.get('name') for asset in found} != {APK_NAME, MANIFEST_NAME}:
        raise ValueError('Development release does not have the exact published asset set')
    result = {asset['name']: asset for asset in found}
    for asset in result.values():
        if (asset.get('state') != 'uploaded' or type(asset.get('size')) is not int
                or asset['size'] < 1 or not re.fullmatch(r'sha256:[0-9a-f]{64}', str(asset.get('digest', '')))):
            raise ValueError('Development release asset identity is incomplete')
    if result[MANIFEST_NAME]['digest'] != 'sha256:' + digest(canonical(manifest)):
        raise ValueError('Development manifest asset differs from immutable tag provenance')
    if result[APK_NAME]['digest'] != 'sha256:' + manifest.get('signedApkSha256', ''):
        raise ValueError('Development APK asset differs from immutable tag provenance')
    return {name: (asset['size'], asset['digest']) for name, asset in result.items()}


def _expected_asset_identities(expected):
    return {name: (len(data), 'sha256:' + digest(data)) for name, data in expected.items()}


def _inspect_asset_subset(api, release, accepted, *, require_complete, message):
    """Authenticate a rolling asset set without mutating it.

    Draft replacement may be interrupted after any delete or upload.  Every
    surviving asset must therefore be an exact old or new byte identity; an
    unknown name, duplicate, or third digest always refuses before mutation.
    """
    actual = api.pages(f"releases/{release['id']}/assets")
    names = [asset.get('name') for asset in actual]
    if len(set(names)) != len(names) or any(name not in accepted for name in names):
        raise ValueError(message)
    for asset in actual:
        identity = (asset.get('size'), asset.get('digest'))
        if asset.get('state') != 'uploaded' or identity not in accepted[asset['name']]:
            raise ValueError(message)
    if require_complete and set(names) != set(accepted):
        raise ValueError(message)
    return actual


def _immutable_version(api, releases, number):
    """Authenticate one already-published immutable Development identity."""
    tag = f'downstream-build-{number}'
    ref = api.call('GET', f'git/ref/tags/{tag}', missing=True)
    if not ref or ref.get('object', {}).get('type') != 'tag':
        raise ValueError('Detached rolling recovery immutable provenance is missing')
    annotation = api.call('GET', 'git/tags/' + ref['object'].get('sha', '')) or {}
    try:
        manifest = json.loads(annotation.get('message', ''))
    except (TypeError, json.JSONDecodeError):
        raise ValueError('Detached rolling recovery immutable provenance is invalid') from None
    if (not isinstance(manifest, dict)
            or annotation.get('tag') != tag
            or annotation.get('object', {}).get('type') != 'commit'
            or annotation.get('object', {}).get('sha') != manifest.get('sourceSha')
            or annotation.get('message', '').rstrip('\n') != canonical(manifest).decode().rstrip('\n')
            or manifest.get('schemaVersion') != 1
            or manifest.get('applicationId') != 'io.github.constbogdan.mosaic'
            or manifest.get('versionCode') != number
            or manifest.get('versionName') != f'1.0.{number}'
            or manifest.get('immutableIdentity') != tag
            or manifest.get('rollingChannel') != 'develop'
            or manifest.get('assetName') != APK_NAME
            or not re.fullmatch('[0-9a-f]{40}', str(manifest.get('sourceSha', '')))):
        raise ValueError('Detached rolling recovery immutable provenance is invalid')
    immutable = find_release(api, tag, releases)
    if (immutable is None or immutable.get('draft') or immutable.get('prerelease') is not True
            or immutable.get('immutable') or immutable.get('name') != 'v' + manifest['versionName']):
        raise ValueError('Detached rolling recovery immutable release is inconsistent')
    identities = _release_assets(api, immutable, manifest)
    return manifest, immutable, identities


def _target_archive_preflight(api, releases, m, expected, compare_from):
    """Read-only validation of the target immutable identity and resumable draft."""
    tag = m['immutableIdentity']
    record_bytes = canonical(m)
    ref = api.call('GET', f'git/ref/tags/{tag}', missing=True)
    archive = find_release(api, tag, releases)
    if ref:
        if ref.get('object', {}).get('type') != 'tag':
            raise ValueError('Immutable identity is not an annotated provenance tag')
        annotation = api.call('GET', 'git/tags/' + ref['object'].get('sha', '')) or {}
        if (annotation.get('tag') != tag
                or annotation.get('object', {}).get('type') != 'commit'
                or annotation.get('object', {}).get('sha') != m['sourceSha']):
            raise ValueError('Immutable tag source mismatch')
        if annotation.get('message', '').rstrip('\n') != record_bytes.decode().rstrip('\n'):
            raise ValueError('Build identity already reserved for different bytes/provenance; never overwrite')
    elif archive is not None:
        raise ValueError('Release exists without its immutable provenance tag')

    if archive is not None:
        fields = release_fields(m, archive.get('draft') is True, archive=True,
                                compare_from=compare_from, repository=api.repository)
        if (archive.get('name') != fields['name']
                or not _canonical_historical_body(
                    m, archive.get('body'), archive=True, repository=api.repository
                )
                or archive.get('prerelease') is not True or archive.get('immutable')):
            raise ValueError('Immutable archive release metadata mismatch')
        accepted = {name: {_identity} for name, _identity in _expected_asset_identities(expected).items()}
        _inspect_asset_subset(
            api, archive, accepted, require_complete=not archive.get('draft'),
            message='Immutable archive release asset state is inconsistent',
        )
    return ref, archive


def _canonical_body(manifest, *, archive, compare_from, repository):
    return release_body(manifest, 'Development', archive=archive, compare_from=compare_from,
                        repository=repository)


def _canonical_historical_body(manifest, body, *, archive, repository):
    """Accept only a body the canonical renderer could have produced when published."""
    candidates = [None]
    comparison = re.findall(
        r'/compare/([0-9a-f]{40}|mosaic-v1\.0\.[1-9][0-9]*)\.\.\.'
        + re.escape(manifest['sourceSha']) + r'\)',
        str(body),
    )
    if len(comparison) == 1:
        candidates.append(comparison[0])
    return any(body == _canonical_body(
        manifest, archive=archive, compare_from=start, repository=repository
    ) for start in candidates)


def _authenticate_detached_r2(api, releases, candidates, develop_ref, compare_from):
    """Authenticate only the exact live R2 detached rolling incident."""
    profile = R2_DETACHED_RECOVERY
    if len(candidates) != 1:
        raise ValueError('Ambiguous detached rolling recovery state')
    candidate = candidates[0]
    expected_scalars = {
        'id': profile['releaseId'],
        'tag_name': profile['tagName'],
        'target_commitish': profile['targetCommitish'],
        'name': 'v' + profile['versionName'],
        'draft': False,
        'prerelease': True,
        'immutable': False,
    }
    if any(candidate.get(field) != value for field, value in expected_scalars.items()):
        raise ValueError('Detached rolling recovery state does not match the authenticated R2 incident')
    if (not develop_ref or develop_ref.get('object', {}).get('type') != 'commit'
            or develop_ref.get('object', {}).get('sha') != profile['sourceSha']):
        raise ValueError('Detached rolling recovery source does not match develop')
    detached_ref = api.call('GET', f"git/ref/tags/{profile['tagName']}", missing=True)
    if (not detached_ref or detached_ref.get('object', {}).get('type') != 'commit'
            or detached_ref.get('object', {}).get('sha') != profile['targetCommitish']):
        raise ValueError('Detached rolling recovery tag identity mismatch')

    try:
        manifest, _, immutable_assets = _immutable_version(api, releases, profile['versionCode'])
        rolling_assets = _release_assets(api, candidate, manifest)
    except ValueError:
        raise ValueError('Detached rolling recovery asset/provenance mismatch') from None
    expected_identities = {
        APK_NAME: (profile['apkSize'], profile['apkDigest']),
        MANIFEST_NAME: (profile['manifestSize'], profile['manifestDigest']),
    }
    if (manifest.get('sourceSha') != profile['sourceSha']
            or manifest.get('versionName') != profile['versionName']
            or immutable_assets != expected_identities or rolling_assets != expected_identities
            or candidate.get('body') != _canonical_body(
                manifest, archive=False, compare_from=compare_from, repository=api.repository
            )):
        raise ValueError('Detached rolling recovery asset/provenance mismatch')
    return manifest, immutable_assets


def _exact_r2_recovery_ref(recovery_ref):
    return (recovery_ref
            and recovery_ref.get('object', {}).get('type') == 'commit'
            and recovery_ref.get('object', {}).get('sha')
            == R2_DETACHED_RECOVERY['targetCommitish'])


def _rolling_preflight(api, releases, m, expected, compare_from):
    """Classify rolling state using reads only; unknown or ambiguous always refuses."""
    rolling = find_release(api, 'develop', releases)
    detached = [release for release in releases
                if re.fullmatch(r'untagged-[0-9a-f]+', str(release.get('tag_name', '')))]
    develop_ref = api.call('GET', 'git/ref/tags/develop', missing=True)
    recovery_ref = api.call('GET', f"git/ref/tags/{R2_DETACHED_RECOVERY['tagName']}", missing=True)
    target_identities = _expected_asset_identities(expected)

    if rolling and detached:
        raise ValueError('Ambiguous detached rolling recovery state')
    if rolling:
        if rolling.get('immutable') or rolling.get('prerelease') is not True:
            raise ValueError('Existing develop is immutable or not a prerelease; no settings changes attempted')
        match = re.fullmatch(r'v1\.0\.([1-9][0-9]*)', str(rolling.get('name', '')))
        if not match or int(match[1]) > m['versionCode']:
            raise ValueError('Unknown or newer rolling release; refusing rollback')
        number = int(match[1])
        manifest, immutable, historical_assets = _immutable_version(api, releases, number)
        if not _canonical_historical_body(
                manifest, rolling.get('body'), archive=False, repository=api.repository):
            raise ValueError('Rolling release metadata does not match immutable provenance')
        source = develop_ref.get('object', {}).get('sha') if develop_ref else None
        if (not develop_ref or develop_ref.get('object', {}).get('type') != 'commit'
                or source not in {manifest['sourceSha'], m['sourceSha']}):
            raise ValueError('Rolling release source relationship is invalid')

        recovery_in_progress = recovery_ref is not None
        if recovery_in_progress:
            if (not _exact_r2_recovery_ref(recovery_ref)
                    or rolling.get('id') != R2_DETACHED_RECOVERY['releaseId']
                    or number != R2_DETACHED_RECOVERY['versionCode']
                    or manifest.get('sourceSha') != R2_DETACHED_RECOVERY['sourceSha']
                    or rolling.get('target_commitish') != m['sourceSha']
                    or source != m['sourceSha']):
                raise ValueError('Detached rolling recovery state does not match the authenticated R2 incident')

        if not rolling.get('draft'):
            if number == m['versionCode']:
                if source != m['sourceSha']:
                    raise ValueError('Existing rolling tag/source mismatch')
                if recovery_ref and (not _exact_r2_recovery_ref(recovery_ref)
                                     or rolling.get('id') != R2_DETACHED_RECOVERY['releaseId']):
                    raise ValueError('Detached rolling recovery state does not match the authenticated R2 incident')
                return dict(state='completed', rolling=rolling, develop_ref=develop_ref,
                            cleanup_recovery_ref=recovery_ref is not None)
            if source != manifest['sourceSha']:
                raise ValueError('Rolling release source relationship is invalid')
            accepted = {name: {identity} for name, identity in historical_assets.items()}
            _inspect_asset_subset(
                api, rolling, accepted, require_complete=True,
                message='Unexpected rolling asset inventory; explicit recovery review required',
            )
        else:
            accepted = {
                name: {historical_assets[name], target_identities[name]}
                for name in target_identities
            }
            _inspect_asset_subset(
                api, rolling, accepted, require_complete=False,
                message='Unexpected rolling asset inventory; explicit recovery review required',
            )
        return dict(state='replace', rolling=rolling, develop_ref=develop_ref,
                    cleanup_recovery_ref=recovery_in_progress)

    if detached:
        if m['versionCode'] <= R2_DETACHED_RECOVERY['versionCode']:
            raise ValueError('Detached rolling recovery requires a newer protected-main build')
        _authenticate_detached_r2(api, releases, detached, develop_ref, compare_from)
        return dict(state='detached-r2', rolling=detached[0], develop_ref=develop_ref,
                    cleanup_recovery_ref=True)
    if develop_ref:
        raise ValueError('Orphan develop tag without an authenticated recovery candidate')
    if recovery_ref:
        raise ValueError('Detached recovery tag exists without its authenticated Release')
    return dict(state='initial', rolling=None, develop_ref=None, cleanup_recovery_ref=False)


def _verify_final_rolling(api, release_id, m, expected, compare_from, *, allow_recovery_ref):
    """Re-fetch and authenticate the complete rolling result, never the PATCH echo alone."""
    result = api.call('GET', f'releases/{release_id}') or {}
    by_tag = api.call('GET', 'releases/tags/develop', missing=True) or {}
    ref = api.call('GET', 'git/ref/tags/develop', missing=True)
    fields = release_fields(m, False, compare_from=compare_from, repository=api.repository)
    if (result.get('id') != release_id or by_tag.get('id') != release_id
            or result.get('tag_name') != 'develop' or by_tag.get('tag_name') != 'develop'
            or result.get('target_commitish') != m['sourceSha']
            or result.get('draft') or result.get('prerelease') is not True
            or result.get('immutable') or result.get('name') != fields['name']
            or result.get('body') != fields['body']):
        raise ValueError('Final rolling Release association or metadata mismatch')
    if (not ref or ref.get('object', {}).get('type') != 'commit'
            or ref.get('object', {}).get('sha') != m['sourceSha']):
        raise ValueError('Final rolling tag/source authentication failed')
    try:
        rolling_assets = _release_assets(api, result, m)
        releases = api.pages('releases')
        immutable = find_release(api, m['immutableIdentity'], releases)
        immutable_assets = _release_assets(api, immutable, m) if immutable else None
    except ValueError:
        raise ValueError('Final rolling asset/provenance mismatch') from None
    if rolling_assets != immutable_assets or rolling_assets != _expected_asset_identities(expected):
        raise ValueError('Final rolling asset/provenance mismatch')
    if any(re.fullmatch(r'untagged-[0-9a-f]+', str(item.get('tag_name', ''))) for item in releases):
        raise ValueError('Final rolling Release has a competing detached identity')
    recovery_ref = api.call('GET', f"git/ref/tags/{R2_DETACHED_RECOVERY['tagName']}", missing=True)
    if recovery_ref and not allow_recovery_ref:
        raise ValueError('Final rolling Release has a competing detached identity')
    return result


def published_development_source(api):
    """Authenticate the latest successfully exposed rolling Development source."""
    releases = api.pages('releases')
    rolling = find_release(api, 'develop', releases)
    if (rolling is None or rolling.get('tag_name') != 'develop' or rolling.get('draft')
            or rolling.get('prerelease') is not True or rolling.get('immutable')):
        raise ValueError('No trustworthy published rolling Development release')
    version = re.fullmatch(r'v1\.0\.([1-9][0-9]*)', str(rolling.get('name', '')))
    if not version:
        raise ValueError('Published rolling Development version is invalid')
    number = int(version[1])
    immutable_name = f'downstream-build-{number}'

    develop_ref = api.call('GET', 'git/ref/tags/develop', missing=True)
    immutable_ref = api.call('GET', f'git/ref/tags/{immutable_name}', missing=True)
    if (not develop_ref or develop_ref.get('object', {}).get('type') != 'commit'
            or not immutable_ref or immutable_ref.get('object', {}).get('type') != 'tag'):
        raise ValueError('Development release refs do not match the publication contract')
    source = develop_ref['object'].get('sha', '')
    if not re.fullmatch(r'[0-9a-f]{40}', source):
        raise ValueError('Development source SHA is invalid')

    annotation = api.call('GET', 'git/tags/' + immutable_ref['object']['sha']) or {}
    try:
        manifest = json.loads(annotation.get('message', ''))
    except (TypeError, json.JSONDecodeError):
        raise ValueError('Immutable Development provenance is not valid JSON') from None
    if (not isinstance(manifest, dict)
            or annotation.get('tag') != immutable_name
            or annotation.get('object', {}).get('type') != 'commit'
            or annotation.get('object', {}).get('sha') != source
            or manifest.get('schemaVersion') != 1
            or manifest.get('applicationId') != 'io.github.constbogdan.mosaic'
            or manifest.get('sourceSha') != source
            or manifest.get('versionCode') != number
            or manifest.get('versionName') != f'1.0.{number}'
            or manifest.get('immutableIdentity') != immutable_name
            or manifest.get('rollingChannel') != 'develop'
            or manifest.get('assetName') != APK_NAME):
        raise ValueError('Immutable Development provenance does not match rolling release identity')

    immutable = find_release(api, immutable_name, releases)
    if (immutable is None or immutable.get('tag_name') != immutable_name or immutable.get('draft')
            or immutable.get('prerelease') is not True or immutable.get('name') != rolling.get('name')):
        raise ValueError('Immutable Development release is missing or inconsistent')
    if _release_assets(api, rolling, manifest) != _release_assets(api, immutable, manifest):
        raise ValueError('Rolling and immutable Development assets differ')
    return source


def release_eligibility(api, root, sha):
    """Classify the complete unpublished range; uncertainty always requires release."""
    try:
        baseline = published_development_source(api)
        result = change_classification.classify_range(root, baseline, sha)
    except (KeyError, OSError, TypeError, UnicodeError, ValueError) as error:
        return {
            'outcome': 'ready',
            'releaseRequired': True,
            'releaseRelevance': change_classification.UNKNOWN,
            'validationRisk': change_classification.HIGH,
            'baselineSha': None,
            'currentSha': sha,
            'paths': [],
            'reason': f'conservative fallback: {error}',
        }
    return {
        **result,
        'outcome': 'ready' if result['releaseRequired'] else 'skipped_non_apk',
        'reason': 'classified complete range from published Development source',
    }


def record_eligibility(result, ci, env, artifact=None):
    repository = authenticate_workflow_repository(env, CI_WORKFLOW)
    output_path = Path(env['GITHUB_OUTPUT'])
    with output_path.open('a', encoding='utf-8') as output:
        values = {
            'release_required': str(result['releaseRequired']).lower(),
            'outcome': result['outcome'],
            'release_relevance': result['releaseRelevance'],
            'validation_risk': result['validationRisk'],
            'baseline_sha': result.get('baselineSha') or 'unresolved',
        }
        if artifact:
            values.update(
                artifact_id=artifact['artifactId'],
                artifact_name=artifact['artifactName'],
                ci_run_id=artifact['runId'],
                ci_run_attempt=artifact['runAttempt'],
                version_name=artifact['versionName'],
            )
        for key, value in values.items():
            output.write(f'{key}={value}\n')
    paths = [entry['path'] for entry in result['paths']]
    if result['releaseRequired']:
        heading = '## Development release required'
        explanation = ('This protected-main range can affect the application. An unsigned Release APK will be '
                       'built next, followed by signing authorization and publication.')
    else:
        heading = '## No build required'
        explanation = ('This change does not affect the application. Development build, signing, and publication '
                       'are skipped; protected-main validation remains authoritative.')
    details = [
        f"Outcome: `{result['outcome']}`",
        f"Release relevance: `{result['releaseRelevance']}`",
        f"Validation risk: `{result['validationRisk']}`",
        f"Published baseline: `{result.get('baselineSha') or 'unresolved'}`",
        f"Current trusted main: `{result['currentSha']}`",
        f"Changed paths: {len(paths)}",
        f"Reason: {result['reason']}",
        f"Authoritative CI: `{json.dumps(ci, sort_keys=True) if ci else 'current main push run'}`",
    ]
    if result.get('baselineSha'):
        details.append(
            f"Compare sources: https://github.com/{repository}/compare/"
            f"{result['baselineSha']}...{result['currentSha']}"
        )
    if artifact:
        details.extend([
            f"Unsigned artifact ID: `{artifact['artifactId']}`",
            f"Unsigned artifact: `{artifact['artifactName']}`",
        ])
    if paths:
        details.extend(['', 'Changed range:', *[f'- `{path}`' for path in paths]])
    summary = (f'{heading}\n\n{explanation}\n\n<details>\n'
               '<summary>Technical details</summary>\n\n' + '\n\n'.join(details[:8]))
    if len(details) > 8:
        summary += '\n\n' + '\n'.join(details[8:])
    summary += '\n\n</details>\n'
    with Path(env['GITHUB_STEP_SUMMARY']).open('a', encoding='utf-8') as output:
        output.write(summary)


def assets(api, release, expected, allow_upload):
    actual = api.pages(f"releases/{release['id']}/assets")
    if len({a['name'] for a in actual}) != len(actual) or any(a['name'] not in expected for a in actual):
        raise ValueError('Unexpected or duplicate immutable release assets')
    by_name = {a['name']: a for a in actual}
    for name, data in expected.items():
        if name not in by_name:
            if not allow_upload:
                raise ValueError('Published immutable release is missing an asset')
            asset = api.call('POST', f"releases/{release['id']}/assets?name={urllib.parse.quote(name)}", data, upload=True)
        else:
            asset = by_name[name]
        check_asset(asset, name, data)


def stable_compare_tag(api, releases):
    """Find the newest Stable tag whose immutable Git identity authenticates."""
    candidates = []
    for release in releases:
        if release.get('draft') or release.get('prerelease'):
            continue
        version = re.fullmatch(r'mosaic-v1\.0\.([1-9][0-9]*)', str(release.get('tag_name', '')))
        if version and release.get('name') == f'v1.0.{version[1]}':
            candidates.append((int(version[1]), release['tag_name']))
    for _, tag in sorted(candidates, reverse=True):
        try:
            ref = api.call('GET', f'git/ref/tags/{tag}', missing=True)
            if not ref or ref.get('object', {}).get('type') != 'tag':
                continue
            annotation = api.call('GET', 'git/tags/' + ref['object']['sha'])
            manifest = json.loads(annotation.get('message', ''))
            version = int(tag.rsplit('.', 1)[1])
            if (annotation.get('tag') != tag or annotation.get('object', {}).get('type') != 'commit'
                    or annotation.get('object', {}).get('sha') != manifest.get('sourceSha')
                    or annotation.get('message', '').rstrip('\n') != canonical(manifest).decode().rstrip('\n')
                    or manifest.get('versionCode') != version
                    or manifest.get('versionName') != f'1.0.{version}'):
                continue
            return tag
        except (AttributeError, KeyError, TypeError, ValueError, json.JSONDecodeError):
            continue
    return None


def publish(api, m, apk, compare_from=None):
    """Never replace immutable records/assets; hide rolling metadata during replacement."""
    tag = m['immutableIdentity']
    record_bytes = canonical(m)
    expected = {APK_NAME: apk, MANIFEST_NAME: record_bytes}
    releases = api.pages('releases')
    compare_from = stable_compare_tag(api, releases) if compare_from is None else compare_from
    # Refuse all predictable existing-state conflicts before creating an
    # immutable identity or changing the rolling Release/ref/assets.
    ref, archive = _target_archive_preflight(api, releases, m, expected, compare_from)
    plan = _rolling_preflight(api, releases, m, expected, compare_from)

    if plan['state'] == 'completed':
        _verify_final_rolling(
            api, plan['rolling']['id'], m, expected, compare_from,
            allow_recovery_ref=plan['cleanup_recovery_ref'],
        )
        if plan['cleanup_recovery_ref']:
            recovery_ref = api.call(
                'GET', f"git/ref/tags/{R2_DETACHED_RECOVERY['tagName']}", missing=True
            )
            if (not recovery_ref or recovery_ref.get('object', {}).get('type') != 'commit'
                    or recovery_ref.get('object', {}).get('sha') != R2_DETACHED_RECOVERY['targetCommitish']):
                raise ValueError('Detached rolling recovery tag identity mismatch')
            api.call('DELETE', f"git/refs/tags/{R2_DETACHED_RECOVERY['tagName']}")
            _verify_final_rolling(
                api, plan['rolling']['id'], m, expected, compare_from,
                allow_recovery_ref=False,
            )
        return compare_from

    if not ref:
        annotation = api.call('POST', 'git/tags', dict(tag=tag, message=record_bytes.decode(), object=m['sourceSha'], type='commit'))
        # Atomic ref creation; conflict fails. Never update/delete downstream-build-N.
        api.call('POST', 'git/refs', dict(ref='refs/tags/' + tag, sha=annotation['sha']))
    if archive is None:
        archive = api.call('POST', 'releases', dict(
            tag_name=tag, target_commitish=m['sourceSha'],
            **release_fields(m, True, archive=True, compare_from=compare_from,
                             repository=api.repository)))
    assets(api, archive, expected, allow_upload=archive['draft'])
    if archive['draft']:
        api.call('PATCH', f"releases/{archive['id']}", dict(
            tag_name=tag, target_commitish=m['sourceSha'],
            **release_fields(m, False, archive=True, compare_from=compare_from,
                             repository=api.repository)))

    rolling = plan['rolling']
    develop_ref = plan['develop_ref']
    if rolling:
        api.call('PATCH', f"releases/{rolling['id']}", dict(
            tag_name=rolling['tag_name'], target_commitish=rolling.get('target_commitish'),
            draft=True, prerelease=True, make_latest='false'))
    if develop_ref:
        # This is the ONLY mutable ref. Never push/force protected main or immutable tags.
        api.call('PATCH', 'git/refs/tags/develop', dict(sha=m['sourceSha'], force=True))
    else:
        api.call('POST', 'git/refs', dict(ref='refs/tags/develop', sha=m['sourceSha']))
    if rolling is None:
        rolling = api.call('POST', 'releases', dict(
            tag_name='develop', target_commitish=m['sourceSha'],
            **release_fields(m, True, compare_from=compare_from, repository=api.repository)))
    else:
        rolling = api.call('PATCH', f"releases/{rolling['id']}", dict(
            tag_name='develop', target_commitish=m['sourceSha'],
            draft=True, prerelease=True, make_latest='false'))
    for asset in api.pages(f"releases/{rolling['id']}/assets"):
        api.call('DELETE', f"releases/assets/{asset['id']}")
    assets(api, rolling, expected, allow_upload=True)
    api.call('PATCH', f"releases/{rolling['id']}", dict(
        tag_name='develop', target_commitish=m['sourceSha'],
        **release_fields(m, False, compare_from=compare_from, repository=api.repository)))
    _verify_final_rolling(
        api, rolling['id'], m, expected, compare_from,
        allow_recovery_ref=plan['cleanup_recovery_ref'],
    )
    if plan['cleanup_recovery_ref']:
        recovery_ref = api.call(
            'GET', f"git/ref/tags/{R2_DETACHED_RECOVERY['tagName']}", missing=True
        )
        if (not recovery_ref or recovery_ref.get('object', {}).get('type') != 'commit'
                or recovery_ref.get('object', {}).get('sha') != R2_DETACHED_RECOVERY['targetCommitish']):
            raise ValueError('Detached rolling recovery tag identity mismatch')
        api.call('DELETE', f"git/refs/tags/{R2_DETACHED_RECOVERY['tagName']}")
        _verify_final_rolling(
            api, rolling['id'], m, expected, compare_from,
            allow_recovery_ref=False,
        )
    return compare_from


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['ci-eligibility', 'ci-artifact', 'ci-manifest', 'ci-publish'])
    parser.add_argument('--directory', type=Path)
    args = parser.parse_args()
    try:
        env = os.environ
        repository = authenticate_workflow_repository(env, CI_WORKFLOW)
        if args.mode == 'ci-eligibility':
            sha = ci_guard(env)
            api = GitHub(repository)
            result = release_eligibility(api, Path(__file__).resolve().parent.parent, sha)
            record_eligibility(result, None, env)
            print(json.dumps(result, sort_keys=True))
            return
        if args.mode == 'ci-artifact':
            verify_same_run_artifact_directory(args.directory, env)
            return
        if args.mode in ('ci-manifest', 'ci-publish'):
            sha = ci_guard(env)
            root = Path(__file__).resolve().parent.parent
            identity = allocate(root, publication=True)
            ci = same_run_ci_identity(env)
            directory = args.directory
            apk = (directory / 'Mosaic-release.apk').read_bytes()
            record = json.loads((directory / 'verification.json').read_text(encoding='utf-8'))
            policy = json.loads((root / 'scripts/mosaic-signing.json').read_text(encoding='utf-8'))
            m = verified_manifest(record, apk, identity, ci['runId'], ci['runAttempt'], policy, CI_WORKFLOW)
            path = directory / MANIFEST_NAME
            if args.mode == 'ci-manifest':
                path.write_bytes(canonical(m))
                return
            api = GitHub(repository)
            require_current_protected_main(api, sha)
            if path.read_bytes() != canonical(m):
                raise ValueError('Prepared publication manifest changed')
            compare_from = publish(api, m, apk)
            append_summary(publication_summary(
                m, 'publish', env, ci, compare_from=compare_from, repository=repository), env)
            return
    except (ValueError, OSError, KeyError) as error:
        parser.exit(1, str(error) + '\n')


if __name__ == '__main__':
    main()
