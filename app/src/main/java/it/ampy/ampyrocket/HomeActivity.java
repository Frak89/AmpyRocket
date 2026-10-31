package it.ampy.ampyrocket;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.ActivityOptions;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import java.util.*;

/** AmpyRocket home: paged workspace, dock, app drawer, task drawer, widgets, gestures. */
public final class HomeActivity extends Activity implements AppRepository.Listener,BadgeListener.Listener,AppDrawerView.Host {
    private static final int REQ_BIND=11,REQ_CONFIG=12;
    private Theme th;private Prefs pr;private AppRepository repo;private LayoutStore layout;
    private FrameLayout root;private CyberBackgroundView bg;private LinearLayout column,dock;
    private PagedWorkspace work;private PageDots dots;private AppDrawerView drawer;private TaskDrawerView tasks;
    private AppWidgetHost widgetHost;private AppWidgetManager widgetMgr;private int pendingWidget=-1;private int pendingRows=2;
    private GestureDetector gestures;private final List<IconView> icons=new ArrayList<>();
    private String stamp;private float downY;private int pointers;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        Window w=getWindow();
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        w.setStatusBarColor(Color.TRANSPARENT);w.setNavigationBarColor(Color.TRANSPARENT);
        pr=Prefs.get(this);repo=AppRepository.get(this);
        widgetMgr=AppWidgetManager.getInstance(this);widgetHost=new AppWidgetHost(this,0x0A3B);
        stamp=stamp();build();
        repo.addListener(this);BadgeListener.addListener(this);
        if(pr.lockEnabled())LockService.start(this);
        if(!pr.b("welcomed",false)){pr.put("welcomed",true);
            root.postDelayed(()->CyberMenu.notice(this,th,"AmpyRocket online",
                "Benvenuto! Scorri in su per il drawer delle app, premi ⧉ TASK per il multitasking e tieni premuto sullo sfondo per widget, effetti e impostazioni.\n\nPer usarlo come Home: Impostazioni → Imposta come launcher predefinito."),900);}
    }

    private String stamp(){
        return pr.theme()+pr.columns()+pr.rows()+pr.iconScale()+pr.labels()+pr.background()+pr.scanlines()+pr.wallpaperDim()+
            pr.glitch()+pr.showSearch()+pr.dockCount()+pr.s("icon_style","neon")+pr.pageFx()+pr.ecoFps()+pr.i("layout_rev",0);
    }

    private void build(){
        th=Theme.load(this);layout=LayoutStore.load(this);
        root=new FrameLayout(this);
        bg=new CyberBackgroundView(this);root.addView(bg,new FrameLayout.LayoutParams(-1,-1));
        column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column,new FrameLayout.LayoutParams(-1,-1));
        if(pr.showSearch()){
            TextView s=Ui.text(this,"⌕  CERCA  //  "+GlitchTextView.GLYPHS.substring(0,6),12,th.muted,false);
            s.setBackground(new Ui.NeonPanel(th,th.accent,.55f));s.setPadding(Ui.dp(16),Ui.dp(12),Ui.dp(16),Ui.dp(12));
            s.setOnClickListener(v->{Ui.tick(v);openDrawer(true);});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(Ui.dp(14),Ui.dp(8),Ui.dp(14),Ui.dp(4));
            column.addView(s,lp);
        }
        work=new PagedWorkspace(this);column.addView(work,new LinearLayout.LayoutParams(-1,0,1));
        work.setListener(new PagedWorkspace.Listener(){
            public void onPageScroll(float pos){bg.setParallax(pos);if(dots!=null)dots.pos=pos;if(dots!=null)dots.invalidate();}
            public void onPageSelected(int page){for(IconView iv:icons)if(iv.getParent()==work.getChildAt(page))iv.decode();}
        });
        // HUD row: task button | page dots | settings
        LinearLayout hud=new LinearLayout(this);hud.setGravity(Gravity.CENTER_VERTICAL);hud.setPadding(Ui.dp(14),0,Ui.dp(14),0);
        TextView taskBtn=Ui.text(this,"⧉ TASK",11,th.accent,true);taskBtn.setPadding(Ui.dp(10),Ui.dp(8),Ui.dp(10),Ui.dp(8));
        taskBtn.setBackground(new Ui.NeonPanel(th,th.accent,.5f));taskBtn.setOnClickListener(v->{Ui.tick(v);openTasks();});
        hud.addView(taskBtn);
        dots=new PageDots();hud.addView(dots,new LinearLayout.LayoutParams(0,Ui.dp(26),1));
        TextView gear=Ui.text(this,"⚙ SYS",11,th.secondary,true);gear.setPadding(Ui.dp(10),Ui.dp(8),Ui.dp(10),Ui.dp(8));
        gear.setBackground(new Ui.NeonPanel(th,th.secondary,.5f));gear.setOnClickListener(v->{Ui.tick(v);homeMenu(v);});
        hud.addView(gear);
        column.addView(hud,new LinearLayout.LayoutParams(-1,-2));
        dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER);
        Ui.NeonPanel dbg=new Ui.NeonPanel(th,th.secondary,.6f);dbg.scan=true;dock.setBackground(dbg);
        LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,Ui.dp(84));dl.setMargins(Ui.dp(10),Ui.dp(6),Ui.dp(10),Ui.dp(8));
        column.addView(dock,dl);
        drawer=new AppDrawerView(this,th,this);drawer.setVisibility(View.GONE);root.addView(drawer,new FrameLayout.LayoutParams(-1,-1));
        tasks=new TaskDrawerView(this,th,this::closeTasks);tasks.setVisibility(View.GONE);root.addView(tasks,new FrameLayout.LayoutParams(-1,-1));
        root.setOnApplyWindowInsetsListener((v,ins)->{
            int top=ins.getSystemWindowInsetTop(),bot=ins.getSystemWindowInsetBottom();
            column.setPadding(0,top,0,bot);drawer.setPadding(0,top,0,bot);tasks.setPadding(0,top,0,bot);return ins.consumeSystemWindowInsets();});
        setContentView(root);
        gestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onFling(MotionEvent e1,MotionEvent e2,float vx,float vy){
                if(e1==null||Math.abs(vy)<Math.abs(vx)*1.4f||Math.abs(vy)<900)return false;
                if(vy<0)act(pointers>=2?pr.gesture("two_up","tasks"):pr.gesture("up","drawer"));
                else act(pointers>=2?pr.gesture("two_down","quicksettings"):pr.gesture("down","notifications"));
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e){act(pr.gesture("double","lock"));return true;}
            @Override public void onLongPress(MotionEvent e){if(touchOnEmpty)homeMenu(work);}
        });
        bindAll();
    }

    private boolean touchOnEmpty=false;

    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(drawer.getVisibility()!=View.VISIBLE&&tasks.getVisibility()!=View.VISIBLE){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){pointers=1;touchOnEmpty=!hitsChild(e);}
            if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN)pointers=Math.max(pointers,e.getPointerCount());
            gestures.onTouchEvent(e);
        }else if(drawer.getVisibility()==View.VISIBLE){
            // swipe down on top of the drawer closes it
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN)downY=e.getY();
            if(e.getActionMasked()==MotionEvent.ACTION_UP&&e.getY()-downY>Ui.dp(140)&&drawer.atTop()){closeDrawer();return true;}
        }
        return super.dispatchTouchEvent(e);
    }
    private boolean hitsChild(MotionEvent e){
        for(IconView v:icons){if(!v.isShown())continue;int[] l=new int[2];v.getLocationOnScreen(l);
            if(e.getRawX()>=l[0]&&e.getRawX()<=l[0]+v.getWidth()&&e.getRawY()>=l[1]&&e.getRawY()<=l[1]+v.getHeight())return true;}
        int[] d=new int[2];dock.getLocationOnScreen(d);if(e.getRawY()>=d[1])return true;
        return false;
    }

    // ------------------------------------------------------------------ binding
    private void bindAll(){
        icons.clear();
        int cur=work.current();work.removeAllViews();
        List<AppRepository.App> all=repo.all();
        layout.sync(all,pr.b("auto_add",true),pr.columns()*pr.rows());
        for(int pi=0;pi<layout.pages.size();pi++){
            LayoutStore.Page page=layout.pages.get(pi);
            GridPage gp=new GridPage(this,pr.columns(),pr.rows());
            for(String wkey:page.widgets){View wv=widgetView(wkey,pi);if(wv!=null)gp.addWidget(wv,LayoutStore.widgetRows(wkey));}
            for(String key:page.items){IconView iv=iconFor(key,true,pi);if(iv!=null){gp.addIcon(iv);}}
            work.addView(gp);
        }
        work.snapTo(Math.min(cur,work.count()-1),false);
        dock.removeAllViews();
        for(String key:layout.dock){
            if(dock.getChildCount()>=pr.dockCount())break;
            IconView iv=iconFor(key,false,-1);if(iv!=null)dock.addView(iv,new LinearLayout.LayoutParams(0,-1,1));
        }
        TextView drawerBtn=Ui.text(this,"⣿",22,th.accent,true);drawerBtn.setGravity(Gravity.CENTER);
        drawerBtn.setOnClickListener(v->{Ui.tick(v);openDrawer(false);});
        dock.addView(drawerBtn,new LinearLayout.LayoutParams(Ui.dp(44),-1));
        dots.invalidate();onBadgesChanged();
    }

    private IconView iconFor(String key,boolean label,int page){
        IconView iv=new IconView(this,th,label&&pr.labels());
        if(key.startsWith("folder:")){
            LayoutStore.Folder f=layout.folders.get(key.substring(7));if(f==null)return null;
            List<AppRepository.App> apps=new ArrayList<>();for(String k:f.apps){AppRepository.App a=repo.find(k);if(a!=null)apps.add(a);}
            iv.bindFolder(f,apps);
            iv.setOnClickListener(v->{Ui.tick(v);openFolder(iv,f);});
            iv.setOnLongClickListener(v->{Ui.longTick(v);folderMenu(iv,f,page);return true;});
        }else{
            AppRepository.App a=repo.find(key);if(a==null)return null;
            iv.bindApp(a);
            iv.setOnClickListener(v->launch(iv,a));
            iv.setOnLongClickListener(v->{Ui.longTick(v);itemMenu(iv,a,page);return true;});
        }
        icons.add(iv);return iv;
    }

    private View widgetView(String key,int page){
        View v=null;
        if("clock".equals(key))v=new ClockWidgetView(this,th,false);
        else if("hud".equals(key)){v=new HudWidgetView(this,th);v.setOnClickListener(x->{Ui.tick(x);openTasks();});}
        else if(key.startsWith("aw:")){
            try{int id=Integer.parseInt(key.split(":")[1]);AppWidgetProviderInfo info=widgetMgr.getAppWidgetInfo(id);
                if(info==null)return null;
                AppWidgetHostView hv=widgetHost.createView(this,id,info);hv.setAppWidget(id,info);
                hv.setPadding(Ui.dp(6),Ui.dp(4),Ui.dp(6),Ui.dp(4));v=hv;}catch(Throwable t){return null;}
        }
        if(v!=null){final View fv=v;v.setOnLongClickListener(x->{Ui.longTick(x);widgetMenu(fv,key,page);return true;});}
        return v;
    }

    // ------------------------------------------------------------------ actions
    @Override public void launch(IconView v,AppRepository.App a){
        if(v!=null)Ui.tick(v);
        String fx=pr.launchFx();
        Rect r=v!=null&&v.isAttachedToWindow()?v.iconRect():null;
        if("genie".equals(fx)&&r!=null&&v.iconBitmap()!=null){
            Bitmap card=launchCard(a,v.iconBitmap());
            int[] rl=new int[2];root.getLocationOnScreen(rl);
            RectF small=new RectF(r.left-rl[0],r.top-rl[1],r.right-rl[0],r.bottom-rl[1]);
            RectF big=new RectF(0,0,root.getWidth(),root.getHeight());
            GenieFx.Overlay o=new GenieFx.Overlay(this,card,big,small,true,320,th.accent,null);
            root.addView(o,new FrameLayout.LayoutParams(-1,-1));
            root.postDelayed(()->{
                repo.launch(a,r,ActivityOptions.makeCustomAnimation(this,android.R.anim.fade_in,android.R.anim.fade_out).toBundle());
                root.postDelayed(()->root.removeView(o),600);
            },300);
        }else{
            Bundle opts=null;
            if(v!=null&&r!=null){
                if("reveal".equals(fx))opts=ActivityOptions.makeClipRevealAnimation(v,0,0,v.getWidth(),v.getHeight()).toBundle();
                else if("scale".equals(fx))opts=ActivityOptions.makeScaleUpAnimation(v,0,0,v.getWidth(),v.getHeight()).toBundle();
            }
            if(!repo.launch(a,r,opts))Ui.toast(this,"Impossibile avviare "+a.label);
        }
        if(drawer.getVisibility()==View.VISIBLE)root.postDelayed(this::closeDrawer,400);
    }

    private Bitmap launchCard(AppRepository.App a,Bitmap icon){
        int w=Math.max(1,root.getWidth()/2),h=Math.max(1,root.getHeight()/2);
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
        c.drawColor(th.bg);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        p.setColor(Theme.a(th.accent,40));for(int x=0;x<w;x+=Ui.dp(16))c.drawLine(x,0,x,h,p);for(int y=0;y<h;y+=Ui.dp(16))c.drawLine(0,y,w,y,p);
        int s=Math.min(w,h)/3;c.drawBitmap(icon,null,new Rect((w-s)/2,(h-s)/2-Ui.dp(10),(w+s)/2,(h+s)/2-Ui.dp(10)),p);
        p.setColor(th.accent);p.setTextSize(Ui.dpf(13));p.setTypeface(Ui.MONO_BOLD);p.setTextAlign(Paint.Align.CENTER);
        c.drawText("> "+a.label.toUpperCase(Locale.ROOT),w/2f,(h+s)/2f+Ui.dp(14),p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(3));c.drawRect(1,1,w-1,h-1,p);
        return b;
    }

    @Override public void appMenu(IconView v,AppRepository.App a){itemMenu(v,a,-2);}

    private void itemMenu(IconView v,AppRepository.App a,int page){
        CyberMenu m=new CyberMenu(this,th,a.label);
        for(ShortcutInfo s:repo.shortcuts(a)){
            CharSequence l=s.getShortLabel();m.add(l==null?"…":l.toString(),repo.shortcutIcon(s),th.fg,()->repo.startShortcut(s));
        }
        m.add("ⓘ  Info app",()->repo.appInfo(a));
        boolean inDock=layout.dock.contains(a.key);
        if(page==-2){ // from drawer
            m.add("⌂  Aggiungi alla Home",()->{if(!layout.contains(a.key)){layout.pages.get(work.current()).items.add(a.key);saveRebind();}
                else Ui.toast(this,"Già presente");});
        }
        if(page>=0){
            m.add("◂  Sposta a sinistra",()->move(a.key,page,-1));
            m.add("▸  Sposta a destra",()->move(a.key,page,1));
            m.add("⇆  Sposta in un'altra pagina",()->movePage(a.key,page));
            m.add("▤  Crea cartella / aggiungi a cartella",()->toFolder(a.key,page));
        }
        if(!inDock)m.add("⬓  Aggiungi al dock",()->{layout.removeEverywhere(a.key);layout.dock.add(Math.min(layout.dock.size(),pr.dockCount()-1),a.key);
            while(layout.dock.size()>pr.dockCount()){String out=layout.dock.remove(layout.dock.size()-1);layout.pages.get(0).items.add(out);}saveRebind();});
        else m.add("⬒  Togli dal dock",()->{layout.dock.remove(a.key);layout.pages.get(work.current()).items.add(a.key);saveRebind();});
        m.add("✎  Rinomina",()->CyberMenu.input(this,th,"Rinomina "+a.original,a.label,t->{pr.setRename(a.key,t);repo.reloadAsync();}));
        m.add("◌  Nascondi dal drawer",()->{Set<String> h=pr.hidden();h.add(a.key);pr.setHidden(h);layout.removeEverywhere(a.key);saveRebind();
            Ui.toast(this,"Nascosta. Ripristina da Impostazioni → App nascoste");});
        if(page>=0||inDock)m.add("✕  Rimuovi dalla Home",()->{layout.removeEverywhere(a.key);saveRebind();});
        if(!repo.isSystem(a.pkg()))m.danger("⌫  Disinstalla",()->repo.uninstall(a.pkg()));
        m.showAt(v);
    }

    private void move(String key,int page,int dir){
        List<String> it=layout.pages.get(page).items;int i=it.indexOf(key);int j=i+dir;
        if(i<0)return;
        if(j<0||j>=it.size()){int np=page+dir;if(np<0||np>=layout.pages.size())return;it.remove(i);
            List<String> o=layout.pages.get(np).items;if(dir<0)o.add(key);else o.add(0,key);}
        else Collections.swap(it,i,j);
        saveRebind();
    }
    private void movePage(String key,int page){
        String[] n=new String[layout.pages.size()+1];for(int i=0;i<layout.pages.size();i++)n[i]="PAGINA "+(i+1);n[n.length-1]="+ NUOVA PAGINA";
        CyberMenu.choose(this,th,"Sposta in",n,page,i->{layout.removeEverywhere(key);
            if(i>=layout.pages.size())layout.pages.add(new LayoutStore.Page());layout.pages.get(i).items.add(key);saveRebind();work.snapTo(i,true);});
    }
    private void toFolder(String key,int page){
        List<LayoutStore.Folder> fs=new ArrayList<>(layout.folders.values());
        String[] n=new String[fs.size()+1];for(int i=0;i<fs.size();i++)n[i]="▤ "+fs.get(i).name;n[fs.size()]="+ NUOVA CARTELLA";
        CyberMenu.choose(this,th,"Cartella",n,-1,i->{
            if(i<fs.size()){LayoutStore.Folder f=fs.get(i);layout.removeEverywhere(key);if(!f.apps.contains(key))f.apps.add(key);saveRebind();}
            else CyberMenu.input(this,th,"Nome cartella","CARTELLA",name->{
                LayoutStore.Folder f=layout.newFolder(name.isEmpty()?"CARTELLA":name);
                List<String> it=layout.pages.get(page).items;int idx=it.indexOf(key);
                layout.removeEverywhere(key);f.apps.add(key);
                if(idx<0||idx>it.size())it.add("folder:"+f.id);else it.add(idx,"folder:"+f.id);saveRebind();});
        });
    }
    private void folderMenu(IconView v,LayoutStore.Folder f,int page){
        new CyberMenu(this,th,f.name)
            .add("✎  Rinomina cartella",()->CyberMenu.input(this,th,"Rinomina",f.name,t->{f.name=t.isEmpty()?f.name:t;saveRebind();}))
            .add("◂  Sposta a sinistra",()->move("folder:"+f.id,page,-1))
            .add("▸  Sposta a destra",()->move("folder:"+f.id,page,1))
            .danger("✕  Sciogli cartella",()->{layout.deleteFolder(f.id);saveRebind();}).showAt(v);
    }
    private void openFolder(IconView anchor,LayoutStore.Folder f){
        android.app.Dialog d=new android.app.Dialog(this);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        Ui.NeonPanel np=new Ui.NeonPanel(th,th.secondary,.95f);np.scan=true;box.setBackground(np);box.setPadding(Ui.dp(12),Ui.dp(14),Ui.dp(12),Ui.dp(14));
        GlitchTextView t=new GlitchTextView(this,th,14,th.accent,true);t.setText("▤ "+f.name.toUpperCase(Locale.ROOT));box.addView(t);
        GridLayout g=new GridLayout(this);g.setColumnCount(4);
        for(String k:f.apps){AppRepository.App a=repo.find(k);if(a==null)continue;
            IconView iv=new IconView(this,th,true);iv.bindApp(a);iv.setBadge(BadgeListener.count(a.pkg()));
            iv.setOnClickListener(x->{d.dismiss();launch(iv,a);});
            iv.setOnLongClickListener(x->{new CyberMenu(this,th,a.label).add("⇱  Togli dalla cartella",()->{d.dismiss();f.apps.remove(k);
                layout.pages.get(work.current()).items.add(k);saveRebind();}).add("ⓘ  Info app",()->repo.appInfo(a)).showAt(iv);return true;});
            g.addView(iv,new GridLayout.LayoutParams(new ViewGroup.LayoutParams(Ui.dp(68),Ui.dp(84))));}
        box.addView(g);d.setContentView(box);
        Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            w.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
        d.show();
    }

    private void widgetMenu(View v,String key,int page){
        new CyberMenu(this,th,"Widget").danger("✕  Rimuovi widget",()->{
            layout.pages.get(page).widgets.remove(key);
            if(key.startsWith("aw:"))try{widgetHost.deleteAppWidgetId(Integer.parseInt(key.split(":")[1]));}catch(Throwable ignored){}
            saveRebind();}).showAt(v);
    }

    private void homeMenu(View anchor){
        int page=work.current();LayoutStore.Page p=layout.pages.get(page);
        CyberMenu m=new CyberMenu(this,th,"AmpyRocket // pagina "+(page+1));
        m.add("▦  Aggiungi widget Android",this::pickWidget);
        if(!p.widgets.contains("clock"))m.add("◷  Aggiungi orologio glitch",()->{p.widgets.add(0,"clock");saveRebind();});
        if(!p.widgets.contains("hud"))m.add("▥  Aggiungi HUD sistema",()->{p.widgets.add("hud");saveRebind();});
        m.add("⧉  Task drawer / multitasking",this::openTasks);
        m.add("✦  Effetto pagine: "+Prefs.PAGE_FX_NAMES[Arrays.asList(Prefs.PAGE_FX).indexOf(pr.pageFx())],()->
            CyberMenu.choose(this,th,"Transizione pagine",Prefs.PAGE_FX_NAMES,Arrays.asList(Prefs.PAGE_FX).indexOf(pr.pageFx()),i->{
                pr.put("page_fx",Prefs.PAGE_FX[i]);work.setEffect(Prefs.PAGE_FX[i]);work.snapTo(work.count()>1?(page+1)%work.count():0,true);}));
        m.add("◐  Tema: "+th.label,()->CyberMenu.choose(this,th,"Tema",Theme.NAMES,th.index(),i->{pr.put("theme",Theme.KEYS[i]);rebuild();}));
        m.add("▒  Sfondo animato",()->CyberMenu.choose(this,th,"Sfondo",Prefs.BG_NAMES,Arrays.asList(Prefs.BG).indexOf(pr.background()),i->{
            pr.put("background",Prefs.BG[i]);bg.reload();bg.pulse();}));
        m.add("🖼  Sfondo di sistema",()->{try{startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER),"Sfondo"));}catch(Throwable ignored){}});
        m.add("+  Nuova pagina",()->{layout.pages.add(new LayoutStore.Page());saveRebind();work.snapTo(work.count()-1,true);});
        if(layout.pages.size()>1)m.danger("✕  Elimina questa pagina",()->{
            LayoutStore.Page dead=layout.pages.remove(page);layout.pages.get(Math.max(0,page-1)).items.addAll(dead.items);saveRebind();});
        m.add("⚙  Impostazioni AmpyRocket",()->startActivity(new Intent(this,SettingsActivity.class)));
        m.showAt(anchor);
    }

    // ------------------------------------------------------------------ widgets
    private void pickWidget(){
        List<AppWidgetProviderInfo> list=widgetMgr.getInstalledProviders();
        Collections.sort(list,(a,b)->String.valueOf(a.loadLabel(getPackageManager())).compareToIgnoreCase(String.valueOf(b.loadLabel(getPackageManager()))));
        CyberMenu m=new CyberMenu(this,th,"Widget ("+list.size()+")");
        for(AppWidgetProviderInfo info:list){
            String app=repo.labelFor(info.provider.getPackageName());
            m.add(app+" › "+info.loadLabel(getPackageManager()),null,th.fg,()->addWidget(info));
        }
        m.show();
    }
    private void addWidget(AppWidgetProviderInfo info){
        int id=widgetHost.allocateAppWidgetId();pendingWidget=id;
        float cellDp=(work.getHeight()/(float)pr.rows())/Ui.density();
        pendingRows=Math.max(1,Math.min(4,(int)Math.ceil(info.minHeight/Ui.density()/Math.max(40,cellDp))));
        if(widgetMgr.bindAppWidgetIdIfAllowed(id,info.getProfile(),info.provider,null))configureWidget(id,info);
        else{Intent i=new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,info.provider).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE,info.getProfile());
            startActivityForResult(i,REQ_BIND);}
    }
    private void configureWidget(int id,AppWidgetProviderInfo info){
        if(info.configure!=null){
            try{widgetHost.startAppWidgetConfigureActivityForResult(this,id,0,REQ_CONFIG,null);return;}catch(Throwable ignored){}
        }
        commitWidget(id);
    }
    private void commitWidget(int id){layout.pages.get(work.current()).widgets.add("aw:"+id+":"+pendingRows);pendingWidget=-1;saveRebind();}

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(pendingWidget<0)return;
        if(res!=RESULT_OK){widgetHost.deleteAppWidgetId(pendingWidget);pendingWidget=-1;return;}
        if(req==REQ_BIND){AppWidgetProviderInfo info=widgetMgr.getAppWidgetInfo(pendingWidget);if(info!=null)configureWidget(pendingWidget,info);}
        else if(req==REQ_CONFIG)commitWidget(pendingWidget);
    }

    // ------------------------------------------------------------------ gestures & drawers
    private void act(String a){
        switch(a){
            case "drawer":openDrawer(false);break;
            case "search":openDrawer(true);break;
            case "tasks":openTasks();break;
            case "notifications":if(!GestureService.notifications())expandShade();break;
            case "quicksettings":if(!GestureService.quickSettings())expandShade();break;
            case "recents":if(!GestureService.recents())needAccessibility();break;
            case "lock":if(!GestureService.lock())needAccessibility();else Ui.buzz(this,20);break;
            case "settings":startActivity(new Intent(this,SettingsActivity.class));break;
            default:break;
        }
    }
    @SuppressWarnings({"WrongConstant","JavaReflectionMemberAccess"})
    private void expandShade(){
        try{Object sb=getSystemService("statusbar");sb.getClass().getMethod("expandNotificationsPanel").invoke(sb);}
        catch(Throwable t){needAccessibility();}
    }
    private void needAccessibility(){
        CyberMenu.notice(this,th,"Servizio gesti","Per bloccare lo schermo e aprire tendina/recenti con i gesti attiva \"AmpyRocket gesti\" in Accessibilità.");
        root.postDelayed(()->Perms.open(this,Perms.accessibilityIntent()),1800);
    }

    private void openDrawer(boolean search){
        if(drawer.getVisibility()==View.VISIBLE)return;
        drawer.setVisibility(View.VISIBLE);drawer.open(search);bg.pulse();
        drawer.post(()->{
            int cx=drawer.getWidth()/2,cy=drawer.getHeight();
            Animator a=ViewAnimationUtils.createCircularReveal(drawer,cx,cy,0,(float)Math.hypot(cx,cy));a.setDuration(380);a.start();
            drawer.setTranslationY(Ui.dp(40));drawer.animate().translationY(0).setDuration(380).start();
        });
    }
    @Override public void closeDrawer(){
        if(drawer.getVisibility()!=View.VISIBLE)return;drawer.hideKeyboard();
        int cx=drawer.getWidth()/2,cy=drawer.getHeight();
        Animator a=ViewAnimationUtils.createCircularReveal(drawer,cx,cy,(float)Math.hypot(cx,cy),0);a.setDuration(300);
        a.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator x){drawer.setVisibility(View.GONE);}});a.start();
    }
    private void openTasks(){
        if(tasks.getVisibility()==View.VISIBLE)return;
        tasks.setVisibility(View.VISIBLE);tasks.setAlpha(0);tasks.setScaleX(1.08f);tasks.setScaleY(1.08f);
        tasks.animate().alpha(1).scaleX(1).scaleY(1).setDuration(260).start();tasks.open();
    }
    private void closeTasks(){
        if(tasks.getVisibility()!=View.VISIBLE)return;tasks.close();
        tasks.animate().alpha(0).scaleX(.92f).scaleY(.92f).setDuration(220).withEndAction(()->tasks.setVisibility(View.GONE)).start();
    }

    @Override public void onBackPressed(){
        if(tasks.getVisibility()==View.VISIBLE)closeTasks();else if(drawer.getVisibility()==View.VISIBLE)closeDrawer();
        else work.snapTo(0,true);
    }
    @Override protected void onNewIntent(Intent i){
        super.onNewIntent(i);
        if(Intent.ACTION_MAIN.equals(i.getAction())&&i.hasCategory(Intent.CATEGORY_HOME)){
            boolean any=tasks.getVisibility()==View.VISIBLE||drawer.getVisibility()==View.VISIBLE;
            if(tasks.getVisibility()==View.VISIBLE)closeTasks();if(drawer.getVisibility()==View.VISIBLE)closeDrawer();
            if(!any&&hasWindowFocus())work.snapTo(0,true);
        }
    }

    // ------------------------------------------------------------------ lifecycle & callbacks
    private void saveRebind(){layout.save();bindAll();}
    private void rebuild(){stamp=stamp();build();}

    @Override protected void onStart(){super.onStart();try{widgetHost.startListening();}catch(Throwable ignored){}}
    @Override protected void onStop(){super.onStop();try{widgetHost.stopListening();}catch(Throwable ignored){}bg.setRunning(false);}
    @Override protected void onResume(){
        super.onResume();
        if(!stamp.equals(stamp())){boolean d=drawer.getVisibility()==View.VISIBLE;rebuild();if(d)openDrawer(false);}
        bg.setRunning(true);
        for(IconView v:icons)if(v.isShown())v.decode();
        if(tasks.getVisibility()==View.VISIBLE)tasks.open();
    }
    @Override protected void onPause(){super.onPause();bg.setRunning(false);if(tasks.getVisibility()==View.VISIBLE)tasks.close();}
    @Override protected void onDestroy(){repo.removeListener(this);BadgeListener.removeListener(this);super.onDestroy();}
    @Override public void onAppsChanged(){bindAll();if(drawer.getVisibility()==View.VISIBLE)drawer.refresh();}
    @Override public void onBadgesChanged(){for(IconView v:icons){
        if(v.app!=null)v.setBadge(BadgeListener.count(v.app.pkg()));
        else if(v.folder!=null){int n=0;for(String k:v.folder.apps){AppRepository.App a=repo.find(k);if(a!=null)n+=BadgeListener.count(a.pkg());}v.setBadge(n);}}}

    /** Page indicator: glowing segments with a sliding glitch cursor. */
    private final class PageDots extends View {
        float pos;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        PageDots(){super(HomeActivity.this);}
        @Override protected void onDraw(Canvas c){
            int n=Math.max(1,work.count());float seg=Ui.dpf(18),gap=Ui.dpf(6),total=n*seg+(n-1)*gap,x0=(getWidth()-total)/2f,y=getHeight()/2f;
            for(int i=0;i<n;i++){p.setColor(Theme.a(th.accent,70));float x=x0+i*(seg+gap);c.drawRect(x,y-Ui.dpf(1.5f),x+seg,y+Ui.dpf(1.5f),p);}
            float cx=x0+pos*(seg+gap);p.setColor(th.secondary);c.drawRect(cx,y-Ui.dpf(3),cx+seg,y+Ui.dpf(3),p);
            p.setColor(Theme.a(th.accent,200));c.drawRect(cx+Ui.dpf(2),y-Ui.dpf(3),cx+seg+Ui.dpf(2),y-Ui.dpf(1),p);
        }
    }
}
