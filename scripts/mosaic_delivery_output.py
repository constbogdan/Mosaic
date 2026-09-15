"""Human delivery presentation; never authorizes, builds, signs or publishes."""
import json
import os
from pathlib import Path
import re


REPOSITORY_URL = 'https://github.com/constbogdan/Wholphin'
COMPARE_REF = re.compile(r'(?:[0-9a-f]{40}|mosaic-v1\.0\.[1-9][0-9]*)')


def append_summary(text, env=None):
    env = os.environ if env is None else env
    if env.get('GITHUB_STEP_SUMMARY'):
        with Path(env['GITHUB_STEP_SUMMARY']).open('a', encoding='utf-8') as output:
            output.write(text.rstrip() + '\n')


def compare_link(start, end):
    """Return an immutable GitHub comparison only for known source/tag identities."""
    if (not isinstance(start, str) or not isinstance(end, str)
            or not COMPARE_REF.fullmatch(start) or not COMPARE_REF.fullmatch(end) or start == end):
        return None
    return f'{REPOSITORY_URL}/compare/{start}...{end}'


def release_body(m, channel, *, archive=False, compare_from=None):
    version, build, source = m['versionName'], m['immutableIdentity'], m['sourceSha']
    tag = 'mosaic-v' + version if channel == 'Stable' else build if archive else 'develop'
    comparison = compare_link(compare_from, tag if channel == 'Stable' else source)
    if archive:
        title = f'Immutable Development build v{version}'
        purpose = (f'Permanent build and provenance record for `{build}`. This is not a separate '
                   'update channel.')
        guidance = f'[Open current Development]({REPOSITORY_URL}/releases/tag/develop) for normal preview updates.'
    elif channel == 'Stable':
        title = f'Stable v{version}'
        purpose = 'Explicitly promoted trusted build for normal consumption.'
        guidance = 'Choose Stable in the app update channel for normal updates.'
    else:
        title = f'Development v{version}'
        purpose = 'Latest automatically published validated build.'
        guidance = ('Development preview; may contain recently merged changes. Choose Development in the app '
                    'update channel.')
    links = [f'[Download APK]({REPOSITORY_URL}/releases/download/{tag}/Wholphin-release.apk)']
    if comparison:
        links.append(f'[Compare changes]({comparison})')
    details = [
        f'Version: `v{version}`',
        f'Immutable build: [{build}]({REPOSITORY_URL}/releases/tag/{build})',
        f'Source: [{source}]({REPOSITORY_URL}/commit/{source})',
        f"APK SHA-256: `{m['signedApkSha256']}`",
        f'Public provenance: [mosaic-release.json]({REPOSITORY_URL}/releases/download/{tag}/mosaic-release.json)',
    ]
    return (f'# {title}\n\n{purpose}\n\n' + ' · '.join(links) + f'\n\n{guidance}\n\n'
            '<details>\n<summary>Technical provenance</summary>\n\n' + '\n\n'.join(details)
            + '\n\n</details>\n')


def publication_summary(m, operation, env, ci=None, compare_from=None):
    version, build, source = m['versionName'], m['immutableIdentity'], m['sourceSha']
    if operation == 'promote':
        heading = f'Stable v{version} released'
        tag = 'mosaic-v' + version
        result = ('Explicitly promoted trusted build for normal consumption.\n\n'
                  f'[Open Stable release]({REPOSITORY_URL}/releases/tag/{tag})')
        details = ['Channel: Stable', 'Exact Development bytes reused']
    elif operation == 'publish':
        heading = f'Development v{version} published'
        tag = source
        result = ('Latest automatically published validated build.\n\n'
                  f'[Open Development release]({REPOSITORY_URL}/releases/tag/develop)')
        details = ['Channel: Development', f'Build: {build}']
    else:
        raise ValueError('Unknown presentation operation')
    comparison = compare_link(compare_from, tag)
    if comparison:
        result += f' · [Compare changes]({comparison})'
    details += [f'Source: [{source}]({REPOSITORY_URL}/commit/{source})',
                f"APK SHA-256: {m['signedApkSha256']}",
                f'Immutable build record: [{build}]({REPOSITORY_URL}/releases/tag/{build})',
                f"Original build run: {m['buildRunId']} / attempt {m['buildRunAttempt']}"]
    if ci:
        details.append('Authoritative CI: ' + json.dumps(ci, sort_keys=True))
    return ('## ' + heading + '\n\n' + result + '\n\n<details>\n'
            '<summary>Technical details</summary>\n\n' + '\n\n'.join(details) + '\n\n</details>\n')
