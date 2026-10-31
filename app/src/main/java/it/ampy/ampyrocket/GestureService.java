package it.ampy.ampyrocket;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

/** Optional: lets launcher gestures lock the screen / open the shade. Reads no screen content. */
public final class GestureService extends AccessibilityService {
    private static GestureService live;
    @Override protected void onServiceConnected(){live=this;}
    @Override public boolean onUnbind(android.content.Intent i){live=null;return super.onUnbind(i);}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){}

    public static boolean available(){return live!=null;}
    public static boolean lock(){return live!=null&&live.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);}
    public static boolean notifications(){return live!=null&&live.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);}
    public static boolean quickSettings(){return live!=null&&live.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);}
    public static boolean recents(){return live!=null&&live.performGlobalAction(GLOBAL_ACTION_RECENTS);}
}
