package it.ampy.ampyrocket;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.text.Normalizer;
import java.util.*;

/** Full screen A–Z app drawer: search (+ calculator, web), sort modes, glitch letter fast-scroller. */
public final class AppDrawerView extends FrameLayout {
    public interface Host{void launch(IconView v,AppRepository.App a);void appMenu(IconView v,AppRepository.App a);void closeDrawer();}

    private final Theme th;private final Host host;private final GridView grid;private final EditText search;
    private final GlitchTextView title,bigLetter;private final TextView calc;private final LetterBar bar;
    private final List<AppRepository.App> shown=new ArrayList<>();private final Adapter adapter=new Adapter();
    private String sort;

    public AppDrawerView(Context c,Theme th,Host host){
        super(c);this.th=th;this.host=host;sort=Prefs.get(c).drawerSort();
        setBackgroundColor(Theme.a(th.bg,235));setClickable(true);
        LinearLayout col=new LinearLayout(c);col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(12),Ui.dp(8),Ui.dp(12),0);
        addView(col,new LayoutParams(-1,-1));

        LinearLayout head=new LinearLayout(c);head.setGravity(Gravity.CENTER_VERTICAL);
        title=new GlitchTextView(c,th,15,th.accent,true);title.alignLeft();
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView sortBtn=Ui.text(c,"⇅ ORDINA",11,th.secondary,true);sortBtn.setPadding(Ui.dp(10),Ui.dp(10),Ui.dp(10),Ui.dp(10));
        sortBtn.setOnClickListener(v->{Ui.tick(v);String[] k={"az","used","recent","new"};
            String[] n={"A → Z","PIÙ USATE","USATE DI RECENTE","INSTALLATE DI RECENTE"};
            int sel=Arrays.asList(k).indexOf(sort);
            CyberMenu.choose(c,th,"Ordina app",n,sel,i->{sort=k[i];Prefs.get(c).put("drawer_sort",sort);refresh();});});
        head.addView(sortBtn);
        col.addView(head);

