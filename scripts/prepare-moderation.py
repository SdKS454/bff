#!/usr/bin/env python3
"""Extract the reviewed colleagues' ZIP and apply our integration patch to a fresh directory."""
import argparse
import hashlib
import os
from pathlib import Path, PurePosixPath
import subprocess
import zipfile

ARCHIVE_SHA256 = '399db1e0a64550901e36bd17cacb37512c783ce41eec3a7f9b621045ceab44b8'
PREFIX = 'moderation-main/moderation-main/Moderation/'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('output', type=Path, help='New directory; existing files will never be overwritten')
    args = parser.parse_args()
    if hashlib.sha256(args.archive.read_bytes()).hexdigest() != ARCHIVE_SHA256:
        raise SystemExit('Archive differs from the reviewed moderation-main.zip; review it before applying this patch.')
    output = args.output.resolve()
    if output.exists():
        raise SystemExit('Output directory already exists; choose a new directory.')
    output.mkdir(parents=True)
    with zipfile.ZipFile(args.archive) as archive:
        for entry in archive.infolist():
            if not entry.filename.startswith(PREFIX) or entry.is_dir():
                continue
            relative = PurePosixPath(entry.filename[len(PREFIX):])
            if relative.is_absolute() or '..' in relative.parts:
                raise SystemExit('Unsafe archive entry')
            target = output.joinpath(*relative.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(entry))
    patch = Path(__file__).resolve().parents[1] / 'integrations/moderation/moderation-sync.patch'
    env = os.environ.copy()
    env.pop('GIT_DIR', None)
    env.pop('GIT_WORK_TREE', None)
    env['GIT_CEILING_DIRECTORIES'] = str(output.parent)
    subprocess.run(['git', 'apply', '--check', str(patch)], cwd=output, env=env, check=True)
    subprocess.run(['git', 'apply', str(patch)], cwd=output, env=env, check=True)
    print('Patched Moderation source: ' + str(output))
    print('Build with Java 17+ and Maven: mvn -f ' + str(output / 'pom.xml') + ' clean verify')


if __name__ == '__main__':
    main()
