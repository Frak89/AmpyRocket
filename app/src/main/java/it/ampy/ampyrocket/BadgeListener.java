package it.ampy.ampyrocket;

import android.app.Notification;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.*;

/** Notification dots for icons + the notification feed of the custom lockscreen. */
public final class BadgeListener extends NotificationListenerService {
    public static final class Item {public String pkg,title,text,key;public long when;}
    public interface Listener{void onBadgesChanged();}

    private static final Map<String,Integer> counts=new HashMap<>();
    private static final List<Item> feed=new ArrayList<>();
    private static final List<Listener> listeners=new ArrayList<>();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static BadgeListener live;

    public static synchronized int count(String pkg){Integer n=counts.get(pkg);return n==null?0:n;}
    public static synchronized List<Item> feed(){return new ArrayList<>(feed);}
    public static synchronized int total(){int t=0;for(int v:counts.values())t+=v;return t;}
    public static void addListener(Listener l){synchronized(listeners){if(!listeners.contains(l))listeners.add(l);}}
    public static void removeListener(Listener l){synchronized(listeners){listeners.remove(l);}}
    public static void dismiss(String key){BadgeListener b=live;if(b!=null)try{b.cancelNotification(key);}catch(Throwable ignored){}}

    @Override public void onListenerConnected(){live=this;rebuild();}
    @Override public void onListenerDisconnected(){live=null;}
    @Override public void onNotificationPosted(StatusBarNotification sbn){rebuild();}
    @Override public void onNotificationRemoved(StatusBarNotification sbn){rebuild();}

    private void rebuild(){
        StatusBarNotification[] all;
        try{all=getActiveNotifications();}catch(Throwable t){return;}
        Map<String,Integer> c=new HashMap<>();List<Item> f=new ArrayList<>();
        if(all!=null)for(StatusBarNotification s:all){
            Notification n=s.getNotification();
            if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)continue;
            if(!s.isClearable()&&(n.flags&Notification.FLAG_ONGOING_EVENT)!=0)continue;
            int num=n.number>0?n.number:1;
            Integer cur=c.get(s.getPackageName());c.put(s.getPackageName(),(cur==null?0:cur)+num);
            Item it=new Item();it.pkg=s.getPackageName();it.key=s.getKey();it.when=s.getPostTime();
            CharSequence t=n.extras.getCharSequence(Notification.EXTRA_TITLE),x=n.extras.getCharSequence(Notification.EXTRA_TEXT);
            it.title=t==null?"":t.toString();it.text=x==null?"":x.toString();f.add(it);
        }
        Collections.sort(f,(a,b)->Long.compare(b.when,a.when));
        synchronized(BadgeListener.class){counts.clear();counts.putAll(c);feed.clear();feed.addAll(f);}
        main.post(()->{List<Listener> ls;synchronized(listeners){ls=new ArrayList<>(listeners);}for(Listener l:ls)l.onBadgesChanged();});
    }
}
