"""GitHub-hosted qualification only. No private URL in argv or diagnostics."""
import hashlib, io, json, os, pathlib, shutil, signal, subprocess, sys, time, urllib.parse, urllib.request, zipfile
BASE=pathlib.Path(__file__).resolve().parent
MANIFEST_SHA="aca752e557ab92f534ecf1c9883132a9164f173e77bee6540a310f4af05ebb25"
RESOLVER_SHA="d9580249e8f6d9cf4ffd351d48da5615f181d013866b80a80801659ecf278109"
ZIP_SHA="673d9776f303bc7048fc3329d232d6ebf1051b07893bd9d11616fad9a8673be0"
BUNDLE_SHA="4ec5d22eb4b6324a239dfcf08262d5df7b703da7377f42b4fe0f9cd0e36d6c4a"
JAR_SHA="cd3fbf85d7ca744507fd6a37a41b90122d43e616a4f8962332b1b658655e8a64"
REL="net/minecraftforge/forge/1.12.2-14.23.5.2860/forge-1.12.2-14.23.5.2860-universal.jar"
NAMES={"forge-1.12.2-14.23.5.2860-universal.jar","SOURCE-MANIFEST.json","BUILD-INPUTS.json","README.txt",
"corresponding-source/MCPConfig-LICENSE.txt","corresponding-source/mcp_config-1.12.2-20200226.224830.zip",
"corresponding-source/MinecraftForge-d3f01843f7e7a4f613b5e8113d381fd8747b4343-source.zip"}
def require(ok):
    if not ok: raise ValueError("Qualification rejected")
def sha(data): return hashlib.sha256(data).hexdigest()
def file_sha(p):
    h=hashlib.sha256()
    with p.open("rb") as f:
        for b in iter(lambda:f.read(1048576),b""): h.update(b)
    return h.hexdigest()
def root():
    ids=[os.environ[k] for k in ("GITHUB_RUN_ID","GITHUB_RUN_ATTEMPT")]
    require(all(s.isdigit() for s in ids))
    base=pathlib.Path(os.environ["RUNNER_TEMP"]).resolve()
    p=base/("fgqual-"+"-".join(ids))
    require(p.parent==base and not p.is_symlink())
    return p
def ownership():
    return (os.environ["GITHUB_RUN_ID"]+":"+os.environ["GITHUB_RUN_ATTEMPT"]+":"+os.environ["GITHUB_SHA"]).encode()
def admission():
    m=dict(s.split(":",1) for s in pathlib.Path("/proc/meminfo").read_text().splitlines())
    require(int(m["MemAvailable"].split()[0])*1024>=1610612736)
def frozen():
    b=(BASE/"fg-5.1.64-resolved.json").read_bytes()
    require(sha(b)==MANIFEST_SHA)
    m=json.loads(b)
    require(len(m["artifacts"])==31 and m["gradle"]=="7.6.6")
    return m
def checked_cp(p):
    m=frozen()
    expected={(a["coordinate"],a["file"]):(a["bytes"],a["sha256"]) for a in m["artifacts"]}
    actual=json.loads((p/"tooling/classpath.json").read_text())
    require(len(actual)==31)
    got={}
    for a in actual:
        f=pathlib.Path(a["path"])
        require(f.resolve().is_relative_to((p/"public-home").resolve()))
        require(f.name==a["file"])
        got[(a["coordinate"],a["file"])]=(f.stat().st_size,file_sha(f))
    require(got==expected)
    return [a["path"] for a in sorted(actual,key=lambda a:(a["coordinate"],a["file"]))]
def gradle(p,project,home,task,extra=(),env=None):
    admission()
    log=p/(task+"-"+project.name+".log")
    args=[str(p/"gradle-7.6.6/bin/gradle"),"-p",str(project),"-g",str(home),
          "--no-daemon","--max-workers=1","--console=plain","-Dorg.gradle.jvmargs=-Xmx256m",*extra,task]
    child=dict(os.environ if env is None else env,JAVA_OPTS="-Xmx64m")
    with log.open("xb") as out:
        process=subprocess.Popen(args,env=child,stdout=out,stderr=subprocess.STDOUT,start_new_session=True)
        try:
            code=process.wait(timeout=240)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid,signal.SIGTERM)
            try: process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid,signal.SIGKILL);process.wait(timeout=10)
            raise
        if code: raise subprocess.CalledProcessError(code,args)
