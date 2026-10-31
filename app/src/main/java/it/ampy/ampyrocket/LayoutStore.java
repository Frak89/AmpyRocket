package it.ampy.ampyrocket;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

/** Home layout: pages (widgets on top + app grid), dock and folders. Persisted as JSON. */
public final class LayoutStore {
    public static final class Page {
        public final List<String> widgets=new ArrayList<>();  // "clock","hud","aw:<id>:<rows>"
        public final List<String> items=new ArrayList<>();    // app key or "folder:<id>"
    }
    public static final class Folder {public String id,name;public final List<String> apps=new ArrayList<>();}

    public final List<Page> pages=new ArrayList<>();
    public final List<String> dock=new ArrayList<>();
    public final Map<String,Folder> folders=new LinkedHashMap<>();
    public final Set<String> known=new HashSet<>();
    private final Context ctx;

    private LayoutStore(Context c){ctx=c.getApplicationContext();}

    public static LayoutStore load(Context c){
        LayoutStore s=new LayoutStore(c);
        String json=Prefs.get(c).s("layout_json",null);
        if(json!=null){
            try{JSONObject o=new JSONObject(json);
                JSONArray ps=o.getJSONArray("pages");
                for(int i=0;i<ps.length();i++){JSONObject po=ps.getJSONObject(i);Page p=new Page();
                    arr(po.optJSONArray("widgets"),p.widgets);arr(po.optJSONArray("items"),p.items);s.pages.add(p);}
                arr(o.optJSONArray("dock"),s.dock);
                JSONArray fs=o.optJSONArray("folders");
                if(fs!=null)for(int i=0;i<fs.length();i++){JSONObject fo=fs.getJSONObject(i);Folder f=new Folder();
                    f.id=fo.getString("id");f.name=fo.optString("name","CARTELLA");arr(fo.optJSONArray("apps"),f.apps);s.folders.put(f.id,f);}
                List<String> k=new ArrayList<>();arr(o.optJSONArray("known"),k);s.known.addAll(k);
            }catch(Exception e){s.pages.clear();s.dock.clear();s.folders.clear();}
        }
        if(s.pages.isEmpty())s.defaults();
        return s;
    }
    private static void arr(JSONArray a,List<String> out){if(a==null)return;for(int i=0;i<a.length();i++)out.add(a.optString(i));}

    public void save(){
        try{JSONObject o=new JSONObject();JSONArray ps=new JSONArray();
            for(Page p:pages){JSONObject po=new JSONObject();po.put("widgets",new JSONArray(p.widgets));po.put("items",new JSONArray(p.items));ps.put(po);}
            o.put("pages",ps);o.put("dock",new JSONArray(dock));
            JSONArray fs=new JSONArray();
            for(Folder f:folders.values()){JSONObject fo=new JSONObject();fo.put("id",f.id);fo.put("name",f.name);fo.put("apps",new JSONArray(f.apps));fs.put(fo);}
            o.put("folders",fs);o.put("known",new JSONArray(known));
            Prefs.get(ctx).put("layout_json",o.toString());
        }catch(Exception ignored){}
    }
    public String export(){save();return Prefs.get(ctx).s("layout_json","{}");}
    public static boolean importJson(Context c,String json){
        try{new JSONObject(json).getJSONArray("pages");Prefs.get(c).put("layout_json",json);return true;}catch(Exception e){return false;}
    }

    private void defaults(){
        AppRepository repo=AppRepository.get(ctx);
        Page p0=new Page();p0.widgets.add("clock");p0.widgets.add("hud");pages.add(p0);
        Page p1=new Page();pages.add(p1);
        String[] dockIntents={"dial","sms","browser","camera","settings"};
        for(String k:dockIntents){String key=resolve(k,repo);if(key!=null&&!dock.contains(key))dock.add(key);}
        String[] pref={"com.google.android.apps.photos","com.google.android.gm","com.google.android.youtube",
            "com.google.android.apps.maps","com.android.vending","com.google.android.calendar",
            "com.google.android.deskclock","com.whatsapp","org.telegram.messenger","com.spotify.music",
            "com.instagram.android","com.google.android.apps.messaging","com.android.gallery3d",
            "com.miui.gallery","com.sec.android.gallery3d"};
        for(String pk:pref){AppRepository.App a=repo.findPkg(pk);
            if(a!=null&&!dock.contains(a.key)&&p0.items.size()<8)p0.items.add(a.key);}
        for(AppRepository.App a:repo.visible()){
            if(p0.items.size()>=4)break;if(!dock.contains(a.key)&&!p0.items.contains(a.key))p0.items.add(a.key);}
        for(AppRepository.App a:repo.all())known.add(a.key);
        save();
    }

