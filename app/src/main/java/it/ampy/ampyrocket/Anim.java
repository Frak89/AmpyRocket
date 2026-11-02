package it.ampy.ampyrocket;

import android.content.Context;

/** Durations of every animation, user-tunable, scaled by a global speed factor. */
public final class Anim {
    private Anim(){}
    /** key, label, default ms */
    public static final Object[][] ITEMS={
        {"page","Scorrimento pagine Home",420},
        {"drawer_open","Apertura drawer app",380},
        {"drawer_close","Chiusura drawer app",300},
        {"tasks_open","Apertura task manager",260},
        {"tasks_close","Chiusura task manager",220},
        {"launch","Apertura app (genie / morph)",320},
        {"card_close","Chiusura card (genie, glitch, burn…)",480},
        {"card_flip","Rotazione card (flip)",360},
        {"card_snap","Scorrimento card",260},
        {"glitch","Decodifica lettere glitch",420},
        {"menu","Chiusura menu dopo il tocco",170},
        {"dropbar","Barra di trascinamento",150},
        {"unlock","Sblocco lockscreen",220},
        {"icon_press","Pressione icona",120},
    };
    public static final float[] SPEEDS={0f,.25f,.5f,.75f,1f,1.5f,2f,3f};
    public static final String[] SPEED_NAMES={"ISTANTANEO (nessuna animazione)","4× PIÙ VELOCE","2× PIÙ VELOCE","1,3× PIÙ VELOCE",
        "NORMALE","1,5× PIÙ LENTO","2× PIÙ LENTO","3× PIÙ LENTO"};
    public static final int[] STEPS={0,50,100,150,200,250,300,400,500,650,800,1000,1500,2000};

    public static int def(String key){for(Object[] o:ITEMS)if(o[0].equals(key))return (Integer)o[2];return 300;}
    /** Final duration in ms (custom value × global speed). */
    public static long ms(Context c,String key){
        Prefs pr=Prefs.get(c);
        float speed=pr.f("anim_speed",1f);
        int base=pr.i("anim_"+key,def(key));
        return Math.max(0,Math.round(base*speed));
    }
}
