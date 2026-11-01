package it.ampy.ampyrocket;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.*;

/** AmpyRocket control deck: look, effects, gestures, lockscreen, permissions, backup. */
public final class SettingsActivity extends Activity {
    private Prefs pr;private Theme th;private LinearLayout list;
    private static final int REQ_EXPORT=31,REQ_IMPORT=32,REQ_HOME=33;

    @Override protected void onCreate(Bundle b){super.onCreate(b);pr=Prefs.get(this);build();}
    @Override protected void onResume(){super.onResume();if(list!=null)build();}

    private void build(){
        th=Theme.load(this);
        getWindow().setStatusBarColor(th.bg);getWindow().setNavigationBarColor(th.bg);
        FrameLayout root=new FrameLayout(this);
        CyberBackgroundView bg=new CyberBackgroundView(this);root.addView(bg);
        LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);root.addView(col);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(Ui.dp(14),Ui.dp(14),Ui.dp(14),Ui.dp(10));
        TextView back=Ui.text(this,"‹ ",20,th.accent,true);back.setOnClickListener(v->finish());head.addView(back);
        GlitchTextView t=new GlitchTextView(this,th,16,th.fg,true);t.alignLeft();t.setText("⚙ AMPYROCKET // CONTROL DECK");
        head.addView(t,new LinearLayout.LayoutParams(0,-2,1));col.addView(head);
        ScrollView sv=new ScrollView(this);col.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(Ui.dp(14),0,Ui.dp(14),Ui.dp(40));sv.addView(list);
        setContentView(root);

