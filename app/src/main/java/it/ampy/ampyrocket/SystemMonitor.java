package it.ampy.ampyrocket;

import android.app.ActivityManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Debug;
import android.os.Environment;
import android.os.Process;
import android.os.StatFs;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/**
 * Collects system-wide and per-app load.
 *  - STANDARD: RAM totals, per-core CPU frequency load, battery, storage, app activity from UsageStats.
 */
public final class SystemMonitor {
    public static final int HIST=48;

    public static final class Proc {
        public int pid;public String user="",state="",name="",pkg="";public float cpu;public long rssKb;
    }
    public static final class AppLoad {
        public String pkg;public float cpu;public long rssKb;public int procs;
        public long lastUsed,fgMs;public boolean foreground,fgService,running;
        public float activity;      // 0..1 share of foreground time in the last 15 min (standard mode)
        public final float[] cpuHist=new float[HIST],ramHist=new float[HIST];
        public final List<Proc> list=new ArrayList<>();
    }
    public static final class Snapshot {
        public String source="STANDARD";
        public long ramTotal,ramAvail,ramThreshold;public boolean lowMem;
        public float cpu;public boolean cpuReal;public int cores;
        public long[] curFreq=new long[0],maxFreq=new long[0];
        public int battery;public float batteryTemp;public boolean charging;
        public long storeTotal,storeFree;public long uptime;
        public long selfPssKb;public float selfCpu;
        public final List<Proc> procs=new ArrayList<>();
        public final List<AppLoad> apps=new ArrayList<>();
        public float[] cpuHist,ramHist;
    }

    private static SystemMonitor inst;
    public static synchronized SystemMonitor get(Context c){if(inst==null)inst=new SystemMonitor(c.getApplicationContext());return inst;}

    private final Context ctx;
    private final float[] cpuHist=new float[HIST],ramHist=new float[HIST];
    private final Map<String,AppLoad> appHist=new HashMap<>();
    private long[] lastStat;private long lastSelfCpu,lastSelfTime;

    private SystemMonitor(Context c){ctx=c;}

    private static void push(float[] h,float v){System.arraycopy(h,1,h,0,h.length-1);h[h.length-1]=v;}

    /** Cheap system snapshot (no per-process data). Safe to call every second. */
    public synchronized Snapshot quick(){
        Snapshot s=new Snapshot();fillBasics(s);
        s.cpuHist=cpuHist.clone();s.ramHist=ramHist.clone();return s;
    }

    /** Full snapshot including apps / processes. Call from a background thread. */
    public synchronized Snapshot full(boolean includeSystemProcs){
        Snapshot s=new Snapshot();
        fillBasics(s);
        Map<String,AppLoad> apps=new LinkedHashMap<>();
        usage(apps);
        selfOnly(s,apps);
        long now=System.currentTimeMillis();
        for(AppLoad a:apps.values()){
            AppLoad h=appHist.get(a.pkg);
            if(h==null){h=new AppLoad();h.pkg=a.pkg;appHist.put(a.pkg,h);}
            float cpuV=a.activity*100f;
            push(h.cpuHist,cpuV);push(h.ramHist,a.rssKb/1024f);
            System.arraycopy(h.cpuHist,0,a.cpuHist,0,HIST);System.arraycopy(h.ramHist,0,a.ramHist,0,HIST);
            if(a.running||a.fgService||a.procs>0||now-a.lastUsed<3*3600_000L)s.apps.add(a);
        }
        Collections.sort(s.apps,(x,y)->{
            if(x.foreground!=y.foreground)return x.foreground?-1:1;
            if(x.procs>0!=y.procs>0)return x.procs>0?-1:1;
            return Long.compare(y.lastUsed,x.lastUsed);
        });
        s.cpuHist=cpuHist.clone();s.ramHist=ramHist.clone();
        return s;
    }

    private void fillBasics(Snapshot s){
        ActivityManager am=(ActivityManager)ctx.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();am.getMemoryInfo(mi);
        s.ramTotal=mi.totalMem;s.ramAvail=mi.availMem;s.lowMem=mi.lowMemory;s.ramThreshold=mi.threshold;
        s.cores=Runtime.getRuntime().availableProcessors();
        s.curFreq=new long[s.cores];s.maxFreq=new long[s.cores];
        float freqLoad=0;int counted=0;
        for(int i=0;i<s.cores;i++){
            long cur=readLong("/sys/devices/system/cpu/cpu"+i+"/cpufreq/scaling_cur_freq");
            long max=readLong("/sys/devices/system/cpu/cpu"+i+"/cpufreq/cpuinfo_max_freq");
            long min=readLong("/sys/devices/system/cpu/cpu"+i+"/cpufreq/cpuinfo_min_freq");
            s.curFreq[i]=cur;s.maxFreq[i]=max;
            if(cur>0&&max>min){freqLoad+=(cur-min)/(float)(max-min);counted++;}
        }
        float cpu=counted>0?freqLoad/counted:0;
        float real=procStat(readFile("/proc/stat"));
        if(real>=0){cpu=real;s.cpuReal=true;}
        s.cpu=Math.max(0,Math.min(1,cpu));
        Intent bi=ctx.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(bi!=null){int lv=bi.getIntExtra(BatteryManager.EXTRA_LEVEL,0),sc=bi.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            s.battery=sc>0?lv*100/sc:lv;s.batteryTemp=bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10f;
            int st=bi.getIntExtra(BatteryManager.EXTRA_STATUS,0);
            s.charging=st==BatteryManager.BATTERY_STATUS_CHARGING||st==BatteryManager.BATTERY_STATUS_FULL;}
        try{StatFs fs=new StatFs(Environment.getDataDirectory().getPath());
            s.storeTotal=fs.getTotalBytes();s.storeFree=fs.getAvailableBytes();}catch(Throwable ignored){}
        s.uptime=SystemClock.elapsedRealtime();
        // own process (always readable)
        try{Debug.MemoryInfo[] m=am.getProcessMemoryInfo(new int[]{Process.myPid()});
            if(m!=null&&m.length>0)s.selfPssKb=m[0].getTotalPss();}catch(Throwable ignored){}
        long cpuMs=Process.getElapsedCpuTime(),t=SystemClock.elapsedRealtime();
        if(lastSelfTime>0&&t>lastSelfTime)s.selfCpu=100f*(cpuMs-lastSelfCpu)/((t-lastSelfTime)*(float)s.cores);
        lastSelfCpu=cpuMs;lastSelfTime=t;
        push(cpuHist,s.cpu*100f);push(ramHist,100f*(s.ramTotal-s.ramAvail)/Math.max(1,s.ramTotal));
    }

