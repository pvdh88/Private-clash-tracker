package org.clashledger.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Links the Clash of Clans API for ranked-attack tracking. Opened from the ranked widget's gear button. */
public final class ApiSettingsActivity extends Activity {
    private EditText token,base,target;
    private CheckBox weekly;
    private TextView status;
    private float dp;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp=getResources().getDisplayMetrics().density;
        RankedSettings s=new RankedSettings(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad(16),pad(16),pad(16),pad(16));
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B1220);scroll.setFitsSystemWindows(true);scroll.addView(root);
        add(root,label("Ranked attacks · Clash API",20,0xFFF2F5F7,true),0);
        add(root,label("1. Create a key at developer.clashofclans.com and paste the token below.",13,0xFFA4B0C1,false),8);
        add(root,label("2. Official keys only work from the IP addresses listed on the key. Phone addresses change, so you may see an 'IP address' error. If so, use a proxy: allow the proxy's IP on your key and enter its address as the base URL (RoyaleAPI's proxy is documented at docs.royaleapi.com/proxy.html). A proxy can see your token and player tags.",13,0xFFA4B0C1,false),6);
        add(root,label("3. Each village needs its player tag, which is saved when you import it. Only the player tag is sent. The token stays on this phone.",13,0xFFA4B0C1,false),6);
        token=field("API token",s.token());
        token.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        add(root,token,12);
        base=field("Base URL (blank = official server)",s.baseUrl().equals(RankedSettings.DEFAULT_BASE)?"":s.baseUrl());
        base.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        add(root,base,8);
        target=field("Ranked attacks per window (8 for daily Legend)",String.valueOf(s.target()));
        target.setInputType(InputType.TYPE_CLASS_NUMBER);
        add(root,target,8);
        weekly=new CheckBox(this);
        weekly.setText("Count per week (Monday 05:00 UTC) instead of per day (05:00 UTC)");
        weekly.setTextColor(0xFFF2F5F7);weekly.setChecked(s.weekly());
        add(root,weekly,8);
        add(root,button("Save and fetch now",()->{ if(save())fetchNow(); }),12);
        add(root,button("Save only",()->{ if(save()) { Toast.makeText(this,"Saved",Toast.LENGTH_SHORT).show();RankedWidget.refresh(this);showStatus(); } }),6);
        add(root,button("Copy last battle log response",this::copyRaw),6);
        status=label("",13,0xFFF2F5F7,false);
        add(root,status,16);
        setContentView(scroll);
        showStatus();
    }
    private int pad(int v) { return Math.round(v*dp); }
    private void add(LinearLayout root,View v,int topDp) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin=pad(topDp);root.addView(v,lp);
    }
    private TextView label(String text,int sp,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(text);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(t.getTypeface(),android.graphics.Typeface.BOLD);
        return t;
    }
    private EditText field(String hint,String value) {
        EditText e=new EditText(this);
        e.setHint(hint);e.setText(value);e.setSingleLine(true);
        e.setTextColor(0xFFF2F5F7);e.setHintTextColor(0xFF7F8CA0);e.setBackgroundColor(0xFF152033);e.setPadding(pad(12),pad(12),pad(12),pad(12));
        return e;
    }
    private Button button(String text,Runnable action) {
        Button button=new Button(this);
        button.setText(text);button.setOnClickListener(v->action.run());
        return button;
    }
    private boolean save() {
        int t;
        try { t=Integer.parseInt(target.getText().toString().trim()); } catch(NumberFormatException e) { t=8; }
        t=Math.max(1,Math.min(99,t));
        String url=base.getText().toString().trim();
        if(!url.isEmpty()&&!url.startsWith("https://")) { Toast.makeText(this,"Base URL must start with https://",Toast.LENGTH_LONG).show();return false; }
        new RankedSettings(this).save(token.getText().toString().trim(),url.isEmpty()?RankedSettings.DEFAULT_BASE:url,t,weekly.isChecked());
        return true;
    }
    private void fetchNow() {
        status.setText("Fetching ranked attacks…");
        Context app=getApplicationContext();
        new Thread(()->{
            try { RankedTracker.refreshAll(app,true); } catch(Exception ignored) { }
            RankedWidget.refresh(app);AllVillagesWidget.refresh(app);
            runOnUiThread(()->{ if(!isFinishing()&&!isDestroyed())showStatus(); });
        },"ranked-fetch").start();
    }
    private void showStatus() {
        StringBuilder out=new StringBuilder();
        for(RankedTracker.Line line:RankedTracker.lines(this))out.append(line.village).append(": ").append(line.status).append(" (").append(line.detail).append(')').append('\n');
        status.setText(out.length()==0?"No villages saved yet.":out.toString().trim());
    }
    private void copyRaw() {
        String raw=RankedTracker.lastRaw(this);
        if(raw.isEmpty()) { Toast.makeText(this,"Nothing fetched yet",Toast.LENGTH_SHORT).show();return; }
        ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Clash battle log",raw));
        Toast.makeText(this,"Copied. It includes opponent player tags.",Toast.LENGTH_LONG).show();
    }
}