        search=new EditText(c);search.setHint("⌕  cerca app, calcola, web…");search.setSingleLine(true);
        search.setTypeface(Ui.MONO);search.setTextSize(14);search.setTextColor(th.fg);search.setHintTextColor(th.muted);
        search.setBackground(new Ui.NeonPanel(th,th.accent,.7f));search.setPadding(Ui.dp(14),Ui.dp(12),Ui.dp(14),Ui.dp(12));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,-2);sl.topMargin=Ui.dp(6);col.addView(search,sl);
        calc=Ui.text(c,"",13,th.warn,true);calc.setPadding(Ui.dp(14),Ui.dp(8),Ui.dp(14),Ui.dp(4));calc.setVisibility(GONE);
        col.addView(calc);

        FrameLayout body=new FrameLayout(c);
        LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,0,1);bl.topMargin=Ui.dp(8);col.addView(body,bl);
        grid=new GridView(c);grid.setNumColumns(Prefs.get(c).columns());grid.setVerticalSpacing(Ui.dp(6));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));grid.setAdapter(adapter);grid.setClipToPadding(false);
        grid.setPadding(0,0,Ui.dp(26),Ui.dp(40));grid.setVerticalScrollBarEnabled(false);
        body.addView(grid,new LayoutParams(-1,-1));
        bar=new LetterBar(c);body.addView(bar,new LayoutParams(Ui.dp(24),-1,Gravity.END));
        bigLetter=new GlitchTextView(c,th,64,th.accent,true);bigLetter.setVisibility(GONE);
        body.addView(bigLetter,new LayoutParams(-2,-2,Gravity.CENTER));

        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int d){}
            public void onTextChanged(CharSequence s,int a,int b,int d){}
            public void afterTextChanged(Editable e){refresh();}
        });
        search.setOnEditorActionListener((v,id,ev)->{
            if(!shown.isEmpty()){AppRepository.App a=shown.get(0);host.launch(null,a);return true;}
            String q=search.getText().toString().trim();if(q.isEmpty())return false;
            try{Intent i=new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY,q);
                getContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Throwable t){Ui.toast(getContext(),"Nessuna app di ricerca");}
            return true;
        });
    }

    public void open(boolean focusSearch){
        search.setText("");refresh();title.glitchNow();grid.setSelection(0);
        if(focusSearch){search.requestFocus();
            InputMethodManager im=(InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            postDelayed(()->im.showSoftInput(search,0),150);}
    }
    public void hideKeyboard(){
        InputMethodManager im=(InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        im.hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();
    }
    public boolean atTop(){return grid.getChildCount()==0||grid.getFirstVisiblePosition()==0&&grid.getChildAt(0).getTop()>=0;}

    private static String norm(String s){return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}

    public void refresh(){
        AppRepository repo=AppRepository.get(getContext());
        String q=norm(search.getText().toString().trim());
        List<AppRepository.App> all=repo.visible();shown.clear();
        for(AppRepository.App a:all){
            if(q.isEmpty()||norm(a.label).contains(q)||a.pkg().toLowerCase(Locale.ROOT).contains(q))shown.add(a);
        }
        if(!q.isEmpty())Collections.sort(shown,(x,y)->Boolean.compare(!norm(x.label).startsWith(q),!norm(y.label).startsWith(q)));
        else switch(sort){
            case "used":Collections.sort(shown,(x,y)->Integer.compare(repo.launches(y),repo.launches(x)));break;
            case "recent":Collections.sort(shown,(x,y)->Long.compare(repo.lastLaunch(y),repo.lastLaunch(x)));break;
            case "new":Collections.sort(shown,(x,y)->Long.compare(y.installed,x.installed));break;
            default:break;
        }
        String r=MathEval.tryEval(search.getText().toString());
        if(r!=null){calc.setText("= "+r);calc.setVisibility(VISIBLE);}else calc.setVisibility(GONE);
        title.setText("// APP DRAWER ["+shown.size()+"]");
        bar.setVisibility(q.isEmpty()&&"az".equals(sort)?VISIBLE:GONE);
        adapter.notifyDataSetChanged();
    }

    private final class Adapter extends BaseAdapter{
        public int getCount(){return shown.size();}
        public Object getItem(int i){return shown.get(i);}
        public long getItemId(int i){return i;}
        public View getView(int i,View cv,ViewGroup parent){
            IconView v=cv instanceof IconView?(IconView)cv:new IconView(getContext(),th,true);
            int cw=Math.max(1,parent.getWidth()-parent.getPaddingRight())/Math.max(1,Prefs.get(getContext()).columns());
            v.setLayoutParams(new AbsListView.LayoutParams(-1,(int)(cw*1.15f)));
            AppRepository.App a=shown.get(i);v.bindApp(a);v.setBadge(BadgeListener.count(a.pkg()));
            v.setOnClickListener(x->host.launch(v,a));
            v.setOnLongClickListener(x->{Ui.longTick(x);host.appMenu(v,a);return true;});
            return v;
        }
    }

    /** Vertical A–Z bar; dragging shows a giant glitch letter and jumps the grid. */
    private final class LetterBar extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final String L="#ABCDEFGHIJKLMNOPQRSTUVWXYZ";private int hot=-1;
        LetterBar(Context c){super(c);p.setTypeface(Ui.MONO_BOLD);p.setTextAlign(Paint.Align.CENTER);}
        @Override protected void onDraw(Canvas c){
            float h=getHeight()/(float)L.length();p.setTextSize(Math.min(h*.8f,Ui.dpf(11)));
            for(int i=0;i<L.length();i++){p.setColor(i==hot?th.secondary:Theme.a(th.accent,170));
                c.drawText(String.valueOf(L.charAt(i)),getWidth()/2f,h*i+h*.75f,p);}
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            int i=(int)(e.getY()/(getHeight()/(float)L.length()));i=Math.max(0,Math.min(L.length()-1,i));
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){
                hot=-1;bigLetter.setVisibility(GONE);invalidate();return true;}
            if(i!=hot){hot=i;Ui.tick(this);char ch=L.charAt(i);bigLetter.setVisibility(VISIBLE);bigLetter.setText(String.valueOf(ch));bigLetter.glitchNow();
                for(int k=0;k<shown.size();k++){String lab=norm(shown.get(k).label);char f=lab.isEmpty()?'#':Character.toUpperCase(lab.charAt(0));
                    if(ch=='#'?!Character.isLetter(f):f>=ch){grid.setSelection(k);break;}}
                invalidate();}
            getParent().requestDisallowInterceptTouchEvent(true);return true;
        }
    }
}
