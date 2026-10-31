package it.ampy.ampyrocket;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;

import java.util.Locale;

/** Compact system HUD widget: CPU, RAM, battery, storage with live sparklines. */
public final class HudWidgetView extends View {
    private static HandlerThread worker;
    private final Theme th;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Ui.NeonPanel bg;private SystemMonitor.Snapshot snap;
    private final Handler ui=new Handler(android.os.Looper.getMainLooper());private Handler bgH;
    private final Runnable poll=new Runnable(){@Override public void run(){
        if(!isAttachedToWindow())return;
        bgH.post(()->{SystemMonitor.Snapshot s=SystemMonitor.get(getContext()).quick();ui.post(()->{snap=s;invalidate();});});
        ui.postDelayed(this,2000);
    }};

    public HudWidgetView(Context c,Theme th){
        super(c);this.th=th;bg=new Ui.NeonPanel(th,th.secondary,.42f);bg.scan=true;
        synchronized(HudWidgetView.class){if(worker==null){worker=new HandlerThread("ampy-hud");worker.start();}}
        bgH=new Handler(worker.getLooper());
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();ui.post(poll);}
    @Override protected void onDetachedFromWindow(){ui.removeCallbacks(poll);super.onDetachedFromWindow();}

    @Override protected void onDraw(Canvas c){
        int w=getWidth(),h=getHeight();
        bg.setBounds(Ui.dp(6),Ui.dp(3),w-Ui.dp(6),h-Ui.dp(3));bg.draw(c);
        if(snap==null)return;
        float pad=Ui.dpf(16),colW=(w-pad*2)/4f;
        float ram=100f*(snap.ramTotal-snap.ramAvail)/Math.max(1,snap.ramTotal);
        float sto=100f*(snap.storeTotal-snap.storeFree)/Math.max(1,snap.storeTotal);
        cell(c,pad,colW,h,"CPU"+(snap.cpuReal?"":"≈"),snap.cpu*100f,String.format(Locale.US,"%.0f%%",snap.cpu*100),snap.cpuHist,th.accent);
        cell(c,pad+colW,colW,h,"RAM",ram,SystemMonitor.fmtBytes(snap.ramTotal-snap.ramAvail),snap.ramHist,th.secondary);
        cell(c,pad+colW*2,colW,h,snap.charging?"BATT ⚡":"BATT",snap.battery,snap.battery+"% "+String.format(Locale.US,"%.0f°",snap.batteryTemp),null,
            snap.battery<20?th.bad:th.warn);
        cell(c,pad+colW*3,colW,h,"DISK",sto,SystemMonitor.fmtBytes(snap.storeFree)+" lib",null,th.accent);
    }

    private void cell(Canvas c,float x,float w,int h,String label,float pct,String value,float[] hist,int col){
        float top=Ui.dpf(12),inner=w-Ui.dpf(10);
        p.setTypeface(Ui.MONO);p.setTextSize(Ui.dpf(9));p.setColor(th.muted);c.drawText(label,x,top+Ui.dpf(8),p);
        p.setTypeface(Ui.MONO_BOLD);p.setTextSize(Ui.dpf(12));p.setColor(th.fg);
        String v=value;while(p.measureText(v)>inner&&v.length()>3)v=v.substring(0,v.length()-1);
        c.drawText(v,x,top+Ui.dpf(23),p);
        float by=h-Ui.dpf(14),bh=Ui.dpf(4);
        p.setColor(Theme.a(col,45));c.drawRect(x,by,x+inner,by+bh,p);
        p.setColor(col);c.drawRect(x,by,x+inner*Math.max(0,Math.min(100,pct))/100f,by+bh,p);
        // segment ticks
        p.setColor(Theme.a(th.bg,200));for(int i=1;i<10;i++){float tx=x+inner*i/10f;c.drawRect(tx,by,tx+Ui.dpf(1),by+bh,p);}
        if(hist!=null){
            Path path=new Path();float gy=by-Ui.dpf(4),gh=Ui.dpf(14);
            for(int i=0;i<hist.length;i++){float px=x+inner*i/(hist.length-1f),py=gy-gh*Math.min(100,hist[i])/100f;
                if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(1.2f));p.setColor(Theme.a(col,200));c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        }
    }
}
