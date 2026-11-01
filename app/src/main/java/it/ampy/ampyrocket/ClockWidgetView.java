package it.ampy.ampyrocket;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.*;
import android.os.BatteryManager;
import android.provider.AlarmClock;
import android.text.format.DateFormat;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

/** Home clock widget: glitch-decoding digits, seconds ring, battery arc, next alarm. */
public final class ClockWidgetView extends View {
    private final Theme th;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GlitchTextView.Scrambler time=new GlitchTextView.Scrambler(),date=new GlitchTextView.Scrambler();
    private final Random r=new Random();
    private String lastTime="";private int battVal=0;private String alarmVal=null;private long burstUntil=0;private final boolean big;
    private final Runnable tick=new Runnable(){@Override public void run(){
        update();long now=System.currentTimeMillis();
        if(Power.secondsClock(getContext()))postDelayed(this,1000-now%1000);else postDelayed(this,60000-now%60000+20);}};
    private long battAt=0;
    private final Ui.NeonPanel bg;

    public ClockWidgetView(Context c,Theme th,boolean big){
        super(c);this.th=th;this.big=big;bg=new Ui.NeonPanel(th,th.accent,.42f);bg.scan=true;
        setOnClickListener(v->{Ui.tick(v);try{getContext().startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Throwable ignored){}});
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();update();post(tick);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow();}
    public void glitch(){time.replay();date.replay();burstUntil=System.currentTimeMillis()+380;postInvalidateOnAnimation();}

    private void update(){
        if(System.currentTimeMillis()-battAt>30000){battVal=battery();alarmVal=nextAlarm();battAt=System.currentTimeMillis();}
        boolean h24=DateFormat.is24HourFormat(getContext());
        String t=new SimpleDateFormat(h24?"HH:mm":"hh:mm",Locale.getDefault()).format(Calendar.getInstance().getTime());
        if(!t.equals(lastTime)){
            boolean first=lastTime.isEmpty();lastTime=t;time.set(t,true);
            date.set(new SimpleDateFormat("EEE dd MMM yyyy",Locale.getDefault()).format(Calendar.getInstance().getTime()).toUpperCase(Locale.getDefault()),first);
            burstUntil=System.currentTimeMillis()+300;
        }else if(Prefs.get(getContext()).glitch()>1&&Power.idleGlitch(getContext())&&r.nextInt(25)==0)burstUntil=System.currentTimeMillis()+120;
        invalidate();
    }