    /** Usage stats: foreground / recently used / foreground services. */
    private void usage(Map<String,AppLoad> apps){
        if(!Perms.usageAccess(ctx))return;
        UsageStatsManager um=(UsageStatsManager)ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        long end=System.currentTimeMillis(),begin=end-3*3600_000L,win=15*60_000L;
        Map<String,Long> resumedAt=new HashMap<>();
        try{
            UsageEvents ev=um.queryEvents(begin,end);UsageEvents.Event e=new UsageEvents.Event();
            while(ev.hasNextEvent()){
                ev.getNextEvent(e);String pkg=e.getPackageName();
                if(pkg==null||pkg.equals(ctx.getPackageName()))continue;
                AppLoad a=apps.get(pkg);if(a==null){a=new AppLoad();a.pkg=pkg;apps.put(pkg,a);}
                int type=e.getEventType();long ts=e.getTimeStamp();
                if(type==UsageEvents.Event.ACTIVITY_RESUMED){resumedAt.put(pkg,ts);a.foreground=true;a.lastUsed=ts;a.running=true;}
                else if(type==UsageEvents.Event.ACTIVITY_PAUSED||type==UsageEvents.Event.ACTIVITY_STOPPED){
                    Long r=resumedAt.remove(pkg);a.foreground=false;a.lastUsed=Math.max(a.lastUsed,ts);
                    if(r!=null){long from=Math.max(r,end-win);if(ts>from)a.fgMs+=ts-from;}
                    if(type==UsageEvents.Event.ACTIVITY_STOPPED)a.running=end-ts<30*60_000L;
                }
                else if(type==UsageEvents.Event.FOREGROUND_SERVICE_START){a.fgService=true;a.running=true;}
                else if(type==UsageEvents.Event.FOREGROUND_SERVICE_STOP){a.fgService=false;}
            }
        }catch(Throwable ignored){}
        for(Map.Entry<String,Long> r:resumedAt.entrySet()){
            AppLoad a=apps.get(r.getKey());long from=Math.max(r.getValue(),end-win);a.fgMs+=end-from;a.foreground=true;a.lastUsed=end;
        }
        for(AppLoad a:apps.values())a.activity=Math.min(1f,a.fgMs/(float)win);
    }

    private void selfOnly(Snapshot s,Map<String,AppLoad> apps){
        Proc me=new Proc();me.pid=Process.myPid();me.name=ctx.getPackageName();me.pkg=me.name;me.user="self";
        me.cpu=s.selfCpu;me.rssKb=s.selfPssKb;me.state="R";s.procs.add(me);
    }

    /** Returns 0..1 load from two /proc/stat samples, or -1 when unreadable. */
    private float procStat(String txt){
        if(txt==null)return -1;
        for(String line:txt.split("\n")){
            if(!line.startsWith("cpu "))continue;
            String[] f=line.trim().split("\\s+");if(f.length<8)return -1;
            long[] v=new long[f.length-1];for(int i=1;i<f.length;i++)try{v[i-1]=Long.parseLong(f[i]);}catch(Exception e){return -1;}
            long idle=v[3]+(v.length>4?v[4]:0),total=0;for(long x:v)total+=x;
            float load=-1;
            if(lastStat!=null){long dT=total-lastStat[0],dI=idle-lastStat[1];if(dT>0)load=1f-dI/(float)dT;}
            lastStat=new long[]{total,idle};
            return load<0?-1:load;
        }
        return -1;
    }

    public static long parseMem(String s){
        try{s=s.trim();char u=Character.toUpperCase(s.charAt(s.length()-1));
            if(Character.isDigit(u))return Long.parseLong(s);
            double v=Double.parseDouble(s.substring(0,s.length()-1));
            if(u=='K')return (long)v;if(u=='M')return (long)(v*1024);if(u=='G')return (long)(v*1024*1024);
            return (long)v;}catch(Exception e){return 0;}
    }
    private static float parseF(String s){try{return Float.parseFloat(s.replace(",","."));}catch(Exception e){return 0;}}
    private static long readLong(String path){String s=readFile(path);if(s==null)return 0;try{return Long.parseLong(s.trim());}catch(Exception e){return 0;}}
    private static String readFile(String path){
        try(BufferedReader br=new BufferedReader(new FileReader(path))){StringBuilder sb=new StringBuilder();String l;
            int n=0;while((l=br.readLine())!=null&&n++<4)sb.append(l).append('\n');return sb.toString();}
        catch(Throwable t){return null;}
    }

    public static String fmtKb(long kb){
        if(kb>=1024*1024)return String.format(Locale.US,"%.2f GB",kb/1048576f);
        if(kb>=1024)return String.format(Locale.US,"%.0f MB",kb/1024f);
        return kb+" KB";
    }
    public static String fmtBytes(long b){return fmtKb(b/1024);}
}
