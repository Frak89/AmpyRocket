package it.ampy.ampyrocket;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.OverScroller;

import java.util.Random;

/** Horizontal home pager with 3D page transitions (cube, flip, stack, carousel...). */
public final class PagedWorkspace extends ViewGroup {
    public interface Listener{void onPageScroll(float pos);void onPageSelected(int page);}
    private final OverScroller scroller;private VelocityTracker vt;
    private final int touchSlop,minFling;private float downX,downY,lastX;private boolean dragging;
    private int current=0;private String fx;private Listener listener;private final Random rnd=new Random();

    public PagedWorkspace(Context c){
        super(c);scroller=new OverScroller(c);ViewConfiguration vc=ViewConfiguration.get(c);
        touchSlop=vc.getScaledTouchSlop();minFling=vc.getScaledMinimumFlingVelocity()*4;
        fx=Prefs.get(c).pageFx();setClipChildren(false);
    }
    public void setListener(Listener l){listener=l;}
    public void setEffect(String e){fx=e;applyFx();}
    public int current(){return current;}
    public int count(){return getChildCount();}

    @Override protected void onMeasure(int w,int h){
        int W=MeasureSpec.getSize(w),H=MeasureSpec.getSize(h);
        for(int i=0;i<getChildCount();i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(W,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(H,MeasureSpec.EXACTLY));
        setMeasuredDimension(W,H);
    }
    @Override protected void onLayout(boolean ch,int l,int t,int r,int b){
        int w=r-l;for(int i=0;i<getChildCount();i++)getChildAt(i).layout(i*w,0,(i+1)*w,b-t);
        current=Math.min(current,Math.max(0,getChildCount()-1));scrollTo(current*w,0);applyFx();
    }

    public void snapTo(int page,boolean anim){
        page=Math.max(0,Math.min(getChildCount()-1,page));int w=getWidth();
        boolean changed=page!=current;current=page;
        if(anim){scroller.startScroll(getScrollX(),0,page*w-getScrollX(),0,420);postInvalidateOnAnimation();}
        else{scrollTo(page*w,0);applyFx();}
        if(changed&&listener!=null)listener.onPageSelected(page);
    }

    @Override public void computeScroll(){
        if(scroller.computeScrollOffset()){scrollTo(scroller.getCurrX(),0);applyFx();postInvalidateOnAnimation();}
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=lastX=e.getX();downY=e.getY();dragging=!scroller.isFinished();
                if(dragging)scroller.abortAnimation();break;
            case MotionEvent.ACTION_MOVE:
                float dx=Math.abs(e.getX()-downX),dy=Math.abs(e.getY()-downY);
                if(dx>touchSlop&&dx>dy*1.3f){dragging=true;lastX=e.getX();getParent().requestDisallowInterceptTouchEvent(true);}
                break;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:dragging=false;break;
        }
        return dragging;
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(vt==null)vt=VelocityTracker.obtain();vt.addMovement(e);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:downX=lastX=e.getX();return true;
            case MotionEvent.ACTION_MOVE:{
                float dx=lastX-e.getX();lastX=e.getX();int max=(getChildCount()-1)*getWidth();
                float nx=getScrollX()+dx;if(nx<0||nx>max)dx*=.35f;
                scrollBy((int)dx,0);applyFx();return true;}
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:{
                vt.computeCurrentVelocity(1000);float v=vt.getXVelocity();vt.recycle();vt=null;
                int w=Math.max(1,getWidth());int target=Math.round(getScrollX()/(float)w);
                if(Math.abs(v)>minFling)target=v<0?(int)Math.floor(getScrollX()/(float)w)+1:(int)Math.ceil(getScrollX()/(float)w)-1;
                snapTo(target,true);dragging=false;return true;}
        }
        return super.onTouchEvent(e);
    }

    /** Applies the selected transition to every page depending on its distance from the viewport. */
    private void applyFx(){
        int w=getWidth();if(w==0)return;
        float pos=getScrollX()/(float)w;if(listener!=null)listener.onPageScroll(pos);
        for(int i=0;i<getChildCount();i++){
            View v=getChildAt(i);float o=i-pos; // -1..1 visible
            v.setTranslationX(0);v.setRotationY(0);v.setRotationX(0);v.setRotation(0);v.setScaleX(1);v.setScaleY(1);v.setAlpha(1);
            v.setCameraDistance(w*8);v.setPivotX(w/2f);v.setPivotY(v.getHeight()/2f);
            if(Math.abs(o)>=1f){v.setAlpha(Math.abs(o)>=1.5f?0:1);continue;}
            switch(fx){
                case "cube":v.setPivotX(o<0?w:0);v.setRotationY(o*90f);break;
                case "flip":v.setTranslationX(-o*w);v.setRotationY(o*180f);v.setAlpha(Math.abs(o)<.5f?1:0);break;
                case "stack":if(o>0){v.setTranslationX(-o*w);float s=1-.25f*o;v.setScaleX(s);v.setScaleY(s);v.setAlpha(1-o);}break;
                case "carousel":v.setRotationY(o*-45f);v.setScaleX(1-.3f*Math.abs(o));v.setScaleY(1-.3f*Math.abs(o));v.setTranslationX(-o*w*.25f);break;
                case "rotate":v.setPivotY(v.getHeight()*1.4f);v.setRotation(o*-25f);break;
                case "zoom":{float s=1-.4f*Math.abs(o);v.setScaleX(s);v.setScaleY(s);v.setAlpha(1-Math.abs(o));break;}
                case "tablet":v.setPivotX(o<0?w:0);v.setScaleX(1-Math.abs(o));break;
                case "glitch":{float a=Math.abs(o);v.setAlpha(1-a*.6f);
                    if(a>.05f&&a<.95f)v.setTranslationX((rnd.nextFloat()-.5f)*w*.06f);
                    v.setScaleY(1+(rnd.nextFloat()-.5f)*.04f*a);break;}
                default:break;
            }
        }
    }
}
