package it.ampy.ampyrocket;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

/**
 * Launcher gestures (lock, shade, recents) and, only when the user closes an app from
 * the task drawer, presses "Force stop" in that app's system info page.
 * It does nothing with other screens and never collects their content.
 */
public final class GestureService extends AccessibilityService {
    private static GestureService live;
    private static final ArrayDeque<String> queue=new ArrayDeque<>();
    private static final Handler h=new Handler(Looper.getMainLooper());
    private static String current;private static int step;private static long stepAt;private static int done;
    private static final int OPEN=0,BUTTON=1,CONFIRM=2;

    private static final String[] FORCE={"forza arresto","arresto forzato","forza interruzione","force stop","forcer l'arrêt",
        "forzar detención","forzar cierre","stopp erzwingen","beenden erzwingen","forçar parada","forçar paragem"};
    private static final String[] OK={"ok","forza arresto","force stop","arresta","sì","si","yes","conferma","accetta"};

    @Override protected void onServiceConnected(){live=this;listen(false);}

    /** Receive window events only while a force-stop job runs: zero wake-ups the rest of the time. */
    private void listen(boolean on){
        try{android.accessibilityservice.AccessibilityServiceInfo i=getServiceInfo();if(i==null)return;
            i.eventTypes=on?AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED|AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:0;
            i.notificationTimeout=on?150:1000;setServiceInfo(i);}catch(Throwable ignored){}
    }
    @Override public boolean onUnbind(Intent i){live=null;return super.onUnbind(i);}
    @Override public void onInterrupt(){}

    public static boolean available(){return live!=null;}
    public static boolean lock(){return live!=null&&live.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);}
    public static boolean notifications(){return live!=null&&live.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);}
    public static boolean quickSettings(){return live!=null&&live.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);}
    public static boolean recents(){return live!=null&&live.performGlobalAction(GLOBAL_ACTION_RECENTS);}
    public static boolean busy(){return current!=null||!queue.isEmpty();}

    /** Queues apps to force-stop; work starts shortly after so several closes are batched. */
    public static void forceStop(String pkg){
        if(live==null||pkg==null||queue.contains(pkg)||pkg.equals(current))return;
        queue.add(pkg);
        if(current==null){h.removeCallbacks(NEXT);h.postDelayed(NEXT,350);}
    }

    private static final Runnable NEXT=new Runnable(){@Override public void run(){
        GestureService s=live;
        if(s==null){queue.clear();current=null;return;}
        current=queue.poll();
        if(current==null){s.listen(false);finish(s);return;}
        s.listen(true);
        step=OPEN;stepAt=System.currentTimeMillis();
        try{s.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+current))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_HISTORY|Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
            step=BUTTON;}
        catch(Throwable t){current=null;h.post(this);return;}
        h.removeCallbacks(TICK);h.postDelayed(TICK,450);
    }};
    // Polls the visible window while a job runs (events alone are unreliable across OEM skins).
    private static final Runnable TICK=new Runnable(){@Override public void run(){
        GestureService s=live;if(s==null||current==null)return;
        s.work();
        if(current!=null){
            if(System.currentTimeMillis()-stepAt>5000){current=null;s.performGlobalAction(GLOBAL_ACTION_BACK);h.postDelayed(NEXT,400);return;}
            h.postDelayed(this,300);
        }
    }};

    private static void finish(GestureService s){
        if(done>0)try{s.startActivity(new Intent(s,HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Throwable ignored){}
        done=0;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e){if(current!=null)work();}

    private void work(){
        if(current==null)return;
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return;
        CharSequence pkgName=root.getPackageName();
        if(pkgName==null||pkgName.toString().equals(getPackageName()))return;
        if(step==BUTTON){
            AccessibilityNodeInfo b=find(root,FORCE,false);
            if(b==null)return;
            if(!b.isEnabled()&&!clickableParentEnabled(b)){next();return;}   // already stopped
            if(click(b)){step=CONFIRM;stepAt=System.currentTimeMillis();}
        }else if(step==CONFIRM){
            List<AccessibilityNodeInfo> ok=root.findAccessibilityNodeInfosByViewId("android:id/button1");
            AccessibilityNodeInfo b=ok!=null&&!ok.isEmpty()?ok.get(0):find(root,OK,true);
            if(b!=null&&click(b)){done++;next();}
        }
    }
    private void next(){
        current=null;h.removeCallbacks(TICK);
        h.postDelayed(()->{performGlobalAction(GLOBAL_ACTION_BACK);h.postDelayed(NEXT,350);},250);
    }

    private static boolean clickableParentEnabled(AccessibilityNodeInfo n){
        AccessibilityNodeInfo p=n;for(int i=0;i<4&&p!=null;i++){if(p.isClickable())return p.isEnabled();p=p.getParent();}
        return false;
    }
    private static boolean click(AccessibilityNodeInfo n){
        AccessibilityNodeInfo p=n;
        for(int i=0;i<5&&p!=null;i++){if(p.isClickable()&&p.isEnabled())return p.performAction(AccessibilityNodeInfo.ACTION_CLICK);p=p.getParent();}
        return false;
    }
    /** Depth-first search for a node whose text equals (or starts with) one of the labels. */
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo n,String[] labels,boolean exact){
        if(n==null)return null;
        CharSequence t=n.getText();
        if(t!=null){String s=t.toString().trim().toLowerCase(Locale.ROOT);
            for(String l:labels)if(exact?s.equals(l):s.equals(l)||s.startsWith(l))return n;}
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo r=find(n.getChild(i),labels,exact);if(r!=null)return r;}
        return null;
    }

    public static boolean enabled(Context c){return Prefs.get(c).b("force_stop_auto",true)&&available();}
}
