package org.clashledger.wear;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Shows the next five upgrades and the villages with ranked attacks left, synced from the phone app. */
public final class MainActivity extends Activity implements DataClient.OnDataChangedListener {
    private static final String PATH="/clash-ledger/summary";
    private static final int GOLD=0xFFF2C46C, INK=0xFFF2F5F7, MUTED=0xFFA4B0C1, GREEN=0xFFA6DD98;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<TextView> timers=new ArrayList<>();
    private final List<Long> ends=new ArrayList<>();
    private LinearLayout list;
    private JSONObject summary;
    private long receivedAt;
    private final Runnable tick=new Runnable() { @Override public void run() { updateTimers();handler.postDelayed(this,1000); } };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(26),dp(34),dp(26),dp(40));
        scroll.addView(list);
        setContentView(scroll);
        render();
    }
    @Override protected void onResume() {
        super.onResume();
        Wearable.getDataClient(this).addListener(this);
        load();
        handler.post(tick);
    }
    @Override protected void onPause() {
        Wearable.getDataClient(this).removeListener(this);
        handler.removeCallbacks(tick);
        super.onPause();
    }
    private void load() {
        Wearable.getDataClient(this).getDataItems().addOnSuccessListener(items -> {
            try {
                for(DataItem item:items) {
                    if(PATH.equals(item.getUri().getPath()))apply(DataMapItem.fromDataItem(item).getDataMap().getString("json"));
                }
            } finally { items.release(); }
        });
    }
    @Override public void onDataChanged(DataEventBuffer events) {
        String json=null;
        for(DataEvent event:events) {
            if(event.getType()==DataEvent.TYPE_CHANGED&&PATH.equals(event.getDataItem().getUri().getPath()))
                json=DataMapItem.fromDataItem(event.getDataItem()).getDataMap().getString("json");
        }
        final String value=json;
        if(value!=null)runOnUiThread(()->apply(value));
    }
    private void apply(String json) {
        if(json==null)return;
        try { summary=new JSONObject(json);receivedAt=System.currentTimeMillis();render(); } catch(Exception ignored) { }
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }
    private void add(TextView t,int topDp) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(topDp);
        list.addView(t,p);
    }
    private void render() {
        list.removeAllViews();timers.clear();ends.clear();
        if(summary==null) {
            add(text("CLASH LEDGER",12,GOLD,true),0);
            add(text("Open Clash Ledger on your phone to sync.",13,MUTED,false),8);
            return;
        }
        add(text("NEXT UPGRADES",12,GOLD,true),0);
        JSONArray up=summary.optJSONArray("up");
        if(up==null||up.length()==0)add(text("No running upgrades",13,MUTED,false),8);
        else for(int i=0;i<up.length();i++) {
            JSONObject o=up.optJSONObject(i);
            if(o==null)continue;
            int level=o.optInt("l");
            TextView name=text(o.optString("v")+" \u00b7 "+o.optString("n")+(level>0?" \u2192 "+level:""),12,INK,false);
            name.setMaxLines(2);
            add(name,10);
            TextView timer=text("",16,GREEN,true);
            add(timer,1);
            timers.add(timer);ends.add(o.optLong("end"));
        }
        boolean configured=summary.optBoolean("cfg");
        int pending=summary.optInt("pend"),unknown=summary.optInt("unk");
        add(text("ATTACKS LEFT"+(configured&&pending>0?" \u00b7 "+pending:""),12,GOLD,true),18);
        JSONArray atk=summary.optJSONArray("atk");
        if(!configured)add(text("Link the Clash API on your phone",12,MUTED,false),6);
        else if(pending==0)add(text(unknown>0?unknown+" need a refresh":"All ranked attacks done \u2713",13,unknown>0?MUTED:GREEN,false),6);
        else if(atk!=null)for(int i=0;i<atk.length();i++) {
            JSONObject o=atk.optJSONObject(i);
            if(o==null)continue;
            add(text(o.optString("v")+"  "+o.optString("s"),13,INK,false),6);
        }
        add(text("Synced "+DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(receivedAt)),10,MUTED,false),18);
        updateTimers();
    }
    private void updateTimers() {
        long now=System.currentTimeMillis();
        for(int i=0;i<timers.size();i++) {
            long left=ends.get(i)-now;
            timers.get(i).setText(left>0?format(left):"Ready");
            timers.get(i).setTextColor(left>0?GREEN:GOLD);
        }
    }
    private static String format(long ms) {
        long s=Math.max(0,ms/1000),d=s/86400,h=(s%86400)/3600,m=(s%3600)/60;
        if(d>0)return d+"d "+h+"h";
        if(h>0)return h+"h "+m+"m";
        return m+"m "+(s%60)+"s";
    }
}
