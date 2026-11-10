package li.cil.oc.plusplus;

import li.cil.oc.api.vm.v2.*;
import li.cil.oc.api.vm.v2.ProtocolV2.*;
import li.cil.oc.api.vm.v2.ProtocolV2.Error;
import li.cil.oc.api.vm.v2.FrameAssemblerV2.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;

/** Synthetic in-memory protocol only. No engine, guest, device, listener or host files. */
public class Api2OfflineTest {
    static final String HASH=String.join("",Collections.nCopies(64,"a"));
    static Identity identity(){return new Identity(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());}
    interface Action {void run()throws Exception;}
    static void denied(Action a)throws Exception{try{a.run();fail("must reject");}catch(IllegalArgumentException|IllegalStateException|SecurityException expected){}}
    static SessionV2 session(Identity i){return new SessionV2.Pool(32).open(i,x->x.equals(i),1,1,HASH,0);}
    static Message message(SessionV2 s,long seq){return new Message(s.id,UUID.randomUUID(),s.identity,1,seq,1,HASH,Operation.STATUS);}
    static Header header(SessionV2 s,long id,int w,int h){return new Header(s.id,1,1,HASH,id,w,h,w*4,w*h*4);}
    static String wire(SessionV2 s){return "{\"version\":2,\"session\":\""+s.id+"\",\"correlation\":\""+UUID.randomUUID()+"\",\"world\":\""+s.identity.world+"\",\"case\":\""+s.identity.machine+"\",\"owner\":\""+s.identity.owner+"\",\"package\":\""+s.identity.pack+"\",\"generation\":1,\"sequence\":1,\"epoch\":1,\"canvas\":\""+HASH+"\",\"operation\":\"STATUS\"}";}
    @Test public void api1SymbolsAndOldProviderRemainIndependent()throws Exception{
        li.cil.oc.api.vm.SuperBios.Provider old=new li.cil.oc.vm.companion.OCVMIntegration.OfflineProvider();
        li.cil.oc.api.vm.SuperBios.register(old);
        ProtocolV2.Provider next=new li.cil.oc.vm.companion.OCVMIntegration.OfflineProvider2();
        try{assertEquals(1,old.apiVersion());assertEquals(1,li.cil.oc.api.vm.SuperBios.statusCode());assertEquals(Error.MISSING_ADDON,ProtocolV2.status());ProtocolV2.register(next);assertEquals(Error.OFFLINE_PROVIDER,ProtocolV2.status());assertFalse(next.capabilities().mayExecuteGuest());assertFalse(ProtocolV2.mayExecuteGuest());assertFalse(li.cil.oc.api.vm.SuperBios.mayExecuteGuest());denied(()->ProtocolV2.register(new li.cil.oc.vm.companion.OCVMIntegration.OfflineProvider2()));}
        finally{ProtocolV2.unregister(next);li.cil.oc.api.vm.SuperBios.unregister(old);}
    }
    @Test public void negotiationRejectsVersionAndBackendWithoutTouchingApi1()throws Exception{
        for(final int v:new int[]{1,3})denied(()->ProtocolV2.register(new ProtocolV2.Provider(){public int apiVersion(){return v;}public String backend(){return "qemu-tcg";}public Capabilities capabilities(){return new Capabilities();}}));
        denied(()->ProtocolV2.register(new ProtocolV2.Provider(){public int apiVersion(){return 2;}public String backend(){return "arbitrary-shell";}public Capabilities capabilities(){return new Capabilities();}}));assertEquals(Error.MISSING_ADDON,ProtocolV2.status());
    }
    @Test public void strictWireHasUtf8ByteGateBeforeDecode()throws Exception{
        SessionV2 s=session(identity());String json=wire(s);Message m=ProtocolV2.decode(json.getBytes(StandardCharsets.UTF_8));assertEquals(s.id,m.session);assertEquals(Operation.STATUS,m.operation);
        byte[] unicode=String.join("",Collections.nCopies(6000,"界")).getBytes(StandardCharsets.UTF_8);assertEquals(18000,unicode.length);
        try{ProtocolV2.decode(unicode);fail();}catch(IllegalArgumentException e){assertEquals("control byte limit",e.getMessage());}
        denied(()->ProtocolV2.decode(new byte[]{(byte)0xc3,0x28}));denied(()->ProtocolV2.decode(new byte[16385]));
    }
    @Test public void wireRejectsDuplicatesUnknownFieldsTypesOverflowAndTrailingData()throws Exception{
        String good=wire(session(identity()));
        for(String bad:new String[]{good.replace("\"version\":2","\"version\":2,\"version\":2"),good.replace("\"version\":2","\"version\":\"2\""),good.replace("\"sequence\":1","\"sequence\":9223372036854775808"),good.replace("STATUS","execute-qmp"),good.replace("\"version\":2","\"qmp\":\"quit\",\"version\":2"),good+"{}",good.replace("\"epoch\":1","\"epoch\":-1"),good.replace("\"sequence\":1","\"sequence\":1.0"),good.replace("\"sequence\":1,","")})denied(()->ProtocolV2.decode(bad.getBytes(StandardCharsets.UTF_8)));
    }
    @Test public void hostAliasesAreDeclarationsNotRuntimeQualification()throws Exception{
        for(String os:new String[]{"Windows 11","Linux","Mac OS X","Darwin","macOS"})for(String arch:new String[]{"amd64","x86_64","aarch64","arm64"}){Host h=Host.declared(os,arch);assertFalse(h.runtimeQualified());}
        denied(()->Host.declared("FreeBSD","amd64"));denied(()->Host.declared("Linux","i386"));
        BackendProfile p=new BackendProfile(Host.declared("Linux","amd64"),UUID.randomUUID(),HASH,"aarch64","virt","unqualified","cortex-a53","unselected",HASH,"unreviewed");assertEquals(Error.UNQUALIFIED_PACK,p.support());
        denied(()->new BackendProfile(p.host,p.pack,HASH,"anything","pc","1","cpu","../firmware",HASH,"license"));
    }
    @Test public void sessionChecksAuthorityReplayOrderAndLayoutEveryOperation()throws Exception{
        Identity i=identity();SessionV2 s=session(i);Message m=message(s,1);s.accept(i,m,1);denied(()->s.accept(i,m,2));denied(()->s.accept(i,message(s,3),3));s.complete(m.correlation);s.accept(i,message(s,2),4);
        denied(()->s.check(i,s.id,2,1,HASH,5));denied(()->s.check(i,s.id,1,2,HASH,5));denied(()->s.accept(i,new Message(s.id,UUID.randomUUID(),identity(),1,3,1,HASH,Operation.STATUS),5));
        denied(()->s.check(identity(),s.id,1,1,HASH,6));assertTrue(s.revoked());
    }
    @Test public void liveAuthorizationRevocationExpiresAllAttachedBuffersAndInput()throws Exception{
        Identity i=identity();AtomicBoolean allowed=new AtomicBoolean(true);SessionV2 s=new SessionV2.Pool(2).open(i,x->allowed.get(),1,1,HASH,0);Budget b=new Budget(16*1024*1024);FrameAssemblerV2 f=new FrameAssemblerV2(s,b,0);GuestInputV2 input=new GuestInputV2(s,new GuestInputV2.Limiter(),0);
        f.begin(i,header(s,1,64,64),0);assertTrue(b.used()>0);input.focus(i,0);input.offer(i,s.id,1,1,HASH,GuestInputV2.Event.key(true,1),0);allowed.set(false);denied(()->s.check(i,s.id,1,1,HASH,1));assertEquals(0,b.used());assertEquals(GuestInputV2.Kind.RELEASE_ALL,input.poll().kind);assertNull(input.poll());assertEquals(0,f.latestFrame());
    }
    @Test public void boundedPendingRequestsTimeoutAndGlobalCaseQuotas()throws Exception{
        Identity i=identity();SessionV2.Pool pool=new SessionV2.Pool(3);SessionV2 a=pool.open(i,x->true,1,1,HASH,0);pool.open(i,x->true,1,1,HASH,0);denied(()->pool.open(i,x->true,1,1,HASH,0));
        for(int n=1;n<=32;n++)a.accept(i,message(a,n),1);assertEquals(32,a.pending());denied(()->a.accept(i,message(a,33),2));a.accept(i,message(a,33),2001);assertEquals(1,a.pending());pool.tick(32002);assertEquals(0,pool.size());assertTrue(a.revoked());
    }
    @Test public void clockRollbackAndCrossDispatcherUseFailClosed()throws Exception{
        SessionV2 s=session(identity());s.check(s.identity,s.id,1,1,HASH,10);denied(()->s.check(s.identity,s.id,1,1,HASH,9));assertTrue(s.revoked());
        SessionV2 other=session(identity());AtomicReference<Throwable> result=new AtomicReference<>();Thread t=new Thread(()->{try{other.check(other.identity,other.id,1,1,HASH,0);}catch(Throwable e){result.set(e);}});t.start();t.join(2000);assertFalse(t.isAlive());assertTrue(result.get() instanceof IllegalStateException);assertFalse(other.revoked());
    }
    @Test public void productionCannotStartAndSimulatedLifecycleIsExplicit()throws Exception{
        LifecycleV2 real=LifecycleV2.productionUnavailable();assertFalse(real.simulated);assertEquals(Error.RUNTIME_DISABLED,real.start());assertEquals(LifecycleV2.State.OFFLINE_ONLY,real.state());denied(real::observedRunning);assertFalse(real.mayExecuteGuest());
        LifecycleV2 fake=LifecycleV2.syntheticFixture();assertTrue(fake.simulated);assertEquals(Error.NONE,fake.stop());assertEquals(Error.NONE,fake.start());denied(fake::observedCleanStop);fake.observedRunning();assertEquals(Error.NONE,fake.stop());fake.observedCleanStop();assertEquals(LifecycleV2.State.STOPPED,fake.state());assertFalse(fake.mayExecuteGuest());
    }
    @Test public void simulatedCrashRetainsQuarantineAndCannotRestart()throws Exception{
        LifecycleV2 s=LifecycleV2.syntheticFixture();s.start();s.observedCrash();assertTrue(s.requiresDiskReconciliation());assertFalse(s.deletesData());assertEquals(Error.FAILED,s.start());s.revoke();assertEquals(Error.REVOKED,s.start());denied(s::observedRunning);
    }
    @Test public void offlineAdapterCorrelatesRequestsButNeverStartsAProcess()throws Exception{
        Identity i=identity();SessionV2 s=session(i);Transport t=new li.cil.oc.vm.companion.OCVMIntegration.OfflineProvider2().openOffline(s);
        Reply status=t.request(i,wire(s).getBytes(StandardCharsets.UTF_8),0);assertEquals(s.id,status.session);assertEquals(Error.OFFLINE_PROVIDER,status.error);assertFalse(status.simulated);assertFalse(status.mayExecuteGuest());assertEquals(0,s.pending());
        String start=wire(s).replace("\"sequence\":1","\"sequence\":2").replace("STATUS","START");assertEquals(Error.RUNTIME_DISABLED,t.request(i,start.getBytes(StandardCharsets.UTF_8),1).error);
        String stop=wire(s).replace("\"sequence\":1","\"sequence\":3").replace("STATUS","STOP");assertEquals(Error.RUNTIME_DISABLED,t.request(i,stop.getBytes(StandardCharsets.UTF_8),2).error);
        String revoke=wire(s).replace("\"sequence\":1","\"sequence\":4").replace("STATUS","REVOKE");assertEquals(Error.REVOKED,t.request(i,revoke.getBytes(StandardCharsets.UTF_8),3).error);assertTrue(s.revoked());denied(()->t.request(i,wire(s).getBytes(StandardCharsets.UTF_8),4));
    }
    @Test public void frameAssemblyOutOfOrderAndEquivalentHeaderProduceExactPixels()throws Exception{
        Identity i=identity();SessionV2 s=session(i);Budget b=new Budget(16*1024*1024);FrameAssemblerV2 f=new FrameAssemblerV2(s,b,0);Header h=header(s,1,128,128),same=header(s,1,128,128);f.begin(i,h,0);byte[] a=new byte[32768],z=new byte[32768];Arrays.fill(a,(byte)7);Arrays.fill(z,(byte)9);assertFalse(f.chunk(i,same,32768,z,1));assertTrue(f.chunk(i,same,0,a,2));byte[] pixels=new byte[65536];f.copyLatest(i,same,pixels,3);assertEquals(7,pixels[0]);assertEquals(9,pixels[32768]);assertEquals(1,f.latestFrame());f.close();assertEquals(0,b.used());
    }
    @Test public void frameRejectsMalformedBoundsChunksReplayAndPartialPublication()throws Exception{
        SessionV2 s=session(identity());Budget b=new Budget(1000000);FrameAssemblerV2 f=new FrameAssemblerV2(s,b,0);Header h=header(s,1,128,128);f.begin(s.identity,h,0);assertFalse(f.chunk(s.identity,h,0,new byte[32768],1));assertEquals(0,f.latestFrame());denied(()->f.chunk(s.identity,h,0,new byte[32768],2));denied(()->f.chunk(s.identity,h,1,new byte[32768],2));denied(()->f.chunk(s.identity,h,32768,new byte[1],2));denied(()->new Header(s.id,1,1,HASH,2,640,480,2560,1228800));denied(()->new Header(s.id,1,1,HASH,2,Integer.MAX_VALUE,2,4,8));f.close();assertEquals(0,b.used());
    }
    @Test public void framesExpireAndReplacementNeverMixesOldEpochOrData()throws Exception{
        SessionV2 s=session(identity());Budget b=new Budget(1000000);FrameAssemblerV2 f=new FrameAssemblerV2(s,b,0);Header old=header(s,1,64,64),fresh=header(s,2,64,64);f.begin(s.identity,old,0);f.expire(2000);assertEquals(0,b.used());denied(()->f.chunk(s.identity,old,0,new byte[16384],2000));f.begin(s.identity,fresh,2001);assertTrue(f.chunk(s.identity,fresh,0,new byte[16384],2001));denied(()->f.begin(s.identity,old,2002));denied(()->f.copyLatest(s.identity,old,new byte[16384],2002));s.revoke();assertEquals(0,b.used());
    }
    @Test public void aggregateMemoryAndTwoFrameCeilingHoldAcrossSessions()throws Exception{
        Identity i=identity();SessionV2 s=session(i);Budget b=new Budget(33000);FrameAssemblerV2 f=new FrameAssemblerV2(s,b,0);Header h=header(s,1,64,64);f.begin(i,h,0);denied(()->f.begin(i,header(s,2,64,64),0));assertEquals(16640,b.used());f.close();assertEquals(0,b.used());
        Budget large=new Budget(1000000);FrameAssemblerV2 f2=new FrameAssemblerV2(s,large,0);f2.begin(i,header(s,1,1,1),0);f2.begin(i,header(s,2,1,1),0);denied(()->f2.begin(i,header(s,3,1,1),0));f2.close();assertEquals(0,large.used());
    }
    @Test public void sharedCaseFrameRateCannotResetByClosingAssembler()throws Exception{
        Identity i=identity();Budget b=new Budget(1000000);
        for(int n=0;n<4;n++){SessionV2 s=session(i);FrameAssemblerV2 f=new FrameAssemblerV2(s,b,0);try{Header h=header(s,n+1,1,1);f.begin(i,h,0);assertTrue(f.chunk(i,h,0,new byte[4],0));}finally{f.close();s.revoke();}}
        SessionV2 s=session(i);FrameAssemblerV2 f=new FrameAssemblerV2(s,b,0);denied(()->f.begin(i,header(s,5,1,1),0));f.begin(i,header(s,5,1,1),1000);f.close();s.revoke();assertEquals(0,b.used());
    }
    @Test public void guestCoordinatesFocusAndRevocationReleaseAreBounded()throws Exception{
        Identity i=identity();SessionV2 s=session(i);GuestInputV2 in=new GuestInputV2(s,new GuestInputV2.Limiter(),0);GuestInputV2.Event e=GuestInputV2.Event.pointer(1,1,640,480);assertEquals(639,e.x);assertEquals(479,e.y);assertTrue(e.maximumEncodedBytes()<=256);
        denied(()->in.offer(i,s.id,1,1,HASH,e,0));in.focus(i,0);in.offer(i,s.id,1,1,HASH,e,0);assertEquals(e,in.poll());denied(()->in.offer(i,s.id,1,2,HASH,e,1));in.loseFocus();assertEquals(GuestInputV2.Kind.RELEASE_ALL,in.poll().kind);denied(()->GuestInputV2.Event.pointer(Double.NaN,0,1,1));denied(()->GuestInputV2.Event.pointer(-0.1,0,640,480));denied(()->GuestInputV2.Event.key(true,256));s.revoke();assertEquals(GuestInputV2.Kind.RELEASE_ALL,in.poll().kind);
    }
    @Test public void sharedInputLimiterEnforcesRateAndQueueWithoutSleep()throws Exception{
        Identity i=identity();SessionV2 s=session(i);GuestInputV2.Limiter limit=new GuestInputV2.Limiter();GuestInputV2 a=new GuestInputV2(s,limit,0);a.focus(i,0);for(int n=0;n<30;n++)a.offer(i,s.id,1,1,HASH,GuestInputV2.Event.key(true,n),0);denied(()->a.offer(i,s.id,1,1,HASH,GuestInputV2.Event.key(true,31),0));a.offer(i,s.id,1,1,HASH,GuestInputV2.Event.key(true,31),1000);a.offer(i,s.id,1,1,HASH,GuestInputV2.Event.key(true,31),1000);denied(()->a.offer(i,s.id,1,1,HASH,GuestInputV2.Event.key(true,31),1000));a.close();GuestInputV2 next=new GuestInputV2(s,limit,1000);next.focus(i,1000);for(int n=0;n<28;n++)next.offer(i,s.id,1,1,HASH,GuestInputV2.Event.key(true,1),1000);denied(()->next.offer(i,s.id,1,1,HASH,GuestInputV2.Event.key(true,1),1000));next.close();
    }
}
