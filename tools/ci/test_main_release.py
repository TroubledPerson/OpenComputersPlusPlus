import copy, hashlib, io, json, unittest, zipfile
from verify_main_release import NOTICES, verify
from publish_main_release import checked_release

class ReleaseGateTest(unittest.TestCase):
    def payloads(self):
        p = {'META-INF/MANIFEST.MF':b'FMLCorePlugin: li.cil.oc.common.launch.TransformerLoader\n',
             'mcmod.info':b'[{"modid":"opencomputers","version":"test"}]',
             'li/cil/oc/OpenComputers.class':b'\xca\xfe\xba\xbe\x00\x00\x00\x34test',
             'assets/opencomputers/sounds.json':json.dumps({'ocpp_cd_'+n:{} for n in ('open','close','seek','motor','spinup','stop')}).encode()}
        assets = []
        for name in ('cd_open.ogg','cd_close.ogg'):
            p['assets/opencomputers/sounds/ocpp/'+name] = b'fixture'
            assets.append({'file':name,'sha256':hashlib.sha256(b'fixture').hexdigest()})
        p['assets/opencomputers/sounds/ocpp/cd-effects-provenance.json'] = json.dumps({'assets':assets}).encode()
        p.update({'META-INF/ocpp-audio/'+n:b'notice fixture' for n in NOTICES})
        return p
    def jar(self,p):
        b=io.BytesIO()
        with zipfile.ZipFile(b,'w') as z:
            for n,v in p.items():z.writestr(n,v)
        return b.getvalue()
    def test_valid_structural_fixture(self): self.assertEqual(len(verify(self.jar(self.payloads()),'test')),64)
    def test_retired_ogg_rejected(self):
        p=self.payloads();p['legacy.txt']=b'cd_read.ogg'
        with self.assertRaises(ValueError):verify(self.jar(p),'test')
    def test_retired_registration_rejected(self):
        p=self.payloads();p['legacy.txt']=b'ocpp_cd_read'
        with self.assertRaises(ValueError):verify(self.jar(p),'test')
    def test_obsolete_provenance_rejected(self):
        p=self.payloads();p['legacy.txt']=b'cd-rom-reading-cd-70282'
        with self.assertRaises(ValueError):verify(self.jar(p),'test')
    def test_missing_notice_rejected(self):
        p=self.payloads();del p['META-INF/ocpp-audio/'+NOTICES[0]]
        with self.assertRaises(KeyError):verify(self.jar(p),'test')
    def test_changed_source_audio_rejected(self):
        with self.assertRaises(ValueError):verify(self.jar(self.payloads()),'test',{'assets/opencomputers/sounds/ocpp/cd_open.ogg':b'changed'})
    def test_wrong_version_rejected(self):
        with self.assertRaises(ValueError):verify(self.jar(self.payloads()),'wrong')
    def test_compatible_scala_and_newer_bytecode(self):
        p=self.payloads();p['li/cil/oc/OpenComputers.class']=b'\xca\xfe\xba\xbe\x00\x00\x00\x32test'
        self.assertEqual(len(verify(self.jar(p),'test')),64)
        p['li/cil/oc/OpenComputers.class']=b'\xca\xfe\xba\xbe\x00\x00\x00\x35test'
        with self.assertRaises(ValueError):verify(self.jar(p),'test')
    def test_wrong_java_rejected(self):
        p=self.payloads();p['li/cil/oc/OpenComputers.class']=b'not Java 8'
        with self.assertRaises(ValueError):verify(self.jar(p),'test')
    def test_native_qemu_rejected(self):
        p=self.payloads();p['qemu-system-x86_64.exe']=b'fixture'
        with self.assertRaises(ValueError):verify(self.jar(p),'test')
    def release(self): return {'target_commitish':'a'*40,'draft':False,'prerelease':True,'assets':[{'name':'main.jar','state':'uploaded','size':3,'digest':'sha256:abc'}]}
    def test_verified_published_release(self):checked_release(self.release(),'a'*40,{'main.jar':(3,'abc')},False)
    def test_bad_release_states_and_digests(self):
        for key,value in [('prerelease',False),('draft',True),('target_commitish','b'*40)]:
            p=self.release();p[key]=value
            with self.assertRaises(ValueError):checked_release(p,'a'*40,{'main.jar':(3,'abc')},False)
        p=self.release();p['assets'][0]['digest']='sha256:bad'
        with self.assertRaises(ValueError):checked_release(p,'a'*40,{'main.jar':(3,'abc')},False)
    def test_unexpected_asset_rejected(self):
        p=self.release();p['assets'].append(copy.deepcopy(p['assets'][0]))
        with self.assertRaises(ValueError):checked_release(p,'a'*40,{'main.jar':(3,'abc')},False)

if __name__ == '__main__':unittest.main()
