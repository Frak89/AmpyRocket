package it.ampy.ampyrocket;

import android.content.Context;
import android.graphics.*;
import android.text.format.DateUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import java.util.*;

/**
 * Task cards in 3D: Vista Flip 3D, Linux depth stack, Cover Flow, Compiz cube, helix, exposé.
 * Swipe a card up to close it (genie / glitch / shrink / pixel-burn), long press flips it.
 */
public final class CardDeckView extends View {
    public interface Host{void open(String pkg);void kill(String pkg);void menu(String pkg,View anchor);}

    private static final class Card {
        String pkg,label;SystemMonitor.AppLoad load;Bitmap face,back;
        float flip=0,flipTarget=0;float closing=-1;
    }
    private final Theme th;private final Host host;private final List<Card> cards=new ArrayList<>();
    private final Camera cam=new Camera();private final Matrix m=new Matrix();
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),tp=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GestureDetector gd;private final Random rnd=new Random();
    private float pos=0,vel=0,target=-1,dragY=0;private boolean draggingY=false,draggingX=false;
    private float downX,downY,lastX;private String fx,closeFx;private boolean wobbly;private float wobble=0;
    private int cw,ch;private final float[] mesh=new float[(GenieFx.MW+1)*(GenieFx.MH+1)*2];
    private String emptyMsg="";

    public CardDeckView(Context c,Theme th,Host host){
        super(c);this.th=th;this.host=host;Prefs pr=Prefs.get(c);fx=pr.deckFx();closeFx=pr.closeFx();wobbly=pr.wobbly();
        tp.setTypeface(Ui.MONO);
        gd=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onSingleTapUp(MotionEvent e){tap(e.getX(),e.getY());return true;}
            @Override public void onLongPress(MotionEvent e){
                if(draggingX||draggingY)return;Card c0=focused();if(c0==null)return;
                Ui.longTick(CardDeckView.this);c0.flipTarget=c0.flipTarget>.5f?0:1;invalidate();}
            @Override public boolean onFling(MotionEvent e1,MotionEvent e2,float vx,float vy){
                if(draggingX){vel=-vx/Math.max(1,cw*1.1f)/60f;target=-1;invalidate();}return true;}
        });
    }

    public void setEffect(String e){fx=e;invalidate();}
    public void setCloseFx(String e){closeFx=e;}
    public void setEmptyMessage(String s){emptyMsg=s;invalidate();}

    public void setApps(List<SystemMonitor.AppLoad> apps){
        Map<String,Card> old=new HashMap<>();for(Card c:cards)old.put(c.pkg,c);
        List<Card> next=new ArrayList<>();AppRepository repo=AppRepository.get(getContext());
        for(SystemMonitor.AppLoad a:apps){
            if(a.pkg.equals(getContext().getPackageName()))continue;
            Card c=old.get(a.pkg);if(c!=null&&c.closing>=0)continue;
            if(c==null){c=new Card();c.pkg=a.pkg;c.label=repo.labelFor(a.pkg);}
            c.load=a;c.face=null;c.back=null;next.add(c);
        }
        for(Card c:cards)if(c.closing>=0)next.add(Math.min(next.size(),cards.indexOf(c)),c);
        cards.clear();cards.addAll(next);pos=Math.max(0,Math.min(pos,cards.size()-1));invalidate();
    }
    public List<String> packages(){List<String> l=new ArrayList<>();for(Card c:cards)if(c.closing<0)l.add(c.pkg);return l;}
    public void closeAll(){for(Card c:cards)if(c.closing<0){c.closing=0;}invalidate();}

    private Card focused(){int i=Math.round(pos);return i>=0&&i<cards.size()?cards.get(i):null;}

    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        cw=(int)(w*.62f);ch=(int)Math.min(h*.62f,cw*1.45f);for(Card c:cards){c.face=null;c.back=null;}
    }

    // ------------------------------------------------------------ rendering of a card
    private Bitmap face(Card c){
        if(c.face!=null)return c.face;
        Bitmap b=Bitmap.createBitmap(Math.max(1,cw),Math.max(1,ch),Bitmap.Config.ARGB_8888);Canvas k=new Canvas(b);
        Ui.NeonPanel bg=new Ui.NeonPanel(th,c.load.foreground?th.secondary:th.accent,.96f);bg.scan=true;bg.cut=Ui.dpf(16);
        bg.setBounds(0,0,cw,ch);bg.draw(k);
        float pad=Ui.dpf(14);AppRepository repo=AppRepository.get(getContext());
        int is=(int)(cw*.26f);
        Bitmap icon=repo.framed("task:"+c.pkg,repo.iconFor(c.pkg),is,th,Prefs.get(getContext()).s("icon_style","neon"));
        k.drawBitmap(icon,pad,pad+Ui.dpf(4),p);
        tp.setTypeface(Ui.MONO_BOLD);tp.setTextSize(Ui.dpf(15));tp.setColor(th.fg);
        float tx=pad*1.6f+is;k.drawText(ellipsize(c.label.toUpperCase(Locale.ROOT),cw-tx-pad),tx,pad+Ui.dpf(20),tp);
        tp.setTypeface(Ui.MONO);tp.setTextSize(Ui.dpf(9.5f));tp.setColor(th.muted);
        k.drawText(ellipsize(c.pkg,cw-tx-pad),tx,pad+Ui.dpf(36),tp);
        String state=(c.load.system?"⚙ SIS · ":"")+(c.load.foreground?"● IN PRIMO PIANO":c.load.fgService?"◆ SERVIZIO ATTIVO":"◇ IN BACKGROUND");
        tp.setColor(c.load.foreground?th.secondary:c.load.fgService?th.warn:th.accent);tp.setTypeface(Ui.MONO_BOLD);
        k.drawText(state,tx,pad+Ui.dpf(54),tp);
        // activity gauge + graph
        float gy=pad*2+is+Ui.dpf(14);
        tp.setTypeface(Ui.MONO);tp.setColor(th.muted);tp.setTextSize(Ui.dpf(9.5f));
        k.drawText("ATTIVITÀ (ultimi 15 min)",pad,gy,tp);
        tp.setTypeface(Ui.MONO_BOLD);tp.setTextSize(Ui.dpf(26));tp.setColor(th.accent);
        String pct=Math.round(c.load.activity*100)+"%";k.drawText(pct,pad,gy+Ui.dpf(30),tp);
        float gx=pad+tp.measureText(pct)+Ui.dpf(12),gw=cw-gx-pad,gh=Ui.dpf(30),gb=gy+Ui.dpf(30);
        graph(k,c.load.cpuHist,gx,gb-gh,gw,gh,th.accent,100);
        float by=gb+Ui.dpf(12);
        p.setColor(Theme.a(th.accent,40));k.drawRect(pad,by,cw-pad,by+Ui.dpf(6),p);
        p.setColor(th.accent);k.drawRect(pad,by,pad+(cw-pad*2)*c.load.activity,by+Ui.dpf(6),p);
        // stats rows
        tp.setTextSize(Ui.dpf(10.5f));float ry=by+Ui.dpf(30);
        row(k,"IN PRIMO PIANO",fmtDur(c.load.fgMs),pad,ry);ry+=Ui.dpf(19);
        row(k,"ULTIMO USO",c.load.lastUsed>0?DateUtils.getRelativeTimeSpanString(c.load.lastUsed,System.currentTimeMillis(),DateUtils.MINUTE_IN_MILLIS).toString():"—",pad,ry);ry+=Ui.dpf(19);
        row(k,"NOTIFICHE",String.valueOf(BadgeListener.count(c.pkg)),pad,ry);ry+=Ui.dpf(19);
        row(k,"LANCI DA AMPYROCKET",launches(c.pkg),pad,ry);
        tp.setTextSize(Ui.dpf(9));tp.setColor(Theme.a(th.muted,200));tp.setTypeface(Ui.MONO);
        String hint="TOCCA: APRI  ·  SU: CHIUDI  ·  TIENI: GIRA";
        k.drawText(hint,(cw-tp.measureText(hint))/2,ch-pad,tp);
        c.face=b;return b;
    }
    private Bitmap back(Card c){
        if(c.back!=null)return c.back;
        Bitmap b=Bitmap.createBitmap(Math.max(1,cw),Math.max(1,ch),Bitmap.Config.ARGB_8888);Canvas k=new Canvas(b);
        Ui.NeonPanel bg=new Ui.NeonPanel(th,th.secondary,.97f);bg.cut=Ui.dpf(16);bg.setBounds(0,0,cw,ch);bg.draw(k);
        float pad=Ui.dpf(16),y=pad+Ui.dpf(14);
        tp.setTypeface(Ui.MONO_BOLD);tp.setTextSize(Ui.dpf(13));tp.setColor(th.secondary);
        k.drawText("// DETTAGLI TASK",pad,y,tp);y+=Ui.dpf(26);
        tp.setTextSize(Ui.dpf(10.5f));
        row(k,"APP",c.label,pad,y);y+=Ui.dpf(19);
        row(k,"PACCHETTO","",pad,y);y+=Ui.dpf(15);
        tp.setColor(th.accent);tp.setTypeface(Ui.MONO);k.drawText(ellipsize(c.pkg,cw-pad*2),pad,y,tp);y+=Ui.dpf(22);
        boolean sys=AppRepository.get(getContext()).isSystem(c.pkg);
        row(k,"TIPO",sys?"SISTEMA":"UTENTE",pad,y);y+=Ui.dpf(19);
        row(k,"STATO",c.load.foreground?"PRIMO PIANO":c.load.fgService?"SERVIZIO":"BACKGROUND",pad,y);y+=Ui.dpf(19);
        row(k,"PRIMO PIANO 15m",fmtDur(c.load.fgMs),pad,y);y+=Ui.dpf(26);
        tp.setTypeface(Ui.MONO);tp.setTextSize(Ui.dpf(9.5f));tp.setColor(th.muted);
        for(String l:new String[]{"Android non consente a un launcher di","leggere CPU/RAM delle singole app:","il carico qui è l'attività d'uso.","I valori di sistema sono reali."}){k.drawText(l,pad,y,tp);y+=Ui.dpf(14);}
        tp.setTypeface(Ui.MONO_BOLD);tp.setColor(th.accent);tp.setTextSize(Ui.dpf(10));
        String hint="TOCCA: AZIONI  ·  TIENI: GIRA";k.drawText(hint,(cw-tp.measureText(hint))/2,ch-pad,tp);
        c.back=b;return b;
    }
    private String launches(String pkg){AppRepository r=AppRepository.get(getContext());AppRepository.App a=r.findPkg(pkg);return a==null?"0":String.valueOf(r.launches(a));}
    private void row(Canvas k,String l,String v,float x,float y){
        tp.setTypeface(Ui.MONO);tp.setColor(th.muted);k.drawText(l,x,y,tp);
        tp.setTypeface(Ui.MONO_BOLD);tp.setColor(th.fg);v=ellipsize(v,cw*.5f);k.drawText(v,cw-x-tp.measureText(v),y,tp);
    }
    private String ellipsize(String s,float w){if(tp.measureText(s)<=w)return s;while(s.length()>2&&tp.measureText(s+"…")>w)s=s.substring(0,s.length()-1);return s+"…";}
    private static String fmtDur(long ms){long s=ms/1000;return s<60?s+" s":s<3600?(s/60)+" min "+(s%60)+" s":(s/3600)+" h "+(s%3600/60)+" min";}
    private void graph(Canvas k,float[] h,float x,float y,float w,float hh,int col,float max){
        Path path=new Path(),fill=new Path();
        for(int i=0;i<h.length;i++){float px=x+w*i/(h.length-1f),py=y+hh-hh*Math.min(max,h[i])/max;
            if(i==0){path.moveTo(px,py);fill.moveTo(px,y+hh);fill.lineTo(px,py);}else{path.lineTo(px,py);fill.lineTo(px,py);}}
        fill.lineTo(x+w,y+hh);fill.close();
        Paint g=new Paint(Paint.ANTI_ALIAS_FLAG);g.setShader(new LinearGradient(0,y,0,y+hh,Theme.a(col,90),Theme.a(col,0),Shader.TileMode.CLAMP));
        k.drawPath(fill,g);g.setShader(null);g.setStyle(Paint.Style.STROKE);g.setStrokeWidth(Ui.dpf(1.5f));g.setColor(col);k.drawPath(path,g);
    }

    // ------------------------------------------------------------ layout of the deck
    /** Transform for card at offset o (0 = focused). Returns alpha 0..1, or -1 to skip. */
    private float transform(float o,int index,Matrix out){
        int w=getWidth(),h=getHeight();float cx=w/2f,cy=h/2f;float rx=0,ry=0,rz=0,tx=0,ty=0,tz=0,alpha=1,scale=1;
        switch(fx){
            case "flip3d":
                if(o<-1.2f||o>7)return -1;
                ry=-38;tx=o*cw*.22f-cw*.12f;ty=-o*ch*.06f;tz=o*cw*.55f;
                if(o<0){tz=o*cw*.8f;alpha=1+o;ty+=-o*ch*.3f;}
                break;
            case "stack":
                if(o<-1.2f||o>6)return -1;
                if(o>=0){ty=-o*Ui.dpf(34);tz=o*cw*.25f;alpha=1-o*.12f;rx=8;}
                else{ty=-o*h*.6f;rx=8-o*30;alpha=1+o;}
                break;
            case "coverflow":
                if(Math.abs(o)>4)return -1;
                float a=Math.max(-1,Math.min(1,o));ry=-a*60;tx=o*cw*.32f+a*cw*.32f;tz=Math.abs(a)*cw*.5f;
                break;
            case "cube":
                if(Math.abs(o)>1.05f)return -1;
                ry=-o*90;tx=(float)Math.sin(Math.toRadians(o*90))*cw*.5f;tz=(float)(1-Math.cos(Math.toRadians(o*90)))*cw*.5f+cw*.15f*(1-Math.abs(Math.abs(o)*2-1));
                alpha=1-Math.abs(o)*.3f;break;
            case "helix":
                if(Math.abs(o)>4)return -1;
                double ang=o*0.9;tx=(float)Math.sin(ang)*w*.33f;tz=(float)(1-Math.cos(ang))*cw*.9f;ty=o*ch*.28f;ry=(float)Math.toDegrees(-ang)*.7f;
                alpha=1-Math.abs(o)*.18f;break;
            case "expose":{
                int cols=2;float sw=w*.44f/cw;scale=sw;int row=index/cols,col=index%cols;
                float cellH=ch*sw+Ui.dpf(14);
                tx=(col-.5f)*(w*.47f);ty=row*cellH-pos*cellH*.5f-h*.5f+cellH*.5f+Ui.dpf(10);
                if(ty<-h||ty>h)return -1;break;}
            default:break;
        }
        cam.save();cam.setLocation(0,0,-14);cam.translate(0,0,tz);cam.rotateX(rx);cam.rotateY(ry);cam.rotateZ(rz);
        cam.getMatrix(out);cam.restore();
        out.preTranslate(-cw/2f,-ch/2f);out.postScale(scale,scale);out.postTranslate(cx+tx,cy+ty);
        return Math.max(0,Math.min(1,alpha));
    }

    @Override protected void onDraw(Canvas canvas){
        if(cw<=0)return;
        if(cards.isEmpty()){
            tp.setTypeface(Ui.MONO_BOLD);tp.setTextSize(Ui.dpf(13));tp.setColor(th.muted);
            String[] lines=emptyMsg.split("\n");float y=getHeight()/2f-lines.length*Ui.dpf(10);
            for(String l:lines){canvas.drawText(l,(getWidth()-tp.measureText(l))/2,y,tp);y+=Ui.dpf(20);}return;}
        boolean anim=physics();
        // draw order: farthest first
        List<Integer> order=new ArrayList<>();for(int i=0;i<cards.size();i++)order.add(i);
        Collections.sort(order,(a,b)->{
            if("expose".equals(fx))return Integer.compare(a,b);
            float oa=a-pos,ob=b-pos;
            if("flip3d".equals(fx)||"stack".equals(fx))return Float.compare(ob,oa);
            return Float.compare(Math.abs(ob),Math.abs(oa));});
        for(int idx:order){
            Card c=cards.get(idx);float o=idx-pos;
            float alpha=transform(o,idx,m);if(alpha<0)continue;
            boolean isFocus=idx==Math.round(pos)&&!"expose".equals(fx);
            if(isFocus&&dragY!=0)m.postTranslate(0,dragY);
            if(wobbly&&wobble!=0&&!"expose".equals(fx)){m.postSkew(wobble*.35f,0,getWidth()/2f,getHeight()/2f);}
            // flip
            float fl=c.flip;Bitmap bmp=fl>.5f?back(c):face(c);
            if(fl>0){cam.save();cam.setLocation(0,0,-14);cam.rotateY(fl>.5f?(fl-1)*180:fl*180);Matrix fm=new Matrix();cam.getMatrix(fm);cam.restore();
                fm.preTranslate(-cw/2f,-ch/2f);fm.postTranslate(cw/2f,ch/2f);m.preConcat(fm);}
            if(c.closing>=0){drawClosing(canvas,c,bmp,m,alpha);continue;}
            // reflection (Vista / coverflow)
            if(("flip3d".equals(fx)||"coverflow".equals(fx))&&alpha>.2f){
                canvas.save();canvas.concat(m);canvas.scale(1,-1,0,ch);
                p.setAlpha((int)(45*alpha));canvas.drawBitmap(bmp,0,0,p);canvas.restore();
            }
            canvas.save();canvas.concat(m);
            p.setAlpha((int)(255*alpha));canvas.drawBitmap(bmp,0,0,p);
            if(isFocus){Paint e=new Paint(Paint.ANTI_ALIAS_FLAG);e.setStyle(Paint.Style.STROKE);e.setStrokeWidth(Ui.dpf(2));
                e.setColor(Theme.a(th.accent,(int)(120+100*Math.sin(System.currentTimeMillis()/300.0))));
                canvas.drawRect(-Ui.dpf(4),-Ui.dpf(4),cw+Ui.dpf(4),ch+Ui.dpf(4),e);anim=true;}
            canvas.restore();
        }
        // counter
        tp.setTypeface(Ui.MONO_BOLD);tp.setTextSize(Ui.dpf(11));tp.setColor(th.accent);
        String cnt=String.format(Locale.US,"[%02d / %02d]",Math.min(cards.size(),Math.round(pos)+1),cards.size());
        canvas.drawText(cnt,(getWidth()-tp.measureText(cnt))/2,getHeight()-Ui.dpf(8),tp);
        if(dragY<-ch*.2f){tp.setColor(th.bad);String k="▲ RILASCIA PER CHIUDERE";canvas.drawText(k,(getWidth()-tp.measureText(k))/2,Ui.dpf(18),tp);}
        if(anim)postInvalidateOnAnimation();
    }

    private void drawClosing(Canvas canvas,Card c,Bitmap bmp,Matrix mm,float alpha){
        float t=c.closing;
        RectF r=new RectF(0,0,cw,ch);mm.mapRect(r);
        switch(closeFx){
            case "genie":{
                RectF small=new RectF(getWidth()/2f-Ui.dpf(14),getHeight()-Ui.dpf(30),getWidth()/2f+Ui.dpf(14),getHeight()-Ui.dpf(2));
                GenieFx.mesh(mesh,r,small,t);p.setAlpha((int)(255*alpha*(1-Math.max(0,t-.85f)*6)));
                canvas.drawBitmapMesh(bmp,GenieFx.MW,GenieFx.MH,mesh,0,null,0,p);
                p.setColor(Theme.a(th.secondary,(int)(200*(1-t))));canvas.drawOval(small,p);p.setColor(0xffffffff);break;}
            case "glitch":{
                canvas.save();canvas.concat(mm);int slices=14;float sh=ch/(float)slices;
                for(int i=0;i<slices;i++){canvas.save();canvas.clipRect(0,i*sh,cw,(i+1)*sh);
                    canvas.translate((rnd.nextFloat()-.5f)*cw*t*1.4f,0);p.setAlpha((int)(255*(1-t)));
                    if(rnd.nextInt(3)==0)p.setColorFilter(new PorterDuffColorFilter(Theme.a(rnd.nextBoolean()?th.accent:th.secondary,160),PorterDuff.Mode.SRC_ATOP));
                    canvas.drawBitmap(bmp,0,0,p);p.setColorFilter(null);canvas.restore();}
                canvas.restore();break;}
            case "burn":{
                canvas.save();canvas.concat(mm);int g=14;float bw=cw/(float)g,bh=ch/(float)(g*1.4f);Random rr=new Random(c.pkg.hashCode());
                for(float y=0;y<ch;y+=bh)for(float x=0;x<cw;x+=bw){
                    float th0=rr.nextFloat()*.8f+(1-y/ch)*.2f;if(t>th0)continue;
                    canvas.save();canvas.clipRect(x,y,x+bw+1,y+bh+1);p.setAlpha(255);
                    if(t>th0-.08f){p.setColorFilter(new PorterDuffColorFilter(th.warn,PorterDuff.Mode.SRC_ATOP));}
                    canvas.drawBitmap(bmp,0,0,p);p.setColorFilter(null);canvas.restore();}
                canvas.restore();break;}
            default:{ // shrink
                canvas.save();canvas.concat(mm);float s=1-t*.8f;canvas.scale(s,s,cw/2f,ch/2f);canvas.translate(0,-t*ch*.6f);
                p.setAlpha((int)(255*(1-t)));canvas.drawBitmap(bmp,0,0,p);canvas.restore();}
        }
        p.setAlpha(255);
    }

    /** Advances springs/animations; returns true while something moves. */
    private boolean physics(){
        boolean moving=false;
        if(!draggingX){
            if(Math.abs(vel)>.0005f){pos+=vel;vel*=.9f;moving=true;if(Math.abs(vel)<.01f){target=Math.round(pos);vel=0;}}
            else if(target<0)target=Math.round(pos);
            target=Math.max(0,Math.min(cards.size()-1,target));
            if(vel==0&&Math.abs(target-pos)>.001f){pos+=(target-pos)*.2f;moving=true;}else if(vel==0)pos=target;
        }
        if(!draggingY&&dragY!=0){dragY*=.75f;if(Math.abs(dragY)<1)dragY=0;moving=true;}
        if(wobble!=0){wobble*=-.82f;if(Math.abs(wobble)<.002f)wobble=0;moving=true;}
        Iterator<Card> it=cards.iterator();
        while(it.hasNext()){Card c=it.next();
            if(Math.abs(c.flip-c.flipTarget)>.01f){c.flip+=(c.flipTarget-c.flip)*.16f;moving=true;}else c.flip=c.flipTarget;
            if(c.closing>=0){c.closing+=.035f;moving=true;if(c.closing>=1){it.remove();host.kill(c.pkg);
                pos=Math.max(0,Math.min(pos,cards.size()-1));target=Math.round(pos);}}
        }
        return moving;
    }

    // ------------------------------------------------------------ input
    @Override public boolean onTouchEvent(MotionEvent e){
        gd.onTouchEvent(e);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=lastX=e.getX();downY=e.getY();vel=0;draggingX=false;draggingY=false;
                getParent().requestDisallowInterceptTouchEvent(true);break;
            case MotionEvent.ACTION_MOVE:{
                float dx=e.getX()-downX,dy=e.getY()-downY;
                if(!draggingX&&!draggingY){
                    if(Math.abs(dx)>Ui.dp(10)&&Math.abs(dx)>Math.abs(dy))draggingX=true;
                    else if(dy<-Ui.dp(10)&&Math.abs(dy)>Math.abs(dx)&&!"expose".equals(fx))draggingY=true;
                    else if(Math.abs(dy)>Ui.dp(10)&&"expose".equals(fx))draggingX=true;
                }
                if(draggingX){float d="expose".equals(fx)?-(e.getY()-downY):(lastX-e.getX());
                    if("expose".equals(fx)){pos=Math.max(0,Math.min(cards.size()-1,pos+(-(e.getY()-lastYForExpose(e)))/Math.max(1,ch*.25f)));}
                    else{pos+=d/(cw*1.1f);if(wobbly)wobble=Math.max(-.25f,Math.min(.25f,d/Ui.dpf(120)));}
                    lastX=e.getX();invalidate();}
                if(draggingY){dragY=Math.min(0,dy);invalidate();}
                break;}
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
                if(draggingY){Card f=focused();if(f!=null&&dragY<-ch*.3f){f.closing=0;Ui.buzz(getContext(),25);}draggingY=false;}
                if(draggingX){draggingX=false;if(Math.abs(vel)<.001f)target=Math.round(pos);}
                lastY=-1;invalidate();break;
        }
        return true;
    }
    private float lastY=-1;
    private float lastYForExpose(MotionEvent e){float r=lastY<0?e.getY():lastY;lastY=e.getY();return r;}

    private void tap(float x,float y){
        if(cards.isEmpty())return;
        // hit-test from nearest to farthest
        int best=-1;float bestDepth=1e9f;
        for(int i=0;i<cards.size();i++){
            float a=transform(i-pos,i,m);if(a<=0)continue;
            Matrix inv=new Matrix();if(!m.invert(inv))continue;float[] pt={x,y};inv.mapPoints(pt);
            if(pt[0]>=0&&pt[0]<=cw&&pt[1]>=0&&pt[1]<=ch){float depth="expose".equals(fx)?0:Math.abs(i-pos);
                if(depth<bestDepth){bestDepth=depth;best=i;}}
        }
        if(best<0)return;
        Card c=cards.get(best);
        if(best!=Math.round(pos)&&!"expose".equals(fx)){target=best;vel=0;Ui.tick(this);invalidate();return;}
        Ui.tick(this);
        if(c.flip>.5f)host.menu(c.pkg,this);else host.open(c.pkg);
    }
}
