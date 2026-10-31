package it.ampy.ampyrocket;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.LruCache;

import java.text.Collator;
import java.util.*;

/** All launchable activities of every profile (personal + work), with icons, renames and stats. */
public final class AppRepository {
    public static final class App {
        public ComponentName comp;public UserHandle user;public String label,original,key;public boolean work;
        public Drawable icon;public long installed;
        public String pkg(){return comp.getPackageName();}
    }
    public interface Listener{void onAppsChanged();}

    private static AppRepository inst;
    public static synchronized AppRepository get(Context c){if(inst==null)inst=new AppRepository(c.getApplicationContext());return inst;}

    private final Context ctx;private final LauncherApps la;private final UserManager um;
    private final SharedPreferences stats;
    private final List<App> apps=new ArrayList<>();
    private final Map<String,App> byKey=new HashMap<>();
    private final List<Listener> listeners=new ArrayList<>();
    private final LruCache<String,Bitmap> iconCache=new LruCache<>(260);
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean loaded=false;

    private AppRepository(Context c){
        ctx=c;la=(LauncherApps)c.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        um=(UserManager)c.getSystemService(Context.USER_SERVICE);
        stats=c.getSharedPreferences("ampyrocket_stats",Context.MODE_PRIVATE);
        la.registerCallback(new LauncherApps.Callback(){
            @Override public void onPackageRemoved(String p,UserHandle u){reloadAsync();}
            @Override public void onPackageAdded(String p,UserHandle u){reloadAsync();}
            @Override public void onPackageChanged(String p,UserHandle u){reloadAsync();}
            @Override public void onPackagesAvailable(String[] p,UserHandle u,boolean r){reloadAsync();}
            @Override public void onPackagesUnavailable(String[] p,UserHandle u,boolean r){reloadAsync();}
        },main);
    }

    public void addListener(Listener l){if(!listeners.contains(l))listeners.add(l);}
    public void removeListener(Listener l){listeners.remove(l);}

    public synchronized List<App> all(){if(!loaded)load();return new ArrayList<>(apps);}
    public synchronized App find(String key){if(!loaded)load();return byKey.get(key);}
    public synchronized App findPkg(String pkg){
        if(!loaded)load();
        for(App a:apps)if(a.pkg().equals(pkg)&&!a.work)return a;
        for(App a:apps)if(a.pkg().equals(pkg))return a;
        return null;
    }

    public List<App> visible(){
        Set<String> hidden=Prefs.get(ctx).hidden();List<App> out=new ArrayList<>();
        for(App a:all())if(!hidden.contains(a.key))out.add(a);return out;
    }

    public void reloadAsync(){
        new Thread(()->{synchronized(AppRepository.this){load();}
            main.post(()->{for(Listener l:new ArrayList<>(listeners))l.onAppsChanged();});},"apps-reload").start();
    }

    private void load(){
        List<App> list=new ArrayList<>();Map<String,App> map=new HashMap<>();
        Prefs pr=Prefs.get(ctx);
        List<UserHandle> users=um.getUserProfiles();UserHandle me=android.os.Process.myUserHandle();
        for(UserHandle u:users){
            List<LauncherActivityInfo> infos;
            try{infos=la.getActivityList(null,u);}catch(Throwable t){continue;}
            for(LauncherActivityInfo i:infos){
                App a=new App();a.comp=i.getComponentName();a.user=u;a.work=!u.equals(me);
                a.original=String.valueOf(i.getLabel());
                a.key=a.comp.flattenToShortString()+"#"+um.getSerialNumberForUser(u);
                String rn=pr.rename(a.key);a.label=rn!=null?rn:a.original;
                try{a.icon=i.getBadgedIcon(0);}catch(Throwable t){a.icon=ctx.getPackageManager().getDefaultActivityIcon();}
                Drawable packed=IconPack.icon(ctx,a.comp);
                if(packed!=null)a.icon=a.work?ctx.getPackageManager().getUserBadgedIcon(packed,u):packed;
                a.installed=i.getFirstInstallTime();
                if(a.comp.getPackageName().equals(ctx.getPackageName())&&!a.comp.getClassName().endsWith("HomeActivity"))continue;
                list.add(a);map.put(a.key,a);
            }
        }
        Collator col=Collator.getInstance();col.setStrength(Collator.PRIMARY);
        Collections.sort(list,(x,y)->col.compare(x.label,y.label));
        apps.clear();apps.addAll(list);byKey.clear();byKey.putAll(map);iconCache.evictAll();loaded=true;
    }

