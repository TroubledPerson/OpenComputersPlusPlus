"""Publish only the verified main bundle. Uses the runner's ephemeral job token via gh."""
import hashlib, json, os, re, subprocess, sys
from pathlib import Path

def checked_bundle(folder, source):
    meta = json.loads((folder/'build.json').read_text())
    if meta['source'] != source or not re.fullmatch('[0-9a-f]{40}', source): raise ValueError('Source identity')
    name = meta['filename']
    if not re.fullmatch(r'OpenComputersPlusPlus-MC1\.12\.2-[A-Za-z0-9._-]+\.jar', name): raise ValueError('Artifact name')
    if {p.name for p in folder.iterdir()} != {name,'SHA256SUMS.txt','build.json','RELEASE-NOTES.md'}:
        raise ValueError('Unexpected bundle files')
    data = (folder/name).read_bytes()
    if len(data) != meta['bytes'] or hashlib.sha256(data).hexdigest() != meta['sha256']:
        raise ValueError('Artifact identity')
    checksum = (folder/'SHA256SUMS.txt').read_bytes()
    if checksum != (meta['sha256']+'  '+name+'\n').encode(): raise ValueError('Checksum text')
    return meta, {name:(len(data),meta['sha256']), 'SHA256SUMS.txt':(len(checksum),hashlib.sha256(checksum).hexdigest())}

def checked_release(release, source, expected, draft):
    if release['target_commitish'] != source or release['draft'] != draft or release['prerelease'] is not True:
        raise ValueError('Release target/state')
    assets = release['assets']
    if len(assets) != len(expected) or {a['name'] for a in assets} != set(expected): raise ValueError('Asset set')
    for a in assets:
        size, digest = expected[a['name']]
        if a['state'] != 'uploaded' or a['size'] != size or a.get('digest') != 'sha256:'+digest:
            raise ValueError('Server asset identity')

def publish(folder):
    repo = os.environ['GITHUB_REPOSITORY']; source = os.environ['GITHUB_SHA']
    run = os.environ['GITHUB_RUN_ID']
    if repo != 'TroubledPerson/OpenComputersPlusPlus' or not run.isdecimal(): raise ValueError('Repository/run')
    meta, expected = checked_bundle(folder, source); tag = 'ocpp-ci-'+run
    def gh(*args): return subprocess.check_output(['gh',*args], text=True)
    # Any existing tag or release, or uncertain API failure, stops without replacement.
    for endpoint in ('git/ref/tags/'+tag, 'releases/tags/'+tag):
        p = subprocess.run(['gh','api','repos/'+repo+'/'+endpoint], capture_output=True, text=True)
        if p.returncode == 0: raise ValueError('Existing tag/release: inspect before retry')
        if '(HTTP 404)' not in p.stderr: raise RuntimeError('GitHub lookup failed; outcome not established')
    gh('release','create',tag,'--repo',repo,'--target',source,'--title','OC++ '+meta['version']+' '+source[:7],
       '--draft','--prerelease','--latest=false','--notes-file',str(folder/'RELEASE-NOTES.md'),
       str(folder/meta['filename']),str(folder/'SHA256SUMS.txt'))
    endpoint = 'repos/'+repo+'/releases/tags/'+tag
    checked_release(json.loads(gh('api',endpoint)),source,expected,True)
    gh('release','edit',tag,'--repo',repo,'--draft=false','--prerelease','--latest=false')
    release = json.loads(gh('api',endpoint)); checked_release(release,source,expected,False)
    ref = json.loads(gh('api','repos/'+repo+'/git/ref/tags/'+tag))
    if ref['object']['type'] != 'commit' or ref['object']['sha'] != source: raise ValueError('Tag target')
    with open(os.environ['GITHUB_STEP_SUMMARY'],'a',encoding='utf-8') as out:
        out.write('Verified prerelease: '+release['html_url']+'\n')
    print(release['html_url'])

if __name__ == '__main__': publish(Path(sys.argv[1]))
