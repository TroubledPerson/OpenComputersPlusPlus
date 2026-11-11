"""Structural main-mod release gate. Does not execute the JAR or qualify a VM addon."""
import hashlib, io, json, re, sys, zipfile
from pathlib import Path

RETIRED = (b'ocpp_cd_read', b'cd_read.ogg', b'cd-rom-reading-cd-70282')
NOTICES = ('CD-718390-NOTICES.txt', 'CD-B-BRIEF-TICK-NOTICES.txt',
           'CD-EFFECT-NOTICES.txt', 'CD-LONGER-STARTUP-NOTICES.txt',
           'CD-MOTOR-NOTICES.txt', 'CD-SEQUENCE-NOTICES.txt',
           'CD-STARTUP-FAMILY-LOOP-NOTICES.txt', 'CODEC-NOTICES.txt')

def verify(data, version, expected_resources=None):
    if len(data) > 128 * 1024**2: raise ValueError('JAR size cap')
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        infos = jar.infolist(); names = [i.filename for i in infos]
        if len(names) > 20000 or len(set(names)) != len(names): raise ValueError('Entry bounds/duplicates')
        if sum(i.file_size for i in infos) > 512 * 1024**2: raise ValueError('Expanded size cap')
        for i in infos:
            if i.file_size > 32 * 1024**2 or i.filename.startswith('/') or '..' in i.filename.split('/'):
                raise ValueError('Unsafe entry')
            if any(t in jar.read(i) for t in RETIRED): raise ValueError('Retired audio reference')
            if 'qemu-system' in i.filename.lower() or 'OCVMIntegration' in i.filename:
                raise ValueError('Unexpected addon/runtime')
        if jar.testzip() is not None: raise ValueError('CRC')
        manifest = jar.read('META-INF/MANIFEST.MF')
        if b'FMLCorePlugin: li.cil.oc.common.launch.TransformerLoader' not in manifest:
            raise ValueError('Core plugin metadata')
        metadata = json.loads(jar.read('mcmod.info'))
        if not any(m.get('modid') == 'opencomputers' and m.get('version') == version for m in metadata):
            raise ValueError('Mod identity/version')
        main = jar.read('li/cil/oc/OpenComputers.class')
        if main[:4] != b'\xca\xfe\xba\xbe' or not 45 <= int.from_bytes(main[6:8], 'big') <= 52 or version.encode() not in main:
            raise ValueError('Java 8/main version')
        events = json.loads(jar.read('assets/opencomputers/sounds.json'))
        if 'ocpp_cd_read' in events or not all('ocpp_cd_' + s in events for s in ('open','close','seek','motor','spinup','stop')):
            raise ValueError('CD registrations')
        for n in NOTICES: jar.read('META-INF/ocpp-audio/' + n)
        provenance = json.loads(jar.read('assets/opencomputers/sounds/ocpp/cd-effects-provenance.json'))
        if {a['file'] for a in provenance['assets']} != {'cd_open.ogg','cd_close.ogg'}:
            raise ValueError('Tray provenance')
        for a in provenance['assets']:
            audio = jar.read('assets/opencomputers/sounds/ocpp/' + a['file'])
            if hashlib.sha256(audio).hexdigest() != a['sha256']: raise ValueError('Tray hash')
        for name, content in (expected_resources or {}).items():
            if jar.read(name) != content: raise ValueError('Source resource differs: ' + name)
    return hashlib.sha256(data).hexdigest()

def prepare(root, source, output):
    if not re.fullmatch('[0-9a-f]{40}', source): raise ValueError('Source SHA')
    version = re.search(r'^mod_version=(.+)$', (root/'gradle.properties').read_text(), re.M).group(1).strip()
    if not re.fullmatch(r'[A-Za-z0-9._-]{1,80}', version): raise ValueError('Version characters')
    candidates = [p for p in (root/'build/libs').glob('OpenComputersPlusPlus-*.jar')
                  if not p.name.endswith(('-api.jar','-javadoc.jar','-sources.jar'))]
    if len(candidates) != 1: raise ValueError('Expected exactly one main JAR')
    resources = root/'src/main/resources'
    expected = {p.relative_to(resources).as_posix():p.read_bytes() for p in resources.rglob('*')
                if p.is_file() and (p.suffix == '.ogg' or p.name in NOTICES)}
    expected.update({p.name:p.read_bytes() for p in root.glob('LICENSE*') if p.is_file()})
    data = candidates[0].read_bytes(); digest = verify(data, version, expected)
    output.mkdir(exist_ok=False)
    name = 'OpenComputersPlusPlus-MC1.12.2-' + version + '-' + source[:7] + '.jar'
    (output/name).write_bytes(data)
    (output/'SHA256SUMS.txt').write_text(digest+'  '+name+'\n', encoding='utf-8')
    meta = {'source':source,'version':version,'filename':name,'bytes':len(data),'sha256':digest}
    (output/'build.json').write_text(json.dumps(meta)+'\n', encoding='utf-8')
    notes = ('Automated experimental AI-assisted/vibe-coded main OC++ prerelease.\n\n'
             'Minecraft 1.12.2 / Forge 14.23.5.2860 / Java 8. Source: `'+source+'`.\n\n'
             'Built with main-only jar/reobfJar; structural CRC, metadata/version, Java 8, '
             'source-audio/notice identity and retired-audio exclusion checks passed. '
             'Aggregate historical-fixture/companion tests and Minecraft/audio runtime tests were not run. '
             'No VM addon or QEMU is included. Component licences and notices remain embedded; '
             'the current source tag supplies the codec sources and licensing material.\n\n'
             'Back up your world and use only one main OC++ JAR. See SHA256SUMS.txt.\n')
    (output/'RELEASE-NOTES.md').write_text(notes, encoding='utf-8')

if __name__ == '__main__': prepare(Path.cwd(), sys.argv[1], Path(sys.argv[2]))