    @Override protected void onDraw(Canvas c){
        int w=getWidth(),h=getHeight();
        if(!big){bg.setBounds(Ui.dp(6),Ui.dp(4),w-Ui.dp(6),h-Ui.dp(4));bg.draw(c);}
        long now=System.currentTimeMillis();boolean glitch=now<burstUntil;
        Calendar cal=Calendar.getInstance();
        boolean showSec=Power.secondsClock(getContext());
        float sec=showSec?cal.get(Calendar.SECOND)+(Power.smoothClock(getContext())?cal.get(Calendar.MILLISECOND)/1000f:0):0;
        // ring
        float ringR=big?Math.min(w,h)*.42f:h*.36f;
        float rcx=big?w/2f:w-ringR-Ui.dpf(22),rcy=big?h*.42f:h/2f;
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.BUTT);
        p.setStrokeWidth(Ui.dpf(1));p.setColor(Theme.a(th.accent,60));
        for(int i=0;i<60;i++){double a=Math.toRadians(i*6-90);float r1=ringR-Ui.dpf(i%5==0?9:5);
            c.drawLine((float)(rcx+Math.cos(a)*r1),(float)(rcy+Math.sin(a)*r1),(float)(rcx+Math.cos(a)*ringR),(float)(rcy+Math.sin(a)*ringR),p);}
        p.setStrokeWidth(Ui.dpf(3));p.setColor(th.accent);
        RectF ro=new RectF(rcx-ringR,rcy-ringR,rcx+ringR,rcy+ringR);
        if(showSec){c.drawArc(ro,-90,sec*6,false,p);
            double sa=Math.toRadians(sec*6-90);p.setStyle(Paint.Style.FILL);p.setColor(th.fg);
            c.drawCircle((float)(rcx+Math.cos(sa)*ringR),(float)(rcy+Math.sin(sa)*ringR),Ui.dpf(4),p);}
        // battery arc (inner)
        int batt=battVal;float br=ringR-Ui.dpf(14);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(4));p.setColor(Theme.a(th.secondary,50));
        RectF bi=new RectF(rcx-br,rcy-br,rcx+br,rcy+br);c.drawArc(bi,135,270,false,p);
        p.setColor(batt<20?th.bad:th.secondary);c.drawArc(bi,135,270*batt/100f,false,p);
        p.setStyle(Paint.Style.FILL);
        // texts
        String ts=time.frame();
        float tsz=big?ringR*.55f:h*.40f;
        p.setTypeface(Ui.MONO_BOLD);p.setTextSize(tsz);p.setLetterSpacing(.02f);
        float tw=p.measureText(ts);
        float tx=big?rcx-tw/2f:Ui.dpf(22),ty=big?rcy+tsz*.35f:h*.56f;
        float split=glitch?Ui.dpf(2+r.nextInt(5)):Ui.dpf(1.4f);
        p.setColor(Theme.a(th.secondary,210));c.drawText(ts,tx-split,ty,p);
        p.setColor(Theme.a(th.accent,210));c.drawText(ts,tx+split,ty,p);
        if(glitch){
            for(int i=0;i<4;i++){float y0=ty-tsz+r.nextFloat()*tsz,bh=Ui.dpf(2+r.nextInt(8));
                c.save();c.clipRect(0,y0,w,y0+bh);c.translate((r.nextFloat()-.5f)*Ui.dpf(26),0);
                p.setColor(th.fg);c.drawText(ts,tx,ty,p);c.restore();}
        }
        p.setColor(th.fg);c.drawText(ts,tx,ty,p);
        // seconds digits
        p.setTextSize(tsz*.28f);p.setColor(th.accent);
        String ss=showSec?String.format(Locale.US,"%02d",(int)sec):"";
        if(big)c.drawText(ss,rcx-p.measureText(ss)/2,ty+tsz*.42f,p);
        else c.drawText(ss,tx+tw+Ui.dpf(6),ty,p);
        // date + labels
        p.setTypeface(Ui.MONO);p.setTextSize(big?Ui.dpf(15):Ui.dpf(12));p.setLetterSpacing(.18f);p.setColor(th.fg);
        String ds=date.frame();
        if(big)c.drawText(ds,(w-p.measureText(ds))/2,rcy+ringR+Ui.dpf(34),p);
        else c.drawText(ds,tx,ty+Ui.dpf(22),p);
        p.setTextSize(Ui.dpf(9.5f));p.setColor(th.secondary);
        String hdr="// AMPYROCKET.CHRONO  ◢ BATT "+batt+"%";
        if(big)c.drawText(hdr,(w-p.measureText(hdr))/2,rcy-ringR-Ui.dpf(14),p);
        else c.drawText(hdr,tx,ty-tsz-Ui.dpf(4),p);
        String alarm=alarmVal;
        if(alarm!=null){p.setColor(th.warn);String a="⏰ "+alarm;
            if(big)c.drawText(a,(w-p.measureText(a))/2,rcy+ringR+Ui.dpf(56),p);else c.drawText(a,tx,ty+Ui.dpf(38),p);}
        p.setLetterSpacing(0);
        if(time.active()||date.active()||glitch)postInvalidateOnAnimation();
        else if(Power.smoothClock(getContext()))postInvalidateDelayed(100);
    }

    private int battery(){
        try{Intent i=getContext().registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(i==null)return 0;int l=i.getIntExtra(BatteryManager.EXTRA_LEVEL,0),s=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);return s>0?l*100/s:l;}
        catch(Throwable t){return 0;}
    }
    private String nextAlarm(){
        try{AlarmManager am=(AlarmManager)getContext().getSystemService(Context.ALARM_SERVICE);
            AlarmManager.AlarmClockInfo i=am.getNextAlarmClock();if(i==null)return null;
            return new SimpleDateFormat("EEE HH:mm",Locale.getDefault()).format(i.getTriggerTime()).toUpperCase(Locale.getDefault());}
        catch(Throwable t){return null;}
    }
}
