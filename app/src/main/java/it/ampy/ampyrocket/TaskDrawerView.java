package it.ampy.ampyrocket;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.*;

/** Task drawer: live system meters, 3D card deck of running/recent apps, activity table. */
public final class TaskDrawerView extends FrameLayout implements CardDeckView.Host {
    private static HandlerThread worker;
    private final Theme th;private final Runnable onClose;private final CardDeckView deck;
    private final MetersView meters;private final GlitchTextView title;private final TextView banner,modeBtn;
    private final LinearLayout table;private final ScrollView tableScroll;private TextView sysChip,recChip;
    private final Handler ui=new Handler(Looper.getMainLooper()),bg;private boolean open=false,tableMode=false;
    private SystemMonitor.Snapshot last;
    private final Runnable poll=new Runnable(){@Override public void run(){
        if(!open)return;
        bg.post(()->{Prefs pr=Prefs.get(getContext());SystemMonitor.Snapshot s=SystemMonitor.get(getContext()).full(pr.b("task_system",false),pr.b("task_recent",false));ui.post(()->apply(s));});
        ui.postDelayed(this,Power.taskPollMs(getContext()));
    }};

    public TaskDrawerView(Context c,Theme th,Runnable onClose){
        super(c);this.th=th;this.onClose=onClose;
        synchronized(TaskDrawerView.class){if(worker==null){worker=new HandlerThread("ampy-tasks");worker.start();}}
        bg=new Handler(worker.getLooper());
        setBackgroundColor(Theme.a(th.bg,238));setClickable(true);
        LinearLayout col=new LinearLayout(c);col.setOrientation(LinearLayout.VERTICAL);col.setPadding(Ui.dp(12),Ui.dp(8),Ui.dp(12),Ui.dp(8));
        addView(col,new LayoutParams(-1,-1));
        LinearLayout head=new LinearLayout(c);head.setGravity(Gravity.CENTER_VERTICAL);
        title=new GlitchTextView(c,th,15,th.accent,true);title.alignLeft();title.setText("// TASK MATRIX");
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView fxBtn=chip("✦ FX",th.secondary,v->fxMenu());head.addView(fxBtn);
        modeBtn=chip("▤ TABELLA",th.accent,v->{tableMode=!tableMode;mode();});head.addView(modeBtn);
        TextView x=chip("✕",th.bad,v->onClose.run());head.addView(x);
        col.addView(head);
        LinearLayout filters=new LinearLayout(c);filters.setGravity(Gravity.CENTER_VERTICAL);
        sysChip=chip("⚙ PROCESSI DI SISTEMA",th.warn,v->toggleFilter("task_system",sysChip));
        recChip=chip("◷ ANCHE RECENTI",th.accent,v->toggleFilter("task_recent",recChip));
        ((LinearLayout.LayoutParams)sysChip.getLayoutParams()).leftMargin=0;
        filters.addView(sysChip);filters.addView(recChip);
        Ui.setSelected(sysChip,Prefs.get(c).b("task_system",false));Ui.setSelected(recChip,Prefs.get(c).b("task_recent",false));
        LinearLayout.LayoutParams fl0=new LinearLayout.LayoutParams(-1,-2);fl0.topMargin=Ui.dp(6);col.addView(filters,fl0);
        meters=new MetersView(c);LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(-1,Ui.dp(118));ml.topMargin=Ui.dp(6);
        col.addView(meters,ml);
        banner=Ui.text(c,"",11,th.warn,true);banner.setPadding(Ui.dp(12),Ui.dp(10),Ui.dp(12),Ui.dp(10));
        banner.setBackground(new Ui.NeonPanel(th,th.warn,.6f));banner.setVisibility(GONE);
        banner.setOnClickListener(v->Perms.open(getContext(),Perms.usageIntent(getContext())));
        LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,-2);bl.topMargin=Ui.dp(6);col.addView(banner,bl);
        FrameLayout body=new FrameLayout(c);col.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        deck=new CardDeckView(c,th,this);deck.setEmptyMessage("NESSUNA APP ATTIVA\nconcedi l'accesso all'utilizzo");
        body.addView(deck,new LayoutParams(-1,-1));
        tableScroll=new ScrollView(c);table=new LinearLayout(c);table.setOrientation(LinearLayout.VERTICAL);tableScroll.addView(table);
        tableScroll.setVisibility(GONE);body.addView(tableScroll,new LayoutParams(-1,-1));
        LinearLayout foot=new LinearLayout(c);foot.setGravity(Gravity.CENTER);
        TextView clear=Ui.button(c,th,"⌫ CHIUDI TUTTE",v->closeAll());
        foot.addView(clear);
        LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(-1,-2);fl.topMargin=Ui.dp(6);col.addView(foot,fl);
    }
    private TextView chip(String s,int col,View.OnClickListener l){
        TextView t=Ui.chip(getContext(),th,s,col,l);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=Ui.dp(6);t.setLayoutParams(lp);return t;
    }

    private void toggleFilter(String key,TextView chip){
        Prefs pr=Prefs.get(getContext());boolean on=!pr.b(key,false);pr.put(key,on);Ui.setSelected(chip,on);
        ui.removeCallbacks(poll);ui.post(poll);
    }

    public void open(){
        open=true;title.glitchNow();
        boolean ok=Perms.usageAccess(getContext());
        banner.setVisibility(ok?GONE:VISIBLE);
        banner.setText("⚠ TOCCA QUI per concedere \"Accesso ai dati di utilizzo\": serve per vedere le app attive.");
        ui.removeCallbacks(poll);ui.post(poll);
    }
    public void close(){open=false;ui.removeCallbacks(poll);}

    private void mode(){
        modeBtn.setText(tableMode?"⧉ CARDS":"▤ TABELLA");Ui.setSelected(modeBtn,tableMode);
        deck.setVisibility(tableMode?GONE:VISIBLE);tableScroll.setVisibility(tableMode?VISIBLE:GONE);
        if(last!=null)fillTable(last);
    }

    private void fxMenu(){
        Prefs pr=Prefs.get(getContext());
        new CyberMenu(getContext(),th,"Effetti card")
            .add("▣  Layout: "+Prefs.DECK_FX_NAMES[Arrays.asList(Prefs.DECK_FX).indexOf(pr.deckFx())],()->
                CyberMenu.choose(getContext(),th,"Layout card",Prefs.DECK_FX_NAMES,Arrays.asList(Prefs.DECK_FX).indexOf(pr.deckFx()),i->{
                    pr.put("deck_fx",Prefs.DECK_FX[i]);deck.setEffect(Prefs.DECK_FX[i]);}))
            .add("✕  Chiusura: "+Prefs.CLOSE_FX_NAMES[Arrays.asList(Prefs.CLOSE_FX).indexOf(pr.closeFx())],()->
                CyberMenu.choose(getContext(),th,"Animazione chiusura",Prefs.CLOSE_FX_NAMES,Arrays.asList(Prefs.CLOSE_FX).indexOf(pr.closeFx()),i->{
                    pr.put("close_fx",Prefs.CLOSE_FX[i]);deck.setCloseFx(Prefs.CLOSE_FX[i]);}))
            .show();
    }

    private void apply(SystemMonitor.Snapshot s){
        if(!open)return;last=s;meters.snap=s;meters.invalidate();
        deck.setApps(s.apps);if(tableMode)fillTable(s);
    }

    private void fillTable(SystemMonitor.Snapshot s){
        table.removeAllViews();AppRepository repo=AppRepository.get(getContext());
        table.addView(rowView("APP","ATTIVITÀ","STATO",th.secondary,true,null));
        for(SystemMonitor.AppLoad a:s.apps){
            String st=(a.system?"SIS · ":"")+(a.foreground?"PRIMO PIANO":a.fgService?"SERVIZIO":"BACKGROUND");
            View r=rowView(repo.labelFor(a.pkg)+(a.launchable?"":"  ("+a.pkg+")"),Math.round(a.activity*100)+"%",st,a.foreground?th.secondary:th.fg,false,a);
            r.setOnClickListener(v->{Ui.tick(v);menu(a.pkg,v);});table.addView(r);
            if(a.system)r.setAlpha(.82f);
        }
        table.addView(rowView("PROCESSO LAUNCHER","CPU "+String.format(Locale.US,"%.1f%%",s.selfCpu),SystemMonitor.fmtKb(s.selfPssKb),th.accent,false,null));
    }
    private View rowView(String a,String b,String c,int col,boolean header,SystemMonitor.AppLoad load){
        LinearLayout r=new LinearLayout(getContext());r.setPadding(Ui.dp(8),Ui.dp(10),Ui.dp(8),Ui.dp(10));r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t1=Ui.text(getContext(),a,12,col,header);t1.setSingleLine(true);t1.setEllipsize(android.text.TextUtils.TruncateAt.END);
        r.addView(t1,new LinearLayout.LayoutParams(0,-2,1.6f));
        if(load!=null){Bar bar=new Bar(getContext(),load.activity);r.addView(bar,new LinearLayout.LayoutParams(0,Ui.dp(8),1f));}
        TextView t2=Ui.text(getContext(),b,12,header?col:th.accent,true);t2.setGravity(Gravity.END);r.addView(t2,new LinearLayout.LayoutParams(0,-2,.7f));
        TextView t3=Ui.text(getContext(),c,10,header?col:th.muted,header);t3.setGravity(Gravity.END);r.addView(t3,new LinearLayout.LayoutParams(0,-2,1f));
        if(!header)r.setBackground(new Ui.NeonPanel(th,Theme.a(th.accent,90),.35f));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(4);r.setLayoutParams(lp);return r;
    }
    private final class Bar extends View{final float v;final Paint p=new Paint();Bar(Context c,float v){super(c);this.v=v;}
        @Override protected void onDraw(Canvas c){p.setColor(Theme.a(th.accent,40));c.drawRect(0,0,getWidth(),getHeight(),p);
            p.setColor(th.accent);c.drawRect(0,0,getWidth()*v,getHeight(),p);}}

    // ---------------------------------------------------------- Host
    @Override public void open(String pkg){
        Ui.buzz(getContext(),12);
        if(!AppRepository.get(getContext()).launchPackage(pkg,null))Ui.toast(getContext(),"Impossibile aprire");
    }
    @Override public void kill(String pkg){
        SystemMonitor.AppLoad load=null;if(last!=null)for(SystemMonitor.AppLoad a:last.apps)if(a.pkg.equals(pkg))load=a;
        try{ActivityManager am=(ActivityManager)getContext().getSystemService(Context.ACTIVITY_SERVICE);am.killBackgroundProcesses(pkg);}
        catch(Throwable ignored){}
        SystemMonitor.get(getContext()).dismiss(pkg);
        if(GestureService.enabled(getContext())){GestureService.forceStop(pkg);return;}
        if(!closingAll&&!warned){warned=true;
            CyberMenu.notice(getContext(),th,"Chiusura reale",
                "Da Android 14 un launcher non può chiudere i processi delle altre app: la card viene tolta ma l'app può restare attiva.\n\nAttiva l'accessibilità \"AmpyRocket gesti\": AmpyRocket premerà da solo \"Forza arresto\" quando chiudi un'app.");
            return;}
        if(load!=null&&(load.fgService||load.foreground)&&!closingAll){
            AppRepository repo=AppRepository.get(getContext());
            new CyberMenu(getContext(),th,repo.labelFor(pkg)+" ha un servizio attivo")
                .add("Android non lascia chiudere a un launcher le app con un servizio in primo piano (musica, navigazione, download…).",null,th.muted,()->{})
                .danger("⏻  Forza arresto (Info app)",()->repo.appInfo(pkg)).show();
        }
    }
    private boolean closingAll=false,warned=false;
    private void closeAll(){
        closingAll=true;int n=deck.packages().size();deck.closeAll();
        postDelayed(()->{closingAll=false;Ui.toast(getContext(),GestureService.enabled(getContext())?"Forza arresto di "+n+" app…":n+" card chiuse");},1400);
    }

    @Override public void menu(String pkg,View anchor){
        AppRepository repo=AppRepository.get(getContext());
        CyberMenu m=new CyberMenu(getContext(),th,repo.labelFor(pkg))
            .add("▶  Passa all'app",()->open(pkg))
            .danger("✕  Chiudi (forza arresto)",()->{kill(pkg);ui.removeCallbacks(poll);ui.post(poll);})
            .danger("⏻  Forza arresto (Info app)",()->repo.appInfo(pkg));
        if(!repo.isSystem(pkg))m.danger("⌫  Disinstalla",()->repo.uninstall(pkg));
        m.show();
    }

    /** System meters: CPU, RAM, per-core frequency, battery, with history graphs. */
    private final class MetersView extends View {
        SystemMonitor.Snapshot snap;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        MetersView(Context c){super(c);}
        @Override protected void onDraw(Canvas c){
            Ui.NeonPanel bgp=new Ui.NeonPanel(th,th.accent,.55f);bgp.scan=true;bgp.setBounds(0,0,getWidth(),getHeight());bgp.draw(c);
            if(snap==null){p.setColor(th.muted);p.setTextSize(Ui.dpf(11));p.setTypeface(Ui.MONO);c.drawText("SCANSIONE SISTEMA…",Ui.dpf(14),Ui.dpf(24),p);return;}
            float pad=Ui.dpf(12),w=getWidth(),half=(w-pad*3)/2;
            float ram=100f*(snap.ramTotal-snap.ramAvail)/Math.max(1,snap.ramTotal);
            block(c,pad,pad,half,"CPU "+(snap.cpuReal?"":"(frequenze)"),String.format(Locale.US,"%.0f%%",snap.cpu*100),snap.cpuHist,th.accent);
            block(c,pad*2+half,pad,half,"RAM "+SystemMonitor.fmtBytes(snap.ramTotal-snap.ramAvail)+" / "+SystemMonitor.fmtBytes(snap.ramTotal),
                String.format(Locale.US,"%.0f%%",ram),snap.ramHist,snap.lowMem?th.bad:th.secondary);
            // cores
            float cy=getHeight()-pad-Ui.dpf(16);int n=snap.curFreq.length;float cwid=(w-pad*2)/Math.max(1,n);
            p.setTextSize(Ui.dpf(8));p.setTypeface(Ui.MONO);
            for(int i=0;i<n;i++){float f=snap.maxFreq[i]>0?snap.curFreq[i]/(float)snap.maxFreq[i]:0;float x=pad+i*cwid;
                p.setColor(Theme.a(th.accent,40));c.drawRect(x+1,cy,x+cwid-Ui.dpf(3),cy+Ui.dpf(6),p);
                p.setColor(f>.8f?th.secondary:th.accent);c.drawRect(x+1,cy,x+1+(cwid-Ui.dpf(4))*f,cy+Ui.dpf(6),p);
                p.setColor(th.muted);c.drawText("C"+i+" "+(snap.curFreq[i]/1000)+"M",x+1,cy+Ui.dpf(15),p);}
            p.setColor(th.warn);p.setTextSize(Ui.dpf(9));
            String bt="BATT "+snap.battery+"% "+String.format(Locale.US,"%.1f°C",snap.batteryTemp)+(snap.charging?" ⚡":"")+"  ·  APP "+snap.apps.size();
            c.drawText(bt,w-pad-p.measureText(bt),cy-Ui.dpf(6),p);
        }
        private void block(Canvas c,float x,float y,float w,String label,String val,float[] hist,int col){
            p.setTypeface(Ui.MONO);p.setTextSize(Ui.dpf(9));p.setColor(th.muted);
            String l=label;while(p.measureText(l)>w&&l.length()>4)l=l.substring(0,l.length()-1);c.drawText(l,x,y+Ui.dpf(8),p);
            p.setTypeface(Ui.MONO_BOLD);p.setTextSize(Ui.dpf(22));p.setColor(col);c.drawText(val,x,y+Ui.dpf(32),p);
            float gx=x+Ui.dpf(70),gw=w-Ui.dpf(70),gy=y+Ui.dpf(14),gh=Ui.dpf(26);
            Path path=new Path();for(int i=0;i<hist.length;i++){float px=gx+gw*i/(hist.length-1f),py=gy+gh-gh*Math.min(100,hist[i])/100f;
                if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(1.5f));c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        }
    }
}