        section("00 / STATO E PERMESSI");
        perm("Launcher predefinito",Perms.isDefaultLauncher(this),()->{
            try{startActivityForResult(Perms.homeRoleIntent(this),REQ_HOME);}catch(Throwable e){Perms.open(this,new Intent(android.provider.Settings.ACTION_HOME_SETTINGS));}});
        perm("Accesso dati di utilizzo (task drawer)",Perms.usageAccess(this),()->Perms.open(this,Perms.usageIntent(this)));
        perm("Accesso notifiche (badge + lockscreen)",Perms.notificationAccess(this),()->Perms.open(this,Perms.notificationIntent()));
        perm("Accessibilità (blocca schermo, tendina)",Perms.accessibility(this),()->Perms.open(this,Perms.accessibilityIntent()));
        perm("Sovrapposizione (lockscreen custom)",Perms.overlay(this),()->Perms.open(this,Perms.overlayIntent(this)));
        if(Build.VERSION.SDK_INT>=33)perm("Notifiche (servizio lockscreen)",Perms.notifications(this),()->requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},5));

        section("01 / LOOK");
        choice("Tema",Theme.NAMES,Theme.KEYS,"theme","cyberpunk");
        choice("Sfondo animato",Prefs.BG_NAMES,Prefs.BG,"background","grid");
        toggle("Scanlines CRT","scanlines",true);
        intChoice("Oscuramento sfondo di sistema",new String[]{"0%","20%","35%","45%","60%","75%","90%"},new int[]{0,20,35,45,60,75,90},"wallpaper_dim",45);
        choice("Stile icone",IconFactory.STYLE_NAMES,IconFactory.STYLES,"icon_style","neon");
        LinkedHashMap<String,String> packs=IconPack.installed(this);
        String[] pk=new String[packs.size()+1],pn=new String[packs.size()+1];pk[0]="";pn[0]="ICONE ORIGINALI";
        int pi=1;for(Map.Entry<String,String> e:packs.entrySet()){pk[pi]=e.getKey();pn[pi]=e.getValue().toUpperCase(Locale.ROOT);pi++;}
        int psel=Math.max(0,Arrays.asList(pk).indexOf(pr.s("icon_pack","")));
        row("Pacchetto icone",packs.isEmpty()?"Nessun pacchetto installato (formato ADW/Nova)":null,pn[psel],th.accent,()->
            CyberMenu.choose(this,th,"Pacchetto icone",pn,psel,i->{pr.put("icon_pack",pk[i]);IconPack.reset();
                AppRepository.get(this).reloadAsync();pr.put("layout_rev",pr.i("layout_rev",0)+1);build();}));
        floatChoice("Dimensione icone",new String[]{"80%","90%","100%","110%","120%"},new float[]{.8f,.9f,1f,1.1f,1.2f},"icon_scale",1f);
        intChoice("Colonne griglia",new String[]{"4","5","6"},new int[]{4,5,6},"columns",5);
        intChoice("Righe griglia",new String[]{"5","6","7"},new int[]{5,6,7},"rows",6);
        intChoice("Icone nel dock",new String[]{"3","4","5","6"},new int[]{3,4,5,6},"dock_count",5);
        toggle("Etichette sotto le icone","labels",true);
        toggle("Barra di ricerca in alto","show_search",true);
        toggle("Badge notifiche sulle icone","badges",true);

        section("02 / VETRO LIQUIDO & BLINK");
        choice("Stile menu e finestre",new String[]{"VETRO LIQUIDO (iOS)","PANNELLO NEON"},new String[]{"glass","neon"},"menu_style","glass");
        toggle("Vetro anche su dock e barra di ricerca","glass_bars",true);
        choice("Blink pulsanti",new String[]{"SELEZIONATI + TOCCO","SOLO AL TOCCO","SPENTO"},new String[]{"selected","tap","off"},"blink_mode","selected");
        intChoice("Velocità blink",Blink.PERIOD_NAMES,Blink.PERIODS,"blink_period_ms",250);

        section("03 / BATTERIA");
        intChoice("Modalità energia",Power.NAMES,new int[]{Power.PERF,Power.BAL,Power.MAX},"power_mode",Power.BAL);
        intChoice("Bilanciato: animazioni attive dopo un tocco per",new String[]{"4 secondi","8 secondi","15 secondi","30 secondi"},new int[]{4,8,15,30},"anim_idle_s",8);
        toggle("Risparmio massimo con il risparmio energetico di sistema","follow_saver",true);

        section("04 / EFFETTI");
        intChoice("Intensità glitch",new String[]{"OFF","LEGGERO","MEDIO","FORTE"},new int[]{0,1,2,3},"glitch_level",2);
        choice("Transizione pagine",Prefs.PAGE_FX_NAMES,Prefs.PAGE_FX,"page_fx","cube");
        choice("Layout card multitasking",Prefs.DECK_FX_NAMES,Prefs.DECK_FX,"deck_fx","flip3d");
        choice("Chiusura card",Prefs.CLOSE_FX_NAMES,Prefs.CLOSE_FX,"close_fx","genie");
        choice("Animazione apertura app",Prefs.LAUNCH_FX_NAMES,Prefs.LAUNCH_FX,"launch_fx","genie");
        toggle("Wobbly cards (molla Compiz)","wobbly",true);
        toggle("Prestazioni: limita a 30 fps","eco_fps",false);
        toggle("Vibrazione al tocco","haptics",true);

        section("05 / TASK MANAGER");
        toggle("Chiusura reale con Forza arresto automatico (richiede Accessibilità)","force_stop_auto",true);
        toggle("Mostra processi e servizi di sistema","task_system",false);
        toggle("Mostra anche app usate di recente (3 ore)","task_recent",false);

        section("06 / GESTI");
        choice("Scorri in su",Prefs.ACTION_NAMES,Prefs.ACTIONS,"gesture_up","drawer");
        choice("Scorri in giù",Prefs.ACTION_NAMES,Prefs.ACTIONS,"gesture_down","notifications");
        choice("Doppio tocco",Prefs.ACTION_NAMES,Prefs.ACTIONS,"gesture_double","lock");
        choice("Due dita in su",Prefs.ACTION_NAMES,Prefs.ACTIONS,"gesture_two_up","tasks");
        choice("Due dita in giù",Prefs.ACTION_NAMES,Prefs.ACTIONS,"gesture_two_down","quicksettings");

        section("07 / LOCKSCREEN CUSTOM");
        item("Lockscreen AmpyRocket: "+(pr.lockEnabled()?"ATTIVO":"SPENTO"),"Copertina cyberpunk mostrata all'accensione dello schermo, sopra il blocco di sistema (PIN/impronta restano attivi).",()->{
            boolean on=!pr.lockEnabled();
            if(on&&!Perms.overlay(this)){CyberMenu.notice(this,th,"Permesso necessario","Concedi \"Mostra sopra altre app\" per far comparire il lockscreen quando accendi lo schermo.");
                Perms.open(this,Perms.overlayIntent(this));return;}
            pr.put("lock_enabled",on);if(on)LockService.start(this);else LockService.stop(this);build();});
        toggle("Secondi sul lockscreen","lock_seconds",true);
        toggle("Notifiche sul lockscreen","lock_notifications",true);
        item("Anteprima lockscreen","",()->startActivity(new Intent(this,LockScreenActivity.class).putExtra("preview",true)));

        section("08 / APP");
        item("App nascoste ("+pr.hidden().size()+")","Tocca per mostrarle di nuovo",this::hiddenApps);
        toggle("Aggiungi alla Home le nuove app","auto_add",true);
        toggle("App suggerite nel drawer","suggestions",true);
        item("Ripristina nomi originali","",()->{for(String k:new ArrayList<>(pr.raw().getAll().keySet()))if(k.startsWith("rename_"))pr.raw().edit().remove(k).apply();
            AppRepository.get(this).reloadAsync();Ui.toast(this,"Nomi ripristinati");});

        section("09 / BACKUP");
        item("Esporta layout Home","Salva pagine, dock, cartelle in un file JSON",()->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                .putExtra(Intent.EXTRA_TITLE,"ampyrocket-layout.json");startActivityForResult(i,REQ_EXPORT);});
        item("Importa layout Home","",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),REQ_IMPORT));
        item("Ripristina layout predefinito","",()->{pr.put("layout_json",null);pr.put("layout_rev",pr.i("layout_rev",0)+1);Ui.toast(this,"Layout ripristinato");});

        section("10 / INFO");
        item("AmpyRocket 1.0 — Neon Ignition","Launcher cyberpunk della famiglia Ampy (AmpyRAW, AmpyLink).",()->{});
    }

    // --------------------------------------------------------- rows
    private void section(String s){
        GlitchTextView t=new GlitchTextView(this,th,12,th.secondary,true);t.alignLeft();t.setText(s);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(22);lp.bottomMargin=Ui.dp(6);list.addView(t,lp);
    }
    private LinearLayout row(String title,String sub,String value,int valCol,Runnable click){
        LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(Ui.dp(14),Ui.dp(12),Ui.dp(14),Ui.dp(12));
        r.setBackground(new Ui.NeonPanel(th,Theme.a(th.accent,120),.55f));
        LinearLayout txt=new LinearLayout(this);txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(Ui.text(this,title,13,th.fg,false));
        if(sub!=null&&!sub.isEmpty()){TextView s=Ui.text(this,sub,10.5f,th.muted,false);s.setPadding(0,Ui.dp(4),0,0);txt.addView(s);}
        r.addView(txt,new LinearLayout.LayoutParams(0,-2,1));
        if(value!=null){TextView v=Ui.text(this,value,11,valCol,true);v.setGravity(Gravity.END);v.setPadding(Ui.dp(8),Ui.dp(6),Ui.dp(8),Ui.dp(6));
            r.addView(v,new LinearLayout.LayoutParams(-2,-2));
            if("[ ON ]".equals(value))Blink.attach(v,th,th.accent,true,4);}
        Blink bl=Blink.attach(r,th,th.accent,false,6);
        r.setOnClickListener(v->{Ui.tick(v);bl.burst();v.postDelayed(click,140);});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(5);list.addView(r,lp);return r;
    }
    private void item(String t,String s,Runnable r){row(t,s,null,0,r);}
    private void perm(String t,boolean ok,Runnable r){row(t,null,ok?"● OK":"○ CONCEDI",ok?th.accent:th.warn,r);}
    private void toggle(String t,String key,boolean def){
        boolean on=pr.b(key,def);row(t,null,on?"[ ON ]":"[ OFF ]",on?th.accent:th.muted,()->{pr.put(key,!pr.b(key,def));build();});
    }
    private void choice(String t,String[] names,String[] keys,String key,String def){
        int sel=Math.max(0,Arrays.asList(keys).indexOf(pr.s(key,def)));
        row(t,null,names[sel],th.accent,()->CyberMenu.choose(this,th,t,names,sel,i->{pr.put(key,keys[i]);AppRepository.get(this).clearIconCache();build();}));
    }
    private void intChoice(String t,String[] names,int[] vals,String key,int def){
        int cur=pr.i(key,def),sel=0;for(int i=0;i<vals.length;i++)if(vals[i]==cur)sel=i;final int s=sel;
        row(t,null,names[sel],th.accent,()->CyberMenu.choose(this,th,t,names,s,i->{pr.put(key,vals[i]);Power.invalidate();build();}));
    }
    private void floatChoice(String t,String[] names,float[] vals,String key,float def){
        float cur=pr.f(key,def);int sel=0;for(int i=0;i<vals.length;i++)if(Math.abs(vals[i]-cur)<.01f)sel=i;final int s=sel;
        row(t,null,names[sel],th.accent,()->CyberMenu.choose(this,th,t,names,s,i->{pr.put(key,vals[i]);build();}));
    }

    private void hiddenApps(){
        Set<String> h=pr.hidden();if(h.isEmpty()){Ui.toast(this,"Nessuna app nascosta");return;}
        CyberMenu m=new CyberMenu(this,th,"Tocca per mostrare");AppRepository repo=AppRepository.get(this);
        for(String k:h){AppRepository.App a=repo.find(k);String label=a!=null?a.label:k;
            m.add("◌ "+label,a!=null?a.icon:null,th.fg,()->{Set<String> n=pr.hidden();n.remove(k);pr.setHidden(n);build();});}
        m.show();
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            if(req==REQ_EXPORT){try(java.io.OutputStream os=getContentResolver().openOutputStream(data.getData())){
                os.write(LayoutStore.load(this).export().getBytes(java.nio.charset.StandardCharsets.UTF_8));}Ui.toast(this,"Layout esportato");}
            else if(req==REQ_IMPORT){try(java.io.InputStream is=getContentResolver().openInputStream(data.getData())){
                byte[] buf=new byte[1<<20];int n,tot=0;java.io.ByteArrayOutputStream bo=new java.io.ByteArrayOutputStream();
                while((n=is.read(buf))>0&&(tot+=n)<(4<<20))bo.write(buf,0,n);
                if(LayoutStore.importJson(this,bo.toString("UTF-8"))){pr.put("layout_rev",pr.i("layout_rev",0)+1);Ui.toast(this,"Layout importato");}
                else Ui.toast(this,"File non valido");}}
        }catch(Throwable t){Ui.toast(this,"Errore: "+t.getMessage());}
    }
}
