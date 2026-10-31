package it.ampy.ampyrocket;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Every user-tunable switch of AmpyRocket lives here. */
public final class Prefs {
    public static final String FILE="ampyrocket_settings";

    // Home page transitions
    public static final String[] PAGE_FX={"cube","flip","stack","carousel","rotate","zoom","glitch","tablet","none"};
    public static final String[] PAGE_FX_NAMES={"CUBO 3D (Compiz)","FLIP CARD","STACK / DECK","CAROUSEL",
        "ROTAZIONE","ZOOM IN/OUT","GLITCH SLICE","TABLET / ACCORDION","NESSUNO"};
    // Task drawer card layouts
    public static final String[] DECK_FX={"flip3d","stack","coverflow","cube","helix","expose"};
    public static final String[] DECK_FX_NAMES={"FLIP 3D (Windows Vista)","STACK PROFONDITÀ (Linux)","COVER FLOW",
        "CUBO ROTANTE (Compiz)","ELICA / HELIX","EXPOSÉ GRIGLIA"};
    // Card close animation
    public static final String[] CLOSE_FX={"genie","glitch","shrink","burn"};
    public static final String[] CLOSE_FX_NAMES={"GENIE / LAMPADA MAGICA","GLITCH DISSOLVE","RIDUCI E SFUMA","PIXEL BURN"};
    // App launch animation
    public static final String[] LAUNCH_FX={"genie","reveal","scale","none"};
    public static final String[] LAUNCH_FX_NAMES={"NEON GENIE MORPH","CLIP REVEAL","SCALE UP","STANDARD DI SISTEMA"};
    // Animated backgrounds
    public static final String[] BG={"grid","rain","particles","hex","starfield","wallpaper"};
    public static final String[] BG_NAMES={"GRIGLIA SYNTHWAVE","PIOGGIA DIGITALE","RETE DI PARTICELLE",
        "ALVEARE ESAGONALE","WARP / STARFIELD","SOLO SFONDO DI SISTEMA"};
    // Gestures
    public static final String[] ACTIONS={"none","drawer","tasks","notifications","search","lock","settings","quicksettings","recents"};
    public static final String[] ACTION_NAMES={"NESSUNA","APRI DRAWER APP","TASK DRAWER / MULTITASKING",
        "TENDINA NOTIFICHE","CERCA","BLOCCA SCHERMO","IMPOSTAZIONI LAUNCHER","IMPOSTAZIONI RAPIDE","RECENTI DI SISTEMA"};

    private final SharedPreferences sp;
    private Prefs(Context c){sp=c.getApplicationContext().getSharedPreferences(FILE,Context.MODE_PRIVATE);}
    public static Prefs get(Context c){return new Prefs(c);}
    public SharedPreferences raw(){return sp;}

    public String s(String k,String d){return sp.getString(k,d);}
    public int i(String k,int d){return sp.getInt(k,d);}
    public boolean b(String k,boolean d){return sp.getBoolean(k,d);}
    public float f(String k,float d){return sp.getFloat(k,d);}
    public void put(String k,Object v){
        SharedPreferences.Editor e=sp.edit();
        if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);
        else if(v instanceof Integer)e.putInt(k,(Integer)v);
        else if(v instanceof Float)e.putFloat(k,(Float)v);
        else if(v instanceof Set){
            @SuppressWarnings("unchecked") Set<String> s=(Set<String>)v;e.putStringSet(k,new HashSet<>(s));
        }
        else e.putString(k,v==null?null:String.valueOf(v));
        e.apply();
    }

    public String theme(){return s("theme","cyberpunk");}
    public int columns(){return i("columns",5);}
    public int rows(){return i("rows",6);}
    public int dockCount(){return i("dock_count",5);}
    public float iconScale(){return f("icon_scale",1f);}
    public boolean labels(){return b("labels",true);}
    public String pageFx(){return s("page_fx","cube");}
    public String deckFx(){return s("deck_fx","flip3d");}
    public String closeFx(){return s("close_fx","genie");}
    public String launchFx(){return s("launch_fx","genie");}
    public String background(){return s("background","grid");}
    public boolean scanlines(){return b("scanlines",true);}
    public int wallpaperDim(){return i("wallpaper_dim",45);}
    public int glitch(){return i("glitch_level",2);}
    public boolean haptics(){return b("haptics",true);}
    public boolean ecoFps(){return b("eco_fps",false);}
    public boolean showClock(){return b("show_clock",true);}
    public boolean showHud(){return b("show_hud",true);}
    public boolean showSearch(){return b("show_search",true);}
    public boolean badges(){return b("badges",true);}
    public boolean lockEnabled(){return b("lock_enabled",false);}
    public boolean lockSeconds(){return b("lock_seconds",true);}
    public String drawerSort(){return s("drawer_sort","az");}
    public boolean wobbly(){return b("wobbly",true);}
    public String gesture(String which,String def){return s("gesture_"+which,def);}
    public Set<String> hidden(){return new HashSet<>(sp.getStringSet("hidden",new HashSet<>()));}
    public void setHidden(Set<String> s){put("hidden",s);}
    public String rename(String key){return sp.getString("rename_"+key,null);}
    public void setRename(String key,String name){
        if(name==null||name.trim().isEmpty())sp.edit().remove("rename_"+key).apply();
        else put("rename_"+key,name.trim());
    }
}
