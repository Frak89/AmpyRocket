package it.ampy.ampyrocket;

import android.graphics.*;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/** Renders launcher icons inside cyberpunk frames (chamfered, hex, circle or plain). */
public final class IconFactory {
    private IconFactory(){}
    public static final String[] STYLES={"neon","hex","circle","plain"};
    public static final String[] STYLE_NAMES={"CORNICE NEON SMUSSATA","ESAGONO HUD","CERCHIO GLOW","ORIGINALE"};

    public static Bitmap render(Drawable d,int size,Theme th,String style){
        Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
        if("plain".equals(style)){drawIcon(c,d,0,0,size,size,false);return b;}
        float pad=size*.07f;
        Path shape=shape(style,pad,pad,size-pad,size-pad);
        Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG);glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(size*.05f);glow.setColor(Theme.a(th.accent,150));
        glow.setMaskFilter(new BlurMaskFilter(size*.06f,BlurMaskFilter.Blur.NORMAL));
        c.drawPath(shape,glow);
        Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(0,0,size,size,Theme.a(th.panel2,235),Theme.a(th.bg,245),Shader.TileMode.CLAMP));
        c.drawPath(shape,fill);
        // icon content, clipped to the shape (adaptive icons fill edge to edge)
        c.save();c.clipPath(shape);
        boolean adaptive=d instanceof AdaptiveIconDrawable;
        float inset=adaptive?size*.02f:size*.2f;
        drawIcon(c,d,(int)(pad+inset),(int)(pad+inset),(int)(size-pad-inset),(int)(size-pad-inset),adaptive);
        // scanline sheen
        Paint sl=new Paint();sl.setColor(0x14000000);
        for(float y=pad;y<size-pad;y+=size*.04f)c.drawRect(0,y,size,y+size*.012f,sl);
        Paint sheen=new Paint(Paint.ANTI_ALIAS_FLAG);
        sheen.setShader(new LinearGradient(0,pad,0,size*.5f,0x30ffffff,0x00ffffff,Shader.TileMode.CLAMP));
        c.drawRect(0,0,size,size*.5f,sheen);
        c.restore();
        Paint edge=new Paint(Paint.ANTI_ALIAS_FLAG);edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1.5f,size*.025f));edge.setColor(th.accent);c.drawPath(shape,edge);
        if("neon".equals(style)){
            edge.setColor(th.secondary);edge.setStrokeWidth(Math.max(2f,size*.04f));
            float k=size*.16f;
            c.drawLine(size-pad,pad,size-pad,pad+k,edge);c.drawLine(pad,size-pad,pad+k,size-pad,edge);
        }
        return b;
    }

    static Path shape(String style,float l,float t,float r,float b){
        Path p=new Path();float w=r-l,h=b-t;
        if("hex".equals(style)){
            float cx=(l+r)/2,cy=(t+b)/2,rad=Math.min(w,h)/2;
            for(int i=0;i<6;i++){double a=Math.PI/3*i;float x=(float)(cx+rad*Math.cos(a)),y=(float)(cy+rad*Math.sin(a)*.95);
                if(i==0)p.moveTo(x,y);else p.lineTo(x,y);}p.close();
        }else if("circle".equals(style)){p.addCircle((l+r)/2,(t+b)/2,Math.min(w,h)/2,Path.Direction.CW);}
        else{float k=w*.2f;p.moveTo(l+k,t);p.lineTo(r,t);p.lineTo(r,b-k);p.lineTo(r-k,b);p.lineTo(l,b);p.lineTo(l,t+k);p.close();}
        return p;
    }

    private static void drawIcon(Canvas c,Drawable d,int l,int t,int r,int b,boolean adaptive){
        if(d==null)return;
        if(adaptive){
            AdaptiveIconDrawable ad=(AdaptiveIconDrawable)d;
            int w=r-l,h=b-t;int ex=(int)(w*.25f),ey=(int)(h*.25f);
            // draw layers bigger so the 72/108 safe zone fills the frame
            Drawable bg=ad.getBackground(),fg=ad.getForeground();
            if(bg!=null){bg.setBounds(l-ex,t-ey,r+ex,b+ey);bg.draw(c);}
            if(fg!=null){fg.setBounds(l-ex,t-ey,r+ex,b+ey);fg.draw(c);}
            return;
        }
        Rect old=d.copyBounds();d.setBounds(l,t,r,b);d.draw(c);d.setBounds(old);
    }
}
