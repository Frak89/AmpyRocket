package it.ampy.ampyrocket;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * iOS-style "liquid glass": blurred backdrop, lens refraction + chromatic split on the rim
 * (AGSL on Android 13+), specular highlight and an iridescent edge.
 */
public final class LiquidGlass extends Drawable {
    // ------------------------------------------------------------------ backdrop snapshot
    private static Bitmap backdrop;private static float bdScale=.25f;private static int bdOx,bdOy;

    /** Captures + blurs what is currently on screen in this activity (call right before showing a menu). */
    public static void capture(Context c){
        if(!(c instanceof Activity))return;
        try{
            View root=((Activity)c).getWindow().getDecorView();int w=root.getWidth(),h=root.getHeight();if(w==0||h==0)return;
            int bw=Math.max(1,(int)(w*bdScale)),bh=Math.max(1,(int)(h*bdScale));
            Bitmap b=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);Canvas cv=new Canvas(b);
            Theme th=Theme.load(c);cv.drawColor(th.bg);
            try{Drawable wp=WallpaperManager.getInstance(c).getDrawable();
                if(wp!=null){int iw=Math.max(1,wp.getIntrinsicWidth()),ih=Math.max(1,wp.getIntrinsicHeight());
                    float s=Math.max(bw/(float)iw,bh/(float)ih);int dw=(int)(iw*s),dh=(int)(ih*s);
                    wp.setBounds((bw-dw)/2,(bh-dh)/2,(bw+dw)/2,(bh+dh)/2);wp.draw(cv);}}
            catch(Throwable ignored){}
            cv.save();cv.scale(bdScale,bdScale);root.draw(cv);cv.restore();
            boxBlur(b,Math.max(2,(int)(Ui.dpf(7)*bdScale*4/4)));boxBlur(b,Math.max(2,(int)(Ui.dpf(7)*bdScale)));
            int[] loc=new int[2];root.getLocationOnScreen(loc);bdOx=loc[0];bdOy=loc[1];
            backdrop=b;
        }catch(Throwable t){backdrop=null;}
    }

    /** Blurs the whole screen behind a dialog window (Android 12+, when the device allows it). */
    public static void blurBehind(Window w){
        if(w==null||Build.VERSION.SDK_INT<31)return;
        try{WindowManager wm=w.getWindowManager();if(!wm.isCrossWindowBlurEnabled())return;
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams lp=w.getAttributes();lp.setBlurBehindRadius(Ui.dp(22));lp.dimAmount=.18f;
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);w.setAttributes(lp);}catch(Throwable ignored){}
    }

    private static void boxBlur(Bitmap b,int r){
        int w=b.getWidth(),h=b.getHeight();int[] px=new int[w*h],tmp=new int[w*h];b.getPixels(px,0,w,0,0,w,h);
        pass(px,tmp,w,h,r,true);pass(tmp,px,w,h,r,false);b.setPixels(px,0,w,0,0,w,h);
    }
    private static void pass(int[] in,int[] out,int w,int h,int r,boolean horiz){
        int len=horiz?w:h,lines=horiz?h:w;
        for(int l=0;l<lines;l++){
            int sa=0,sr=0,sg=0,sb=0;int n=0;
            for(int i=-r;i<=r;i++){int idx=Math.max(0,Math.min(len-1,i));int c=in[horiz?l*w+idx:idx*w+l];
                sa+=c>>>24;sr+=(c>>16)&255;sg+=(c>>8)&255;sb+=c&255;n++;}
            for(int i=0;i<len;i++){
                out[horiz?l*w+i:i*w+l]=((sa/n)<<24)|((sr/n)<<16)|((sg/n)<<8)|(sb/n);
                int o=Math.max(0,Math.min(len-1,i-r)),a=Math.max(0,Math.min(len-1,i+r+1));
                int co=in[horiz?l*w+o:o*w+l],ca=in[horiz?l*w+a:a*w+l];
                sa+=(ca>>>24)-(co>>>24);sr+=((ca>>16)&255)-((co>>16)&255);sg+=((ca>>8)&255)-((co>>8)&255);sb+=(ca&255)-(co&255);
            }
        }
    }

    // ------------------------------------------------------------------ AGSL refraction (API 33+)
    private static final String AGSL=
        "uniform shader image;uniform float2 size;uniform float2 origin;uniform float scale;uniform float radius;"+
        "uniform float refr;uniform float chroma;"+
        "float sd(float2 p,float2 b,float r){float2 q=abs(p)-b+r;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;}"+
        "half4 main(float2 xy){float2 c=size*0.5;float2 p=xy-c;float d=sd(p,c,radius);"+
        "float band=clamp(1.0+d/refr,0.0,1.0);float k=band*band*band;"+
        "float2 g=float2(sd(p+float2(1,0),c,radius)-sd(p-float2(1,0),c,radius),sd(p+float2(0,1),c,radius)-sd(p-float2(0,1),c,radius));"+
        "float2 n=normalize(g+float2(0.0001,0.0001));"+
        "float2 base=(origin+xy+n*k*refr*0.85)*scale;float2 ch=n*k*chroma*scale;"+
        "half r=image.eval(base+ch).r;half gg=image.eval(base).g;half b=image.eval(base-ch).b;"+
        "half4 col=half4(r,gg,b,1.0);col.rgb+=half3(k*0.10);return col;}";

    // ------------------------------------------------------------------ drawable
    private final Theme th;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Path path=new Path();private final RectF rf=new RectF();private final float radius;
    private final boolean useBackdrop;private Object shader;private Bitmap shaderFor;
    public boolean selected=false;

    public LiquidGlass(Theme th,float radiusDp,boolean useBackdrop){this.th=th;this.radius=Ui.dpf(radiusDp);this.useBackdrop=useBackdrop;}

    @Override public void draw(Canvas c){
        Rect b=getBounds();if(b.isEmpty())return;
        rf.set(b.left+1,b.top+1,b.right-1,b.bottom-1);path.reset();path.addRoundRect(rf,radius,radius,Path.Direction.CW);
        // soft outer shadow
        p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(0x33000000);
        c.save();c.translate(0,Ui.dpf(3));c.drawPath(path,p);c.restore();
        c.save();c.clipPath(path);
        Bitmap bd=backdrop;boolean drew=false;
        if(useBackdrop&&bd!=null&&!bd.isRecycled()){
            int[] loc=new int[2];View host=getCallback() instanceof View?(View)getCallback():null;
            if(host!=null)host.getLocationOnScreen(loc);
            float ox=loc[0]-bdOx+b.left,oy=loc[1]-bdOy+b.top;
            if(Build.VERSION.SDK_INT>=33){drew=drawAgsl(c,bd,b,ox,oy);}
            if(!drew){
                BitmapShader bs=new BitmapShader(bd,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
                Matrix m=new Matrix();m.setScale(1/bdScale,1/bdScale);m.postTranslate(-ox+b.left,-oy+b.top);bs.setLocalMatrix(m);
                p.setShader(bs);c.drawRect(b,p);p.setShader(null);drew=true;
            }
        }
        // tint
        p.setColor(Theme.a(th.panel,drew?110:200));c.drawRect(b,p);
        if(selected){p.setColor(Theme.a(th.accent,40));c.drawRect(b,p);}
        // specular sheen + bottom glow
        p.setShader(new LinearGradient(0,b.top,0,b.top+b.height()*.45f,0x40ffffff,0x00ffffff,Shader.TileMode.CLAMP));c.drawRect(b,p);
        p.setShader(new LinearGradient(0,b.bottom-b.height()*.3f,0,b.bottom,0x00000000,Theme.a(th.accent,28),Shader.TileMode.CLAMP));c.drawRect(b,p);
        p.setShader(null);
        c.restore();
        // iridescent rim
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Ui.dpf(1.6f));
        p.setShader(new SweepGradient(rf.centerX(),rf.centerY(),new int[]{Theme.a(th.accent,210),0x88ffffff,Theme.a(th.secondary,210),
            Theme.a(th.warn,170),0x66ffffff,Theme.a(th.accent,210)},null));
        c.drawPath(path,p);
        p.setShader(null);p.setStrokeWidth(Ui.dpf(.8f));p.setColor(0x40ffffff);
        RectF in=new RectF(rf);in.inset(Ui.dpf(2),Ui.dpf(2));c.drawRoundRect(in,radius-Ui.dpf(2),radius-Ui.dpf(2),p);
        p.setStyle(Paint.Style.FILL);
    }

    private boolean drawAgsl(Canvas c,Bitmap bd,Rect b,float ox,float oy){
        try{
            if(shader==null||shaderFor!=bd){
                android.graphics.RuntimeShader rs=new android.graphics.RuntimeShader(AGSL);
                rs.setInputShader("image",new BitmapShader(bd,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));
                shader=rs;shaderFor=bd;
            }
            android.graphics.RuntimeShader rs=(android.graphics.RuntimeShader)shader;
            rs.setFloatUniform("size",b.width(),b.height());rs.setFloatUniform("origin",ox,oy);
            rs.setFloatUniform("scale",bdScale);rs.setFloatUniform("radius",radius);
            rs.setFloatUniform("refr",Ui.dpf(18));rs.setFloatUniform("chroma",Ui.dpf(5));
            c.save();c.translate(b.left,b.top);p.setShader(rs);c.drawRect(0,0,b.width(),b.height(),p);p.setShader(null);c.restore();
            return true;
        }catch(Throwable t){shader=null;return false;}
    }

    @Override public boolean getPadding(Rect r){return false;}
    @Override public void getOutline(android.graphics.Outline o){o.setRoundRect(getBounds(),radius);}
    @Override public void setAlpha(int a){}
    @Override public void setColorFilter(ColorFilter f){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}

    /** Menu style preference: liquid glass (default) or classic neon panel. */
    public static boolean enabled(Context c){return "glass".equals(Prefs.get(c).s("menu_style","glass"));}
}
