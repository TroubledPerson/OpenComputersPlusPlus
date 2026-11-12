import copy, hashlib, io, json, unittest, zipfile
from pathlib import Path
from unittest import mock
import subprocess
import publish_main_release as publisher
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


    def test_standard_release_identity_unchanged(self):
        self.assertEqual(publisher.release_identity('1.8.10-ocpp-dev.33','a'*40,'123','standard'),
                         ('ocpp-ci-123','OC++ 1.8.10-ocpp-dev.33 aaaaaaa'))
    def test_fixed_lab01_identity(self):
        self.assertEqual(publisher.release_identity('1.8.10-ocpp-dev.33','a'*40,'123','lab01'),
                         ('ocpp-dev33-lab01','OC++ dev33 lab01'))
    def test_lab01_rejects_other_versions(self):
        for version in ('1.8.10-ocpp-dev.32','1.8.10-ocpp-dev.34'):
            with self.assertRaises(ValueError):
                publisher.release_identity(version,'a'*40,'123','lab01')
    def test_arbitrary_release_mode_rejected(self):
        for mode in ('lab02','OC++ dev33 lab01',''):
            with self.assertRaises(ValueError):
                publisher.release_identity('1.8.10-ocpp-dev.33','a'*40,'123',mode)
    def test_bad_release_source_or_run_rejected(self):
        for source,run in [('short','123'),('a'*40,'../123'),('A'*40,'123')]:
            with self.assertRaises(ValueError):
                publisher.release_identity('1.8.10-ocpp-dev.33',source,run,'lab01')
    def test_lab01_server_identity_verified(self):
        r=self.release();r.update(tag_name='ocpp-dev33-lab01',name='OC++ dev33 lab01')
        publisher.checked_release(r,'a'*40,{'main.jar':(3,'abc')},False,
                                  'ocpp-dev33-lab01','OC++ dev33 lab01')
        for key in ('tag_name','name'):
            bad=copy.deepcopy(r);bad[key]='wrong'
            with self.assertRaises(ValueError):
                publisher.checked_release(bad,'a'*40,{'main.jar':(3,'abc')},False,
                                          'ocpp-dev33-lab01','OC++ dev33 lab01')
    def test_absent_tag_and_release_are_both_required(self):
        missing=subprocess.CompletedProcess([],1,'','not found (HTTP 404)')
        with mock.patch.object(publisher.subprocess,'run',side_effect=[missing,missing]) as run:
            publisher.ensure_unused('TroubledPerson/OpenComputersPlusPlus','ocpp-dev33-lab01')
        self.assertEqual([c.args[0][-1] for c in run.call_args_list],
                         ['repos/TroubledPerson/OpenComputersPlusPlus/git/ref/tags/ocpp-dev33-lab01',
                          'repos/TroubledPerson/OpenComputersPlusPlus/releases/tags/ocpp-dev33-lab01'])
    def test_existing_tag_stops_before_release_lookup(self):
        exists=subprocess.CompletedProcess([],0,'{}','')
        with mock.patch.object(publisher.subprocess,'run',return_value=exists) as run:
            with self.assertRaises(ValueError):
                publisher.ensure_unused('TroubledPerson/OpenComputersPlusPlus','ocpp-dev33-lab01')
        self.assertEqual(run.call_count,1)
    def test_existing_release_stops_without_replacement(self):
        missing=subprocess.CompletedProcess([],1,'','not found (HTTP 404)')
        exists=subprocess.CompletedProcess([],0,'{}','')
        with mock.patch.object(publisher.subprocess,'run',side_effect=[missing,exists]) as run:
            with self.assertRaises(ValueError):
                publisher.ensure_unused('TroubledPerson/OpenComputersPlusPlus','ocpp-dev33-lab01')
        self.assertEqual(run.call_count,2)
    def test_uncertain_api_failure_stops(self):
        for error in ('Forbidden (HTTP 403)','timeout',''):
            failed=subprocess.CompletedProcess([],1,'',error)
            with mock.patch.object(publisher.subprocess,'run',return_value=failed) as run:
                with self.assertRaises(RuntimeError):
                    publisher.ensure_unused('TroubledPerson/OpenComputersPlusPlus','ocpp-dev33-lab01')
            self.assertEqual(run.call_count,1)
    def test_main_workflow_lab_mode_is_opt_in(self):
        root=Path(__file__).resolve().parents[2]
        w=json.loads((root/'.github/workflows/main-prerelease.yml').read_text())
        i=w['on']['workflow_dispatch']['inputs']['release_mode']
        self.assertEqual(i['default'],'standard')
        self.assertEqual(i['options'],['standard','lab01'])
        self.assertEqual(w['permissions'],{'contents':'read'})
        self.assertEqual(w['jobs']['release']['permissions'],{'contents':'write'})
        self.assertIn('standard',w['env']['OCPP_RELEASE_MODE'])
        self.assertNotIn('lab01',w['env']['OCPP_RELEASE_MODE'])
    def test_hosted_gate_workflow_cannot_publish(self):
        root=Path(__file__).resolve().parents[2]
        w=json.loads((root/'.github/workflows/main-release-gates.yml').read_text())
        self.assertEqual(w['permissions'],{'contents':'read'})
        self.assertEqual(set(w['jobs']),{'gates'})
        j=w['jobs']['gates']
        self.assertEqual(j['runs-on'],'ubuntu-24.04')
        self.assertLessEqual(j['timeout-minutes'],5)
        self.assertFalse(j['steps'][0]['with']['persist-credentials'])
        self.assertEqual(j['steps'][1]['run'],'python3 -B tools/ci/test_main_release.py')
        self.assertEqual(len(j['steps']),2)

if __name__ == '__main__':unittest.main()
