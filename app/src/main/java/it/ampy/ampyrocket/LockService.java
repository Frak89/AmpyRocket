package it.ampy.ampyrocket;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

/** Keeps a screen-on/off receiver alive and shows the AmpyRocket lockscreen cover. */
public final class LockService extends Service {
    private static final String CH="ampyrocket_lock";
    private final BroadcastReceiver rx=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        if(!Prefs.get(c).lockEnabled())return;
        if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())&&Settings.canDrawOverlays(c)){
            try{c.startActivity(new Intent(c,LockScreenActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION|Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));}
            catch(Throwable ignored){}
        }
    }};

    public static void start(Context c){
        try{Intent i=new Intent(c,LockService.class);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}catch(Throwable ignored){}
    }
    public static void stop(Context c){try{c.stopService(new Intent(c,LockService.class));}catch(Throwable ignored){}}

    @Override public void onCreate(){
        super.onCreate();
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CH,"Lockscreen AmpyRocket",NotificationManager.IMPORTANCE_MIN));
        Notification n=new Notification.Builder(this,CH).setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("AmpyRocket lockscreen attivo").setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,SettingsActivity.class),PendingIntent.FLAG_IMMUTABLE)).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(77,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(77,n);
        IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(rx,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(rx,f);
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(!Prefs.get(this).lockEnabled()){stopSelf();return START_NOT_STICKY;}return START_STICKY;
    }
    @Override public void onDestroy(){try{unregisterReceiver(rx);}catch(Throwable ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
