package it.ampy.ampyrocket;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

/**
 * Energy policy. PERFORMANCE = everything live; BALANCED (default) = 24 fps animations that
 * freeze a few seconds after the last touch; SAVER = static backgrounds, per-minute clock, no idle glitches.
 * The system battery saver forces SAVER (optional).
 */
public final class Power {
    public static final int PERF=0,BAL=1,MAX=2;
    public static final String[] NAMES={"PRESTAZIONI (sempre animato)","BILANCIATO (anima dopo un tocco)","RISPARMIO MASSIMO (statico)"};
    private static int cached=-1;private static long cachedAt;
    private static volatile long lastTouch=SystemClock.uptimeMillis();
    private Power(){}

    public static int level(Context c){
        long now=SystemClock.uptimeMillis();
        if(cached>=0&&now-cachedAt<4000)return cached;
        Prefs pr=Prefs.get(c);int l=pr.i("power_mode",BAL);
        try{PowerManager pm=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
            if(pr.b("follow_saver",true)&&pm!=null&&pm.isPowerSaveMode())l=MAX;}catch(Throwable ignored){}
        cached=l;cachedAt=now;return l;
    }
    public static void invalidate(){cached=-1;}

    /** Call on every user interaction: animations wake up for a while. */
    public static void touch(){lastTouch=SystemClock.uptimeMillis();}
    public static long sinceTouch(){return SystemClock.uptimeMillis()-lastTouch;}

    /** Background frame interval in ms, or -1 when the background must stay still. */
    public static long frameMs(Context c){
        switch(level(c)){
            case PERF:return Prefs.get(c).ecoFps()?33:0;          // 0 = vsync
            case BAL:return sinceTouch()<Prefs.get(c).i("anim_idle_s",8)*1000L?42:-1;
            default:return -1;
        }
    }
    public static long hudPollMs(Context c){return new long[]{2000,4000,15000}[level(c)];}
    public static long taskPollMs(Context c){return new long[]{2000,3000,5000}[level(c)];}
    public static boolean smoothClock(Context c){return level(c)==PERF;}
    public static boolean secondsClock(Context c){return level(c)!=MAX;}
    public static boolean idleGlitch(Context c){return level(c)!=MAX;}
    public static boolean continuousFx(Context c){return level(c)==PERF;}
}
