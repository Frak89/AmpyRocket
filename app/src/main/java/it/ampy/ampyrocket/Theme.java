package it.ampy.ampyrocket;

import android.content.Context;

/** Neon palettes shared with AmpyRAW Cyberdark (same keys, same colours). */
public final class Theme {
    public static final String[] KEYS={"cyberpunk","vista","futuristic","retro","modern","bloodmoon","custom"};
    public static final String[] NAMES={"CYBERPUNK / NEON","WINDOWS VISTA / AERO","FUTURISTIC / GLASS",
        "RETRO / ANALOG","MODERNO / MINIMAL","BLOOD MOON / RED OPS","PERSONALIZZATO (16 bit)"};

    public final String key,label;
    public final int bg,panel,panel2,fg,muted,accent,secondary,warn,bad,top,bottom,border,grid,round;
    public final boolean hasGrid,isGlass;
    public String shape="chamfer";

    /** Editable colour slots of the custom theme: pref key, label. */
    public static final String[][] SLOTS={{"col_accent","Accento (neon principale)"},{"col_secondary","Secondario (neon 2)"},
        {"col_bg","Sfondo"},{"col_panel","Pannelli"},{"col_fg","Testo"},{"col_warn","Avvisi / batteria"}};

    private Theme(String key,String label,int bg,int panel,int panel2,int fg,int muted,int accent,
                  int secondary,int warn,int bad,int top,int bottom,int border,int grid,int round,
                  boolean hasGrid,boolean isGlass){
        this.key=key;this.label=label;this.bg=bg;this.panel=panel;this.panel2=panel2;this.fg=fg;
        this.muted=muted;this.accent=accent;this.secondary=secondary;this.warn=warn;this.bad=bad;
        this.top=top;this.bottom=bottom;this.border=border;this.grid=grid;this.round=round;
        this.hasGrid=hasGrid;this.isGlass=isGlass;
    }

    public static Theme of(String id){
        if("vista".equals(id))return new Theme("vista","WINDOWS VISTA / AERO",
            0xff071427,0xff143453,0xff24537b,0xfff5faff,0xffa9cade,
            0xff60d8ff,0xff83aaff,0xffffd389,0xffff6e93,
            0xff4d88b5,0xff102a49,0xff80b9df,0x1f9aefff,18,false,true);
        if("modern".equals(id))return new Theme("modern","MODERNO / MINIMAL",
            0xff121723,0xff202936,0xff293749,0xfff5f7fa,0xffa0adbf,
            0xff71b7ff,0xff8b9dae,0xffffc56d,0xffff7889,
            0xff293343,0xff151d28,0xff425669,0x00000000,12,false,false);
        if("retro".equals(id))return new Theme("retro","RETRO / ANALOG",
            0xff171910,0xff262719,0xff353423,0xfff2e6b4,0xffbaab78,
            0xffe5b64f,0xff91c66d,0xffffd075,0xfff07b59,
            0xff3b3724,0xff191a12,0xff766842,0x18d7bb68,2,true,false);
        if("futuristic".equals(id))return new Theme("futuristic","FUTURISTIC / GLASS",
            0xff0c1026,0xff1d234a,0xff293361,0xfff5f7ff,0xffbdc7ec,
            0xff9be7ff,0xffc797ff,0xffffd68a,0xffff809c,
            0xff3b457d,0xff10152f,0xff7885dc,0x2098aaff,23,true,true);
        if("bloodmoon".equals(id))return new Theme("bloodmoon","BLOOD MOON / RED OPS",
            0xff0d0507,0xff1f0b10,0xff2c1017,0xffffecef,0xffc48a95,
            0xffff2b4e,0xffffb000,0xffffd16a,0xffff6177,
            0xff1d080d,0xff080304,0xff5a1a26,0x18ff2b4e,4,true,false);
        return new Theme("cyberpunk","CYBERPUNK / NEON",
            0xff070b13,0xff101d30,0xff14263a,0xffecfcff,0xff86adbf,
            0xff00ffe2,0xffff3ac8,0xffffd16a,0xffff6177,
            0xff101c2d,0xff07101d,0xff214557,0x1237dcee,6,true,false);
    }

    public static Theme load(Context c){
        Prefs pr=Prefs.get(c);String id=pr.theme();
        Theme t="custom".equals(id)?custom(pr):of(id);t.shape=pr.s("button_shape","chamfer");return t;
    }

    /** Current value of a custom slot (falls back to the cyberpunk palette). */
    public static int slot(Prefs pr,String key){
        Theme d=of("cyberpunk");int def;
        switch(key){case "col_accent":def=d.accent;break;case "col_secondary":def=d.secondary;break;case "col_bg":def=d.bg;break;
            case "col_panel":def=d.panel;break;case "col_fg":def=d.fg;break;default:def=d.warn;}
        return pr.i(key,def)|0xff000000;
    }
    /** Copies a preset palette into the custom slots. */
    public static void seed(Prefs pr,Theme t){
        pr.put("col_accent",t.accent);pr.put("col_secondary",t.secondary);pr.put("col_bg",t.bg);
        pr.put("col_panel",t.panel);pr.put("col_fg",t.fg);pr.put("col_warn",t.warn);
    }
    private static Theme custom(Prefs pr){
        int acc=slot(pr,"col_accent"),sec=slot(pr,"col_secondary"),bg=slot(pr,"col_bg"),pan=slot(pr,"col_panel"),fg=slot(pr,"col_fg"),warn=slot(pr,"col_warn");
        return new Theme("custom","PERSONALIZZATO",bg,pan,mix(pan,fg,.12f),fg,mix(fg,bg,.45f),acc,sec,warn,0xffff6177,
            mix(bg,acc,.08f),mix(bg,0xff000000,.4f),mix(acc,bg,.6f),a(acc,0x14),6,true,false);
    }
    public static int mix(int x,int y,float f){
        int r=(int)(((x>>16)&255)*(1-f)+((y>>16)&255)*f),g=(int)(((x>>8)&255)*(1-f)+((y>>8)&255)*f),b=(int)((x&255)*(1-f)+(y&255)*f);
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    /** Quantises a colour to 16-bit RGB565 and back to ARGB. */
    public static int to565(int c){
        int r=(c>>16)&255,g=(c>>8)&255,b=c&255;int r5=r>>3,g6=g>>2,b5=b>>3;
        return 0xff000000|((r5<<3|r5>>2)<<16)|((g6<<2|g6>>4)<<8)|(b5<<3|b5>>2);
    }

    public int index(){for(int i=0;i<KEYS.length;i++)if(KEYS[i].equals(key))return i;return 0;}

    /** Colour with replaced alpha. */
    public static int a(int color,int alpha){return (color&0x00ffffff)|((alpha&0xff)<<24);}
}
