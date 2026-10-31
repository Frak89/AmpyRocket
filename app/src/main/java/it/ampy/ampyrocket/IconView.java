package it.ampy.ampyrocket;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/** One launcher icon: neon framed bitmap, glitch-decoding label, notification badge. */
public final class IconView extends View {
    public String itemKey;                 // app key or folder:<id>
    public AppRepository.App app;          // null for folders
    public LayoutStore.Folder folder;
    private Bitmap bmp;private final Bitmap[] mini=new Bitmap[4];
    private final TextPaint tp=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final GlitchTextView.Scrambler sc=new GlitchTextView.Scrambler();
    private final Theme th;private final boolean showLabel;private final float scale;private final String style;
    private int badge=0;private float press=0;private boolean pressed=false;private boolean dimmed=false;

    public IconView(Context c,Theme th,boolean showLabel){
        super(c);this.th=th;this.showLabel=showLabel;Prefs pr=Prefs.get(c);scale=pr.iconScale();
        style=pr.s("icon_style","neon");
        tp.setTypeface(Ui.MONO);tp.setTextSize(Ui.dpf(10.5f));tp.setColor(th.fg);tp.setLetterSpacing(.04f);
        tp.setShadowLayer(Ui.dpf(3),0,0,0xff000000);
        setClickable(true);setLongClickable(true);setHapticFeedbackEnabled(true);
    }

    public void bindApp(AppRepository.App a){
        app=a;folder=null;itemKey=a.key;setContentDescription(a.label);sc.set(a.label,Prefs.get(getContext()).glitch()>0);
        bmp=null;requestLayout();invalidate();
    }
    public void bindFolder(LayoutStore.Folder f,List<AppRepository.App> apps){
        folder=f;app=null;itemKey="folder:"+f.id;setContentDescription(f.name);sc.set(f.name,true);
        for(int i=0;i<4;i++)mini[i]=null;
        AppRepository repo=AppRepository.get(getContext());int n=0;
        for(AppRepository.App a:apps){if(n>=4)break;mini[n++]=repo.framed(a.key,a.icon,(int)Ui.dpf(22),th,"plain");}
        bmp=null;invalidate();
    }
    public void setBadge(int n){if(n!=badge){badge=n;invalidate();}}
    public void setDimmed(boolean d){dimmed=d;invalidate();}
    public void decode(){sc.replay();postInvalidateOnAnimation();}
    public String label(){return app!=null?app.label:folder!=null?folder.name:"";}

    public int iconSize(){
        int w=getWidth(),h=getHeight()-(showLabel?(int)Ui.dpf(18):0);
        return (int)(Math.min(w,h)*.74f*scale);
    }
    /** Screen rect of the icon (for launch animations). */
    public Rect iconRect(){
        int s=iconSize();int[] loc=new int[2];getLocationOnScreen(loc);
        int l=loc[0]+(getWidth()-s)/2,t=loc[1]+top(s);return new Rect(l,t,l+s,t+s);
    }
    private int top(int s){int labelH=showLabel?(int)Ui.dpf(18):0;return Math.max(0,(getHeight()-labelH-s)/2);}
    public Bitmap iconBitmap(){ensure();return bmp;}

    private void ensure(){
        int s=iconSize();if(s<=0)return;
        if(bmp!=null&&bmp.getWidth()==s)return;
        AppRepository repo=AppRepository.get(getContext());
        if(app!=null)bmp=repo.framed(app.key,app.icon,s,th,style);
        else if(folder!=null)bmp=folderBitmap(s);
    }
    private Bitmap folderBitmap(int s){
        Bitmap b=Bitmap.createBitmap(s,s,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
        Path sh=IconFactory.shape("neon".equals(style)||"plain".equals(style)?"neon":style,s*.07f,s*.07f,s*.93f,s*.93f);
        Paint f=new Paint(Paint.ANTI_ALIAS_FLAG);f.setColor(Theme.a(th.panel,200));c.drawPath(sh,f);
        f.setStyle(Paint.Style.STROKE);f.setStrokeWidth(s*.03f);f.setColor(th.secondary);c.drawPath(sh,f);
        float cell=s*.34f,gap=s*.06f,ox=(s-cell*2-gap)/2f;
        Paint bp=new Paint(Paint.FILTER_BITMAP_FLAG|Paint.ANTI_ALIAS_FLAG);
        for(int i=0;i<4;i++){if(mini[i]==null)continue;float x=ox+(i%2)*(cell+gap),y=ox+(i/2)*(cell+gap);
            c.drawBitmap(mini[i],null,new RectF(x,y,x+cell,y+cell),bp);}
        return b;
    }

    @Override protected void onSizeChanged(int w,int h,int ow,int oh){bmp=null;}

    @Override protected void onDraw(Canvas c){
        ensure();
        int s=iconSize();int x=(getWidth()-s)/2,y=top(s);
        float k=1f-press*.12f;
        c.save();c.scale(k,k,getWidth()/2f,y+s/2f);
        if(dimmed)p.setAlpha(90);else p.setAlpha(255);
        if(bmp!=null){
            if(press>0){ // chromatic split while pressed
                p.setColorFilter(new PorterDuffColorFilter(Theme.a(th.secondary,200),PorterDuff.Mode.SRC_ATOP));
                c.drawBitmap(bmp,x-Ui.dpf(3)*press,y,p);
                p.setColorFilter(new PorterDuffColorFilter(Theme.a(th.accent,200),PorterDuff.Mode.SRC_ATOP));
                c.drawBitmap(bmp,x+Ui.dpf(3)*press,y,p);p.setColorFilter(null);
            }
            c.drawBitmap(bmp,x,y,p);
        }
        c.restore();
        if(badge>0&&Prefs.get(getContext()).badges()){
            float r=Ui.dpf(8.5f),bx=x+s-r*.6f,by=y+r*.6f;
            p.setColor(th.secondary);c.drawCircle(bx,by,r,p);
            p.setColor(th.bg);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(1.5f));c.drawCircle(bx,by,r,p);p.setStyle(Paint.Style.FILL);
            p.setColor(0xffffffff);p.setTextSize(Ui.dpf(9.5f));p.setTypeface(Ui.MONO_BOLD);p.setTextAlign(Paint.Align.CENTER);
            c.drawText(badge>99?"99":String.valueOf(badge),bx,by+Ui.dpf(3.4f),p);p.setTextAlign(Paint.Align.LEFT);
        }
        if(showLabel){
            String s0=sc.frame();
            CharSequence lab=TextUtils.ellipsize(s0,tp,getWidth()-Ui.dpf(4),TextUtils.TruncateAt.END);
            float tw=tp.measureText(lab,0,lab.length());
            float ty=y+s+Ui.dpf(13);
            if(sc.active()){tp.setColor(th.accent);c.drawText(lab,0,lab.length(),(getWidth()-tw)/2f+Ui.dpf(1),ty,tp);}
            tp.setColor(dimmed?th.muted:th.fg);c.drawText(lab,0,lab.length(),(getWidth()-tw)/2f,ty,tp);
            if(sc.active())postInvalidateOnAnimation();
        }
        if(pressed&&press<1f){press=Math.min(1f,press+.2f);postInvalidateOnAnimation();}
        else if(!pressed&&press>0){press=Math.max(0f,press-.12f);postInvalidateOnAnimation();}
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        int a=e.getActionMasked();
        if(a==MotionEvent.ACTION_DOWN){pressed=true;invalidate();}
        else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){pressed=false;invalidate();}
        return super.onTouchEvent(e);
    }

    public Drawable iconDrawable(){return app!=null?app.icon:null;}
}
