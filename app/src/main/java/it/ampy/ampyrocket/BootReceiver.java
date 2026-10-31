package it.ampy.ampyrocket;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restarts the lockscreen service after boot / update when enabled. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        String a=i.getAction();
        if((Intent.ACTION_BOOT_COMPLETED.equals(a)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(a))&&Prefs.get(c).lockEnabled())LockService.start(c);
    }
}
