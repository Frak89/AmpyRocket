package it.ampy.ampyrocket;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.widget.RemoteViews;

/** Standalone clock widget (usable on any launcher). */
public final class ClockAppWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
        int layout=c.getResources().getIdentifier("widget_clock","layout",c.getPackageName());
        int root=c.getResources().getIdentifier("widget_root","id",c.getPackageName());
        PendingIntent pi=PendingIntent.getActivity(c,0,new Intent(AlarmClock.ACTION_SHOW_ALARMS),PendingIntent.FLAG_IMMUTABLE);
        for(int id:ids){RemoteViews v=new RemoteViews(c.getPackageName(),layout);v.setOnClickPendingIntent(root,pi);m.updateAppWidget(id,v);}
    }
}
