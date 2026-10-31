package it.ampy.ampyrocket;

import android.app.AppOpsManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

/** Status + shortcuts for the special permissions AmpyRocket can use. */
public final class Perms {
    private Perms(){}

    public static boolean usageAccess(Context c){
        try{AppOpsManager ops=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);
            int mode=Build.VERSION.SDK_INT>=29?ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),c.getPackageName())
                :ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),c.getPackageName());
            if(mode==AppOpsManager.MODE_DEFAULT)
                return c.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)==PackageManager.PERMISSION_GRANTED;
            return mode==AppOpsManager.MODE_ALLOWED;}catch(Throwable t){return false;}
    }
    public static boolean notificationAccess(Context c){
        String s=Settings.Secure.getString(c.getContentResolver(),"enabled_notification_listeners");
        return s!=null&&s.contains(new ComponentName(c,BadgeListener.class).flattenToString());
    }
    public static boolean accessibility(Context c){
        String s=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s!=null&&s.contains(c.getPackageName()+"/");
    }
    public static boolean overlay(Context c){return Settings.canDrawOverlays(c);}
    public static boolean notifications(Context c){
        return Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
    }
    public static boolean isDefaultLauncher(Context c){
        Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo r=c.getPackageManager().resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY);
        return r!=null&&r.activityInfo!=null&&c.getPackageName().equals(r.activityInfo.packageName);
    }

    public static Intent usageIntent(Context c){
        Intent i=new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        i.setData(Uri.parse("package:"+c.getPackageName()));return i;
    }
    public static Intent notificationIntent(){return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);}
    public static Intent accessibilityIntent(){return new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);}
    public static Intent overlayIntent(Context c){
        return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+c.getPackageName()));
    }
    public static Intent homeRoleIntent(Context c){
        if(Build.VERSION.SDK_INT>=29){
            RoleManager rm=c.getSystemService(RoleManager.class);
            if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_HOME))return rm.createRequestRoleIntent(RoleManager.ROLE_HOME);
        }
        return new Intent(Settings.ACTION_HOME_SETTINGS);
    }
    public static void open(Context c,Intent i){
        try{c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(Throwable t){try{c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Throwable ignored){}}
    }
}
