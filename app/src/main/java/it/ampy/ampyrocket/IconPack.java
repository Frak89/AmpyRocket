package it.ampy.ampyrocket;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.*;

/** Standard icon packs (ADW / Nova / Apex format: appfilter.xml). */
public final class IconPack {
    private static final String[] ACTIONS={"org.adw.launcher.THEMES","com.novalauncher.THEME","com.gau.go.launcherex.theme",
        "com.teslacoilsw.launcher.THEME","com.anddoes.launcher.THEME"};
    private static String loadedPkg;private static Resources res;private static final Map<String,String> map=new HashMap<>();

    /** Installed icon packs: package → label. */
    public static LinkedHashMap<String,String> installed(Context c){
        PackageManager pm=c.getPackageManager();TreeMap<String,String> byLabel=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for(String a:ACTIONS){
            List<ResolveInfo> l;try{l=pm.queryIntentActivities(new Intent(a),PackageManager.GET_META_DATA);}catch(Throwable t){continue;}
            for(ResolveInfo r:l){String pkg=r.activityInfo.packageName;
                try{byLabel.put(String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg,0)))+"\u0000"+pkg,pkg);}catch(Throwable ignored){}}
        }
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        for(Map.Entry<String,String> e:byLabel.entrySet())out.put(e.getValue(),e.getKey().substring(0,e.getKey().indexOf('\u0000')));
        return out;
    }

    /** Icon from the selected pack, or null (keep the app's own icon). */
    public static synchronized Drawable icon(Context c,ComponentName cn){
        String pack=Prefs.get(c).s("icon_pack","");
        if(pack.isEmpty())return null;
        if(!pack.equals(loadedPkg))load(c,pack);
        if(res==null)return null;
        String name=map.get(cn.getPackageName()+"/"+cn.getClassName());
        if(name==null)name=map.get(cn.getPackageName());
        if(name==null)return null;
        try{int id=res.getIdentifier(name,"drawable",pack);if(id==0)return null;return res.getDrawable(id,null);}catch(Throwable t){return null;}
    }

    private static void load(Context c,String pack){
        loadedPkg=pack;map.clear();res=null;
        try{
            res=c.getPackageManager().getResourcesForApplication(pack);
            XmlPullParser xp;InputStream in=null;
            int id=res.getIdentifier("appfilter","xml",pack);
            if(id!=0)xp=res.getXml(id);
            else{in=res.getAssets().open("appfilter.xml");xp=XmlPullParserFactory.newInstance().newPullParser();xp.setInput(in,"UTF-8");}
            int ev;
            while((ev=xp.next())!=XmlPullParser.END_DOCUMENT){
                if(ev!=XmlPullParser.START_TAG||!"item".equals(xp.getName()))continue;
                String comp=xp.getAttributeValue(null,"component"),dr=xp.getAttributeValue(null,"drawable");
                if(comp==null||dr==null)continue;
                int a=comp.indexOf('{'),b=comp.indexOf('}');if(a<0||b<a)continue;
                String inner=comp.substring(a+1,b);int slash=inner.indexOf('/');if(slash<0)continue;
                String pkg=inner.substring(0,slash),cls=inner.substring(slash+1);
                if(cls.startsWith("."))cls=pkg+cls;
                map.put(pkg+"/"+cls,dr);if(!map.containsKey(pkg))map.put(pkg,dr);
            }
            if(in!=null)in.close();
        }catch(Throwable t){res=null;}
    }
    public static synchronized void reset(){loadedPkg=null;}
}
