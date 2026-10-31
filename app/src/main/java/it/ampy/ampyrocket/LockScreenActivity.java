package it.ampy.ampyrocket;

import android.app.Activity;
import android.app.KeyguardManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.*;
import android.widget.*;

import java.util.List;

/**
 * Cyberpunk lock cover shown over the system keyguard. Swipe up to unlock:
 * the system PIN / fingerprint is still required (requestDismissKeyguard).
 */
public final class LockScreenActivity extends Activity implements BadgeListener.Listener {
    private Theme th;private LinearLayout notes;private View content;private float downY;private CyberBackgroundView bg;
    private ClockWidgetView clock;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);th=Theme.load(this);
        setShowWhenLocked(true);setTurnScreenOn(true);
        Window w=getWindow();w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        bg=new CyberBackgroundView(this);root.addView(bg);
        LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(18),Ui.dp(48),Ui.dp(18),Ui.dp(28));content=col;root.addView(col);
        GlitchTextView hdr=new GlitchTextView(this,th,12,th.secondary,true);hdr.setText("// AMPYROCKET SECURE LINK");col.addView(hdr);
        clock=new ClockWidgetView(this,th,true);clock.setOnClickListener(null);clock.setClickable(false);
        col.addView(clock,new LinearLayout.LayoutParams(-1,Ui.dp(330)));
        notes=new LinearLayout(this);notes.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv=new ScrollView(this);sv.addView(notes);col.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        GlitchTextView hint=new GlitchTextView(this,th,13,th.accent,true);hint.setText("▲  SCORRI IN SU PER SBLOCCARE  ▲");col.addView(hint);
        setContentView(root);
        root.setOnTouchListener((v,e)->{
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:downY=e.getRawY();return true;
                case MotionEvent.ACTION_MOVE:float d=Math.min(0,e.getRawY()-downY);content.setTranslationY(d);content.setAlpha(1+d/(v.getHeight()*.6f));return true;
                case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                    if(downY-e.getRawY()>v.getHeight()*.22f)unlock();
                    else content.animate().translationY(0).alpha(1).setDuration(200).start();return true;
            }
            return false;
        });
        BadgeListener.addListener(this);onBadgesChanged();
    }

    private void unlock(){
        Ui.buzz(this,20);clock.glitch();bg.pulse();
        content.animate().translationY(-content.getHeight()).alpha(0).setDuration(220).withEndAction(()->{
            KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
            if(km!=null&&km.isKeyguardLocked())km.requestDismissKeyguard(this,null);
            finish();overridePendingTransition(0,android.R.anim.fade_out);
        }).start();
    }

    @Override public void onBadgesChanged(){
        notes.removeAllViews();
        if(!Prefs.get(this).b("lock_notifications",true))return;
        List<BadgeListener.Item> f=BadgeListener.feed();AppRepository repo=AppRepository.get(this);
        int n=0;for(BadgeListener.Item it:f){if(n++>=6)break;
            LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(Ui.dp(14),Ui.dp(10),Ui.dp(14),Ui.dp(10));
            r.setBackground(new Ui.NeonPanel(th,th.accent,.55f));
            r.addView(Ui.text(this,repo.labelFor(it.pkg).toUpperCase()+"  ·  "+DateUtils.getRelativeTimeSpanString(it.when),10,th.secondary,true));
            TextView t=Ui.text(this,it.title,13,th.fg,true);t.setPadding(0,Ui.dp(4),0,0);t.setSingleLine(true);r.addView(t);
            if(!it.text.isEmpty()){TextView x=Ui.text(this,it.text,11.5f,th.muted,false);x.setMaxLines(2);r.addView(x);}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=Ui.dp(6);notes.addView(r,lp);}
    }

    @Override protected void onResume(){super.onResume();bg.setRunning(true);}
    @Override protected void onPause(){super.onPause();bg.setRunning(false);}
    @Override protected void onDestroy(){BadgeListener.removeListener(this);super.onDestroy();}
    @Override public void onBackPressed(){if(getIntent().getBooleanExtra("preview",false))finish();}
}
