package org.clashledger.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Sends a short summary to a paired Wear OS watch running the Clash Ledger watch app. */
public final class WatchSync {
    static final String PATH="/clash-ledger/summary";
    private static final long RESEND=6*60*60*1000L;
    private static final class Item { String village,name;int level;boolean forge;long left; }
    public static void push(Context c) {
        try {
            Context app=c.getApplicationContext();
            String json=build(app);
            SharedPreferences prefs=app.getSharedPreferences("watch_sync",Context.MODE_PRIVATE);
            long now=System.currentTimeMillis();
            if(json.equals(prefs.getString("last",""))&&now-prefs.getLong("at",0)<RESEND)return;
            prefs.edit().putString("last",json).putLong("at",now).apply();
            PutDataMapRequest request=PutDataMapRequest.create(PATH);
            request.getDataMap().putString("json",json);
            request.getDataMap().putLong("sent",now);
            PutDataRequest put=request.asPutDataRequest();
            put.setUrgent();
            Wearable.getDataClient(app).putDataItem(put);
        } catch(Exception ignored) { }
    }
    static String build(Context c) throws Exception {
        long now=System.currentTimeMillis();
        List<Item> items=new ArrayList<>();
        for(Ledger l:Ledger.all(c))for(Upgrade u:l.upgrades) {
            long left=u.wallRemaining(now,l.boostUntil);
            if(left<=0)continue;
            Item i=new Item();
            i.village=l.village;i.name=u.name+(u.goblin?" (G)":"");i.level=u.level;i.forge=u.isForge();i.left=left;
            items.add(i);
        }
        items.sort((a,b)->Long.compare(a.left,b.left));
        JSONArray up=new JSONArray();
        for(int n=0;n<Math.min(5,items.size());n++) {
            Item i=items.get(n);
            up.put(new JSONObject().put("v",i.village).put("n",i.name).put("l",i.forge?0:i.level).put("end",Math.round((now+i.left)/10000.0)*10000L));
        }
        RankedSettings settings=new RankedSettings(c);
        JSONArray atk=new JSONArray();
        int pending=0,unknown=0;
        if(settings.configured())for(RankedTracker.Line line:RankedTracker.lines(c)) {
            if(line.state==RankedTracker.UNKNOWN)unknown++;
            if(line.state!=RankedTracker.PENDING)continue;
            pending++;
            if(atk.length()<8)atk.put(new JSONObject().put("v",line.village).put("s",line.status));
        }
        return new JSONObject().put("up",up).put("atk",atk).put("cfg",settings.configured()).put("pend",pending).put("unk",unknown).toString();
    }
    private WatchSync() { }
}
