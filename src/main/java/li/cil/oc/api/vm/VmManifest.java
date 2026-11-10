package li.cil.oc.api.vm;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.util.*;

/** Metadata-only validator. Never opens a declared file, executable or disk. */
public final class VmManifest {
    private VmManifest(){}
    public static final class Plan {
        public final UUID packageId,worldId,caseId,raidId,volumeId;
        public final String architecture,machine,firmware,path,capacityPolicy;
        public final long capacityBytes;
        public final List<UUID> members;
        public final String hostOS,hostArchitecture,machineVersion,cpuModel,firmwareLicense,diskFormat;
        public final UUID nvramId,ownerId;
        public final int vcpus,ramMiB;
        private Plan(JsonObject j){
            packageId=id(j,"packageId");worldId=id(j,"worldId");caseId=id(j,"caseId");raidId=id(j,"raidId");volumeId=id(j,"volumeId");
            architecture=string(j,"architecture");machine=string(j,"machine");firmware=string(j,"firmware");path=relative(string(j,"diskPath"));capacityPolicy=string(j,"capacityPolicy");
            ownerId=id(j,"ownerId");nvramId=id(j,"nvramId");hostOS=string(j,"hostOS");hostArchitecture=string(j,"hostArchitecture");machineVersion=string(j,"machineVersion");cpuModel=string(j,"cpuModel");firmwareLicense=string(j,"firmwareLicense");diskFormat=string(j,"diskFormat");
            if(!Arrays.asList("windows","linux","macos").contains(hostOS)||!Arrays.asList("x86_64","arm64").contains(hostArchitecture))throw new IllegalArgumentException("undeclared host platform");
            if(!machineVersion.matches("[A-Za-z0-9_.+-]{1,80}")||!cpuModel.matches("[A-Za-z0-9_.+-]{1,80}")||!firmwareLicense.matches("[A-Za-z0-9_.+-]{1,80}"))throw new IllegalArgumentException("invalid profile identifier");
            if(!Arrays.asList("raw","qcow2","vdi").contains(diskFormat)||j.getAsJsonArray("externalDependencies").size()!=0)throw new IllegalArgumentException("independent managed disk required; external dependencies need review");
            long cpu=number(j.get("vcpus")),ram=number(j.get("ramMiB"));if(cpu<1||cpu>4||ram<16||ram>512)throw new IllegalArgumentException("offline planning quota exceeded");vcpus=(int)cpu;ramMiB=(int)ram;
            if(!Arrays.asList("x86_64","aarch64","riscv64").contains(architecture))throw new IllegalArgumentException("unsupported declared architecture");
            if(!machine.matches("[A-Za-z0-9_.+:-]{1,80}")||!firmware.matches("[A-Za-z0-9_.+-]{1,80}"))throw new IllegalArgumentException("invalid machine/firmware identifier");
            if(!string(j,"firmwareSha256").matches("[a-f0-9]{64}"))throw new IllegalArgumentException("firmware hash required");
            if(!"qemu-tcg".equals(string(j,"backend"))||number(j.get("schema"))!=1)throw new IllegalArgumentException("unsupported backend/schema");
            if(!Arrays.asList("unapproved","literal-bytes").contains(capacityPolicy))throw new IllegalArgumentException("unreviewed capacity scaling");
            JsonArray ids=j.getAsJsonArray("memberIds"),sizes=j.getAsJsonArray("memberBytes");
            if(ids==null||sizes==null||ids.size()!=3||sizes.size()!=3)throw new IllegalArgumentException("aggregate RAID requires three members");
            List<UUID> list=new ArrayList<>();long total=0;
            for(int n=0;n<3;n++){UUID u=canonicalId(ids.get(n).getAsString());if(list.contains(u))throw new IllegalArgumentException("duplicate member identity");list.add(u);
                long size=number(sizes.get(n));if(size<=0||size>(1L<<40))throw new IllegalArgumentException("invalid member capacity");total=Math.addExact(total,size);}
            capacityBytes=number(j.get("capacityBytes"));if(capacityBytes!=total)throw new IllegalArgumentException("aggregate byte total mismatch");
            members=Collections.unmodifiableList(list);
        }
        public boolean executable(){return false;}
    }
    public static Plan validate(String json){
        if(json==null||json.length()>65536)throw new IllegalArgumentException("manifest limit");
        int depth=0;boolean quoted=false,escape=false;
        for(int n=0;n<json.length();n++){char c=json.charAt(n);if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quoted=false;}
            else if(c=='"')quoted=true;else if(c=='{'||c=='['){if(++depth>16)throw new IllegalArgumentException("manifest nesting limit");}
            else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException("unbalanced manifest");}
            else if(c=='/'||c=='#'||c=='\''||c=='<'||c=='>'||c==0)throw new IllegalArgumentException("non-JSON input");}
        if(quoted||depth!=0)throw new IllegalArgumentException("unbalanced manifest");
        try{JsonReader reader=new JsonReader(new StringReader(json));reader.setLenient(false);JsonObject j=read(reader,0).getAsJsonObject();if(reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("trailing manifest data");
            Set<String> keys=new HashSet<>(Arrays.asList("schema","backend","packageId","worldId","caseId","raidId","volumeId","architecture","machine","firmware","firmwareSha256","diskPath","capacityPolicy","capacityBytes","memberIds","memberBytes","hostOS","hostArchitecture","machineVersion","cpuModel","firmwareLicense","diskFormat","nvramId","ownerId","vcpus","ramMiB","externalDependencies"));
            for(Map.Entry<String,JsonElement> e:j.entrySet())if(!keys.remove(e.getKey()))throw new IllegalArgumentException("unknown field: "+e.getKey());
            if(!keys.isEmpty())throw new IllegalArgumentException("missing fields");return new Plan(j);
        }catch(RuntimeException|IOException e){throw new IllegalArgumentException("invalid VM manifest: "+e.getMessage(),e);}
    }
    private static JsonElement read(JsonReader r,int depth)throws IOException{
        if(depth>16)throw new IllegalArgumentException("manifest nesting limit");
        switch(r.peek()){
            case BEGIN_OBJECT: r.beginObject();JsonObject o=new JsonObject();Set<String> keys=new HashSet<>();while(r.hasNext()){String k=r.nextName();if(!keys.add(k))throw new IllegalArgumentException("duplicate field");o.add(k,read(r,depth+1));}r.endObject();return o;
            case BEGIN_ARRAY: r.beginArray();JsonArray a=new JsonArray();while(r.hasNext()){if(a.size()>=4096)throw new IllegalArgumentException("array limit");a.add(read(r,depth+1));}r.endArray();return a;
            case STRING: return new JsonPrimitive(r.nextString());
            case NUMBER: String n=r.nextString();if(!n.matches("-?(0|[1-9][0-9]{0,15})"))throw new IllegalArgumentException("integer value required");return new JsonPrimitive(new java.math.BigDecimal(n));
            case BOOLEAN: return new JsonPrimitive(r.nextBoolean());
            case NULL: r.nextNull();return JsonNull.INSTANCE;
            default: throw new IllegalArgumentException("invalid JSON token");
        }
    }
    public static UUID canonicalId(String s){UUID u=UUID.fromString(s);if(!u.toString().equals(s))throw new IllegalArgumentException("noncanonical UUID");return u;}
    private static UUID id(JsonObject j,String k){return canonicalId(string(j,k));}
    private static long number(JsonElement e){if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("numeric field required");return e.getAsLong();}
    private static String string(JsonObject j,String k){JsonElement e=j.get(k);if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("string field required");return e.getAsString();}
    public static String relative(String s){
        if(s.isEmpty()||s.length()>1024||s.startsWith("/")||s.contains("\\")||s.contains(":"))throw new IllegalArgumentException("owned relative path required");
        for(String p:s.split("/",-1))if(p.isEmpty()||p.equals(".")||p.equals("..")||p.endsWith(".")||!p.matches("[A-Za-z0-9_.-]{1,255}")||p.split("\\.",-1)[0].toUpperCase(Locale.ROOT).matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))throw new IllegalArgumentException("invalid path segment");
        return s;
    }
}