    // ----- statistics (most used / recent) -----
    public void recordLaunch(App a){
        stats.edit().putInt("n_"+a.key,launches(a)+1).putLong("t_"+a.key,System.currentTimeMillis()).apply();
    }
    public int launches(App a){return stats.getInt("n_"+a.key,0);}
    public long lastLaunch(App a){return stats.getLong("t_"+a.key,0);}

    // ----- actions -----
    public boolean launch(App a,Rect bounds,Bundle opts){
        try{la.startMainActivity(a.comp,a.user,bounds,opts);recordLaunch(a);return true;}
        catch(Throwable t){
            try{Intent i=ctx.getPackageManager().getLaunchIntentForPackage(a.pkg());
                if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);ctx.startActivity(i,opts);recordLaunch(a);return true;}}
            catch(Throwable ignored){}
            return false;
        }
    }
    public boolean launchPackage(String pkg,Bundle opts){
        App a=findPkg(pkg);if(a!=null)return launch(a,null,opts);
        try{Intent i=ctx.getPackageManager().getLaunchIntentForPackage(pkg);
            if(i==null)return false;i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);ctx.startActivity(i,opts);return true;}
        catch(Throwable t){return false;}
    }
    public void appInfo(App a){try{la.startAppDetailsActivity(a.comp,a.user,null,null);}catch(Throwable ignored){}}
    public void appInfo(String pkg){
        try{ctx.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:"+pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Throwable ignored){}
    }
    public void uninstall(String pkg){
        try{ctx.startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(Throwable ignored){}
    }
    public boolean isSystem(String pkg){
        try{return (ctx.getPackageManager().getApplicationInfo(pkg,0).flags&ApplicationInfo.FLAG_SYSTEM)!=0;}
        catch(Throwable t){return true;}
    }

    public boolean canShortcuts(){try{return la.hasShortcutHostPermission();}catch(Throwable t){return false;}}
    public List<ShortcutInfo> shortcuts(App a){
        List<ShortcutInfo> out=new ArrayList<>();if(!canShortcuts())return out;
        try{LauncherApps.ShortcutQuery q=new LauncherApps.ShortcutQuery();q.setPackage(a.pkg());q.setActivity(a.comp);
            q.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC|LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST|
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
            List<ShortcutInfo> r=la.getShortcuts(q,a.user);if(r!=null)out.addAll(r);}catch(Throwable ignored){}
        Collections.sort(out,(x,y)->Integer.compare(x.getRank(),y.getRank()));
        return out.size()>5?out.subList(0,5):out;
    }
    public void startShortcut(ShortcutInfo s){
        try{la.startShortcut(s,null,null);}catch(Throwable t){Ui.toast(ctx,"Scorciatoia non disponibile");}
    }
    public Drawable shortcutIcon(ShortcutInfo s){
        try{return la.getShortcutIconDrawable(s,ctx.getResources().getDisplayMetrics().densityDpi);}catch(Throwable t){return null;}
    }

    public String labelFor(String pkg){
        App a=findPkg(pkg);if(a!=null)return a.label;
        try{PackageManager pm=ctx.getPackageManager();return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)));}
        catch(Throwable t){return pkg;}
    }
    public Drawable iconFor(String pkg){
        App a=findPkg(pkg);if(a!=null)return a.icon;
        try{return ctx.getPackageManager().getApplicationIcon(pkg);}catch(Throwable t){return ctx.getPackageManager().getDefaultActivityIcon();}
    }

    /** Neon-framed icon bitmap, cached. */
    public Bitmap framed(String cacheKey,Drawable d,int size,Theme th,String style){
        String k=cacheKey+"@"+size+style+th.key;Bitmap b=iconCache.get(k);
        if(b==null){b=IconFactory.render(d,size,th,style);iconCache.put(k,b);}
        return b;
    }
    public void clearIconCache(){iconCache.evictAll();}
}
