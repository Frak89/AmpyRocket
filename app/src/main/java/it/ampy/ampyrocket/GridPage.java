package it.ampy.ampyrocket;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * One home page. Portrait: full-width widgets (N rows each) on top, icon grid below.
 * Landscape: widgets stacked in a left column, icon grid on the right.
 */
public final class GridPage extends ViewGroup {
    public final int cols,rows;public final boolean land;
    private int iconCols,iconRows;private float iconLeft,iconTop,cw,ch;
    public GridPage(Context c,int cols,int rows,boolean landscape){super(c);this.cols=cols;this.rows=rows;this.land=landscape;setClipChildren(false);}

    public void addWidget(View v,int rowSpan){v.setTag(R_SPAN,rowSpan);addView(v);}
    public void addIcon(View v){v.setTag(R_SPAN,0);addView(v);}
    private static final int R_SPAN=0x7f0a0001;

    public int cellW(){return (int)cw;}
    public int cellH(){return (int)ch;}
    public int iconCols(){return iconCols;}

    private int widgetRows(){int r=0;for(int i=0;i<getChildCount();i++)r+=span(getChildAt(i));return r;}
    private int iconCount(){int n=0;for(int i=0;i<getChildCount();i++)if(span(getChildAt(i))==0)n++;return n;}

    private void compute(int W,int H){
        int wr=widgetRows();
        if(!land){cw=W/(float)cols;ch=H/(float)rows;iconCols=cols;iconRows=rows-wr;iconLeft=0;iconTop=wr*ch;return;}
        float wArea=wr>0?W*.42f:0;
        iconRows=Math.max(3,Math.min(rows,cols-1));
        int cap=Math.max(cols*rows-wr*cols,iconCount());
        iconCols=Math.max(4,(int)Math.ceil(cap/(float)iconRows));
        float areaW=W-wArea;cw=areaW/iconCols;ch=H/(float)iconRows;
        if(cw<ch*.75f){ch=cw/.75f;} // keep cells reasonable
        iconLeft=wArea;iconTop=0;
    }

    @Override protected void onMeasure(int w,int h){
        int W=MeasureSpec.getSize(w),H=MeasureSpec.getSize(h);compute(W,H);int wr=Math.max(1,widgetRows());
        for(int i=0;i<getChildCount();i++){View v=getChildAt(i);int span=span(v);
            if(span>0){int ww=land?(int)(W*.42f):W,hh=land?H*span/wr:(int)(H/(float)rows*span);
                v.measure(MeasureSpec.makeMeasureSpec(ww,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(hh,MeasureSpec.EXACTLY));}
            else v.measure(MeasureSpec.makeMeasureSpec((int)cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((int)ch,MeasureSpec.EXACTLY));}
        setMeasuredDimension(W,H);
    }
    @Override protected void onLayout(boolean c,int l,int t,int r,int b){
        int W=r-l,H=b-t;compute(W,H);int wr=Math.max(1,widgetRows());float wy=0;int cell=0;
        for(int i=0;i<getChildCount();i++){View v=getChildAt(i);int span=span(v);
            if(span>0){int hh=land?H*span/wr:(int)(H/(float)rows*span);int ww=land?(int)(W*.42f):W;
                v.layout(0,(int)wy,ww,(int)wy+hh);wy+=hh;}
            else{int cr=cell/iconCols,cc=cell%iconCols;cell++;
                if(cr>=iconRows){v.layout(0,0,0,0);continue;}
                int x=(int)(iconLeft+cc*cw),y=(int)(iconTop+cr*ch);v.layout(x,y,(int)(x+cw),(int)(y+ch));}
        }
    }
    private static int span(View v){Object o=v.getTag(R_SPAN);return o instanceof Integer?(Integer)o:0;}

    /** Icon slot index under a point (for drag & drop), or -1. */
    public int slotAt(float x,float y,int ignoredWidgetRows){
        if(cw<=0||ch<=0)return -1;
        int c=(int)((x-iconLeft)/cw),r=(int)((y-iconTop)/ch);
        if(x<iconLeft||r<0||c<0||c>=iconCols||r>=iconRows)return -1;return r*iconCols+c;
    }
    /** Centre of an icon slot in page coordinates. */
    public float[] slotCenter(int slot){return new float[]{iconLeft+(slot%iconCols+.5f)*cw,iconTop+(slot/iconCols+.5f)*ch};}
    public int capacity(){return iconCols*iconRows;}
}
