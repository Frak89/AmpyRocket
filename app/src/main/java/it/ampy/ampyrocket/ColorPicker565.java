package it.ampy.ampyrocket;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;

import java.util.Locale;

/** 16-bit colour picker: RGB565 sliders (32 × 64 × 32 = 65.536 colours) + quick palette grid. */
public final class ColorPicker565 {
    public interface Done{void on(int argb);}
    private ColorPicker565(){}

    public static void show(Context c,Theme th,String title,int initial,Done done){
        if(LiquidGlass.enabled(c))LiquidGlass.capture(c);
        Dialog d=new Dialog(c);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box=new LinearLayout(c);box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(CyberMenu.panel(c,th,th.accent));box.setPadding(Ui.dp(16),Ui.dp(14),Ui.dp(16),Ui.dp(14));
        box.addView(Ui.text(c,"// "+title.toUpperCase(Locale.ROOT),12,th.secondary,true));

        int[] rgb={(initial>>19)&31,(initial>>10)&63,(initial>>3)&31};
        View swatch=new View(c);TextView hex=Ui.text(c,"",13,th.fg,true);hex.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout top=new LinearLayout(c);top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(swatch,new LinearLayout.LayoutParams(Ui.dp(64),Ui.dp(48)));
        hex.setPadding(Ui.dp(14),0,0,0);top.addView(hex,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,-2);tl.topMargin=Ui.dp(10);box.addView(top,tl);

        SeekBar[] bars=new SeekBar[3];String[] names={"R","G","B"};int[] max={31,63,31};int[] tint={0xffff3355,0xff33ff77,0xff3388ff};
        Runnable refresh=()->{
            int col=argb(rgb);
            GradientDrawable g=new GradientDrawable();g.setColor(col);g.setCornerRadius(Ui.dpf(6));g.setStroke(Ui.dp(2),th.fg);swatch.setBackground(g);
            int v565=(rgb[0]<<11)|(rgb[1]<<5)|rgb[2];
            hex.setText(String.format(Locale.US,"#%06X\nRGB565 0x%04X\nR%d G%d B%d",col&0xffffff,v565,rgb[0],rgb[1],rgb[2]));
        };
        for(int i=0;i<3;i++){
            LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(Ui.text(c,names[i]+(i==1?" 6b":" 5b"),11,tint[i],true),new LinearLayout.LayoutParams(Ui.dp(42),-2));
            SeekBar sb=new SeekBar(c);sb.setMax(max[i]);sb.setProgress(rgb[i]);
            sb.setProgressTintList(ColorStateList.valueOf(tint[i]));sb.setThumbTintList(ColorStateList.valueOf(tint[i]));
            final int k=i;sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar s,int p,boolean u){rgb[k]=p;refresh.run();}
                public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
            bars[i]=sb;row.addView(sb,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams rl=new LinearLayout.LayoutParams(-1,-2);rl.topMargin=Ui.dp(6);box.addView(row,rl);
        }

        // quick palette: 12 hues × 4 shades + 12 greys, all quantised to 16 bit
        GridLayout grid=new GridLayout(c);grid.setColumnCount(12);
        int cell=Ui.dp(22);
        for(int row=0;row<5;row++)for(int h=0;h<12;h++){
            int col;
            if(row<4){float[] sv={new float[]{1f,.85f,.55f,1f}[row],new float[]{1f,1f,1f,.55f}[row]};
                col=Color.HSVToColor(new float[]{h*30f,sv[0],sv[1]});}
            else{int v=h*255/11;col=0xff000000|(v<<16)|(v<<8)|v;}
            int q=Theme.to565(col);
            View sw=new View(c);GradientDrawable g=new GradientDrawable();g.setColor(q);g.setCornerRadius(Ui.dpf(3));sw.setBackground(g);
            sw.setOnClickListener(v->{Ui.tick(v);rgb[0]=(q>>19)&31;rgb[1]=(q>>10)&63;rgb[2]=(q>>3)&31;
                for(int i=0;i<3;i++)bars[i].setProgress(rgb[i]);refresh.run();});
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(new ViewGroup.LayoutParams(cell,cell));lp.setMargins(Ui.dp(1),Ui.dp(1),Ui.dp(1),Ui.dp(1));
            grid.addView(sw,lp);
        }
        LinearLayout.LayoutParams gl=new LinearLayout.LayoutParams(-2,-2);gl.topMargin=Ui.dp(12);gl.gravity=Gravity.CENTER_HORIZONTAL;box.addView(grid,gl);

        LinearLayout btns=new LinearLayout(c);btns.setGravity(Gravity.END);
        btns.addView(Ui.button(c,th,"ANNULLA",v->d.dismiss()));
        View sp=new View(c);btns.addView(sp,new LinearLayout.LayoutParams(Ui.dp(10),1));
        btns.addView(Ui.button(c,th,"APPLICA",v->{d.dismiss();done.on(argb(rgb));}));
        LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,-2);bl.topMargin=Ui.dp(14);box.addView(btns,bl);
        refresh.run();
        ScrollView sv=new ScrollView(c);sv.addView(box);d.setContentView(sv);
        Window w=d.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Math.min(Ui.dp(340),(int)(c.getResources().getDisplayMetrics().widthPixels*.94f)),ViewGroup.LayoutParams.WRAP_CONTENT);LiquidGlass.blurBehind(w);}
        d.show();
    }

    private static int argb(int[] rgb){
        int r=rgb[0]<<3|rgb[0]>>2,g=rgb[1]<<2|rgb[1]>>4,b=rgb[2]<<3|rgb[2]>>2;
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
}