    private String resolve(String what,AppRepository repo){
        Intent i;
        switch(what){
            case "dial":i=new Intent(Intent.ACTION_DIAL);break;
            case "sms":i=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"));break;
            case "browser":i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com"));break;
            case "camera":i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);break;
            default:i=new Intent(android.provider.Settings.ACTION_SETTINGS);
        }
        try{ResolveInfo r=ctx.getPackageManager().resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY);
            if(r!=null&&r.activityInfo!=null&&!"android".equals(r.activityInfo.packageName)){
                AppRepository.App a=repo.findPkg(r.activityInfo.packageName);if(a!=null)return a.key;}
            List<ResolveInfo> all=ctx.getPackageManager().queryIntentActivities(i,0);
            for(ResolveInfo ri:all){AppRepository.App a=repo.findPkg(ri.activityInfo.packageName);if(a!=null)return a.key;}
        }catch(Throwable ignored){}
        return null;
    }

    /** Drops uninstalled apps, adds newly installed ones (if enabled). Returns true when changed. */
    public boolean sync(List<AppRepository.App> apps,boolean autoAdd,int perPage){
        Set<String> live=new HashSet<>();for(AppRepository.App a:apps)live.add(a.key);
        boolean ch=false;
        for(Page p:pages)ch|=p.items.removeIf(k->!k.startsWith("folder:")&&!live.contains(k));
        ch|=dock.removeIf(k->!k.startsWith("folder:")&&!live.contains(k));
        for(Folder f:folders.values())ch|=f.apps.removeIf(k->!live.contains(k));
        if(known.isEmpty()){known.addAll(live);ch=true;}
        else for(AppRepository.App a:apps){
            if(known.add(a.key)){ch=true;
                if(autoAdd&&!contains(a.key)){
                    Page target=pages.get(pages.size()-1);
                    if(target.items.size()>=capacity(target,perPage)){target=new Page();pages.add(target);}
                    target.items.add(a.key);
                }
            }
        }
        if(ch)save();return ch;
    }
    public int capacity(Page p,int perPage){return Math.max(0,perPage-widgetCells(p));}
    public int widgetCells(Page p){
        int rows=0;for(String w:p.widgets)rows+=widgetRows(w);return rows*Prefs.get(ctx).columns();
    }
    public static int widgetRows(String w){
        if("clock".equals(w))return 2;if("hud".equals(w))return 1;
        if(w.startsWith("aw:")){try{return Integer.parseInt(w.split(":")[2]);}catch(Exception e){return 2;}}
        return 1;
    }
    public boolean contains(String key){
        if(dock.contains(key))return true;for(Page p:pages)if(p.items.contains(key))return true;
        for(Folder f:folders.values())if(f.apps.contains(key))return true;return false;
    }
    public void removeEverywhere(String key){for(Page p:pages)p.items.remove(key);dock.remove(key);}
    public Folder newFolder(String name){Folder f=new Folder();f.id=Long.toString(System.currentTimeMillis(),36);f.name=name;folders.put(f.id,f);return f;}
    public void deleteFolder(String id){
        Folder f=folders.remove(id);if(f==null)return;
        String fk="folder:"+id;
        for(Page p:pages){int i=p.items.indexOf(fk);if(i>=0){p.items.remove(i);p.items.addAll(i,f.apps);}}
        int d=dock.indexOf(fk);if(d>=0){dock.remove(d);}
    }
}
