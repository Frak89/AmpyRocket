package it.ampy.ampyrocket;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** One home page: full-width widgets (N rows each) on top, then the icon grid. */
public final class GridPage extends ViewGroup {
    public final int cols,rows;
    public GridPage(Context c,int cols,int rows){super(c);this.cols=cols;this.rows=rows;setClipChildren(false);}

    public void addWidget(View v,int rowSpan){v.setTag(R_SPAN,rowSpan);addView(v);}
    public void addIcon(View v){v.setTag(R_SPAN,0);addView(v);}
    private static final int R_SPAN=0x7f0a0001;

    public int cellW(){return getWidth()/cols;}
    public int cellH(){return getHeight()/rows;}

    @Override protected void onMeasure(int w,int h){
        int W=MeasureSpec.getSize(w),H=MeasureSpec.getSize(h);int cw=W/cols,ch=H/rows;
        for(int i=0;i<getChildCount();i++){View v=getChildAt(i);int span=span(v);
            if(span>0)v.measure(MeasureSpec.makeMeasureSpec(W,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ch*span,MeasureSpec.EXACTLY));
            else v.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ch,MeasureSpec.EXACTLY));}
        setMeasuredDimension(W,H);
    }
    @Override protected void onLayout(boolean c,int l,int t,int r,int b){
        int W=r-l,cw=W/cols,ch=(b-t)/rows,row=0,cell=0;
        for(int i=0;i<getChildCount();i++){View v=getChildAt(i);int span=span(v);
            if(span>0){v.layout(0,row*ch,W,(row+span)*ch);row+=span;}
            else{int cr=row+cell/cols,cc=cell%cols;if(cr>=rows){v.layout(0,0,0,0);cell++;continue;}
                v.layout(cc*cw,cr*ch,(cc+1)*cw,(cr+1)*ch);cell++;}
        }
    }
    private static int span(View v){Object o=v.getTag(R_SPAN);return o instanceof Integer?(Integer)o:0;}

    /** Icon slot index under a point (for drag & drop), or -1. */
    public int slotAt(float x,float y,int widgetRows){
        int cw=Math.max(1,cellW()),ch=Math.max(1,cellH());int r=(int)(y/ch)-widgetRows,c=(int)(x/cw);
        if(r<0||c<0||c>=cols)return -1;return r*cols+c;
    }
}