def prepare(p):
    admission();p.mkdir(mode=0o700)
    with (p/'OWNER').open('xb') as marker: marker.write(ownership())
    m=frozen()
    wf=BASE.parents[1]/".github/workflows/forge-tooling-resolution.yml"
    b=wf.read_bytes();require(sha(b)==RESOLVER_SHA)
    script=next(s["run"] for s in json.loads(b)["jobs"]["resolve"]["steps"] if s.get("name")=="Resolve and report public tooling")
    source=script.split("<<'GROOVY'\n",1)[1].split("\nGROOVY\n",1)[0]
    # Public distribution only. HTTPS redirects permitted; private fetch below forbids them.
    class HTTPSRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self,req,fp,code,msg,headers,newurl):
            require(urllib.parse.urlsplit(newurl).scheme=="https")
            return super().redirect_request(req,fp,code,msg,headers,newurl)
    opener=urllib.request.build_opener(HTTPSRedirect())
    archive=p/"gradle.zip";h=hashlib.sha256();n=0;deadline=time.monotonic()+180
    with opener.open("https://services.gradle.org/distributions/gradle-7.6.6-bin.zip",timeout=20) as response,archive.open("xb") as out:
        while True:
            require(time.monotonic()<deadline)
            b=response.read(1048576)
            if not b: break
            n+=len(b);require(n<=128439774);h.update(b);out.write(b)
    require(n==128439774 and h.hexdigest()==ZIP_SHA)
    subprocess.run(["unzip","-q",str(archive),"-d",str(p)],check=True,timeout=60,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    project=p/"tooling";project.mkdir()
    (project/"settings.gradle").write_text("rootProject.name='frozen-tooling'\n")
    source+="\ntasks.register('writeQualificationClasspath') {\n dependsOn 'resolveTooling'\n doLast {\n def xs=configurations.fgRuntime.incoming.artifacts.artifacts.collect { a ->\n [coordinate:a.id.componentIdentifier.group+':'+a.id.componentIdentifier.module+':'+a.id.componentIdentifier.version,file:a.file.name,path:a.file.canonicalPath] }\n file('classpath.json').text=groovy.json.JsonOutput.toJson(xs)\n }\n}\n"
    (project/"build.gradle").write_text(source)
    gradle(p,project,p/"public-home","writeQualificationClasspath")
    require(json.loads((project/"resolved-tooling.json").read_text())["artifacts"]==m["artifacts"])
    checked_cp(p)
    print("Public toolchain and 31 artifact pins verified.")
def fetch(p):
    admission();checked_cp(p)
    url=os.environ.pop("OCPP_FORGE_INPUT_URL","")
    u=urllib.parse.urlsplit(url)
    require(u.scheme=="https" and u.hostname and u.port in (None,443))
    require(not u.username and not u.password and not u.fragment and not any(c.isspace() for c in url))
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self,*args,**kwargs): raise ValueError("Redirect rejected")
    opener=urllib.request.build_opener(NoRedirect())
    request=urllib.request.Request(url,method="GET",headers={"Accept-Encoding":"identity"})
    data=bytearray();deadline=time.monotonic()+60
    # Exactly one open/GET; no HEAD, Range, retries, alternate URL or persistent raw bundle.
    with opener.open(request,timeout=15) as response:
        require(response.status==200)
        require(response.headers.get("Content-Encoding","identity")=="identity")
        length=response.headers.get("Content-Length")
        require(length is None or length=="8548978")
        while True:
            require(time.monotonic()<deadline)
            b=response.read(65536)
            if not b: break
            data.extend(b);require(len(data)<=8548978)
    require(len(data)==8548978 and sha(data)==BUNDLE_SHA)
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        require(len(z.infolist())==7 and set(z.namelist())==NAMES)
        require(z.testzip() is None)
        jar=z.read("forge-1.12.2-14.23.5.2860-universal.jar")
    require(len(jar)==4466148 and sha(jar)==JAR_SHA)
    for case in ("valid","missing","corrupt"):
        repo=p/("repo-"+case);repo.mkdir()
        if case=="missing": continue
        payload=jar if case=="valid" else bytes([jar[0]^1])+jar[1:]
        target=repo/REL;target.parent.mkdir(parents=True)
        target.write_bytes(payload)
        target.with_name(target.name+".md5").write_text(hashlib.md5(payload).hexdigest())
    (p/"corrupt.sha256").write_text(sha(bytes([jar[0]^1])+jar[1:]))
    print("Private input identity verified; test data prepared.")
def probe(p):
    require("OCPP_FORGE_INPUT_URL" not in os.environ)
    results=[]
    for case in ("valid","missing","corrupt"):
        cp=checked_cp(p)
        project=p/("probe-"+case);project.mkdir()
        (project/"settings.gradle").write_text("rootProject.name='file-only-probe'\n")
        shutil.copyfile(BASE/"probe.gradle",project/"build.gradle")
        env=dict(os.environ,FGQUAL_CP=os.pathsep.join(cp),FGQUAL_ROOT=str(p))
        print("Starting file-only case: "+case)
        try:
            gradle(p,project,p/("test-home-"+case),"qualifyInput",("--offline","-Pcase="+case),env)
        except subprocess.CalledProcessError:
            status=project/"result.json"
            if status.exists():
                r=json.loads(status.read_text())
                print(json.dumps({k:r[k] for k in ("case","failureStage","errorClass","unexpectedNetworkOpens") if k in r},sort_keys=True))
            raise
        r=json.loads((project/"result.json").read_text())
        require(r["case"]==case and r["unexpectedNetworkOpens"]==0)
        results.append(r)
    print(json.dumps({"scope":"file-only downloader qualification; no main build","results":results},sort_keys=True))
def cleanup(p):
    if p.exists():
        marker=p/'OWNER'
        require(p.is_dir() and not p.is_symlink() and marker.is_file() and not marker.is_symlink())
        require(marker.read_bytes()==ownership())
        shutil.rmtree(p)
if __name__=="__main__":
    try:
        mode=sys.argv[1];p=root()
        {"prepare":prepare,"fetch":fetch,"probe":probe,"cleanup":cleanup}[mode](p)
    except Exception as error:
        print("Qualification rejected ("+type(error).__name__+"); no automatic retry or fallback.")
        sys.exit(1)
