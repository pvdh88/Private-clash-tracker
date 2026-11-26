package org.clashledger.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/** Fetches each saved village's battle log from the Clash of Clans API and reports ranked attack status. */
public final class RankedTracker {
    public static final int PENDING=0, UNKNOWN=1, DONE=2;
    private static final long MIN_INTERVAL=10*60*1000L;
    public static final class Line {
        public final String village,status,detail;
        public final int state;
        Line(String village,String status,String detail,int state) { this.village=village;this.status=status;this.detail=detail;this.state=state; }
    }
    private static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences("ranked_state",Context.MODE_PRIVATE); }
    public static String normalizeTag(String raw) {
        if(raw==null)return "";
        String s=raw.trim().toUpperCase(Locale.ROOT).replace("#","").replace(" ","").replace('O','0');
        return s.isEmpty()?"":"#"+s;
    }
    private static JSONObject load(Context c,String tag) {
        String saved=prefs(c).getString(tag,null);
        if(saved==null)return null;
        try { return new JSONObject(saved); } catch(JSONException e) { return null; }
    }
    private static void save(Context c,String tag,int done,int ranked,boolean times,String error) {
        try {
            prefs(c).edit().putString(tag,new JSONObject().put("done",done).put("ranked",ranked).put("times",times)
                .put("at",System.currentTimeMillis()).put("error",error).toString()).apply();
        } catch(JSONException ignored) { }
    }
    public static List<Line> lines(Context c) {
        RankedSettings s=new RankedSettings(c);
        List<Line> out=new ArrayList<>();
        long now=System.currentTimeMillis();
        for(Ledger l:Ledger.all(c)) {
            String tag=normalizeTag(l.playerTag);
            if(!s.configured()) { out.add(new Line(l.village,"Link API","Add your API token in settings",UNKNOWN));continue; }
            if(tag.isEmpty()) { out.add(new Line(l.village,"No tag","Import this village to save its player tag",UNKNOWN));continue; }
            JSONObject o=load(c,tag);
            if(o==null) { out.add(new Line(l.village,"Not fetched","Tap refresh",UNKNOWN));continue; }
            String when=DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(o.optLong("at")));
            String error=o.optString("error","");
            if(!error.isEmpty()) { out.add(new Line(l.village,"Error",error+" · "+when,UNKNOWN));continue; }
            boolean times=o.optBoolean("times");
            if(!times) { out.add(new Line(l.village,o.optInt("ranked")+" in log","Battle log has no dates, so today's count is unknown · "+when,UNKNOWN));continue; }
            if(o.optLong("at")<RankedParser.windowStart(now,s.weekly())) { out.add(new Line(l.village,"Refresh","Last fetched before the latest reset · "+when,UNKNOWN));continue; }
            int done=o.optInt("done"),target=s.target();
            boolean complete=done>=target;
            out.add(new Line(l.village,done+"/"+target+(complete?" ✓":""),(s.weekly()?"This week":"Today")+" · "+when,complete?DONE:PENDING));
        }
        return out;
    }
    public static void refreshInBackground(Context c,boolean force,BroadcastReceiver.PendingResult pending) {
        Context app=c.getApplicationContext();
        new Thread(()->{
            try { refreshAll(app,force); } catch(Exception ignored) { }
            finally { AllVillagesWidget.refresh(app);RankedWidget.refresh(app);if(pending!=null)pending.finish(); }
        },"ranked-fetch").start();
    }
    /** Blocking. Call from a background thread. */
    public static void refreshAll(Context c,boolean force) {
        if(!new RankedSettings(c).configured())return;
        long now=System.currentTimeMillis();
        Set<String> seen=new HashSet<>();
        for(Ledger l:Ledger.all(c)) {
            String tag=normalizeTag(l.playerTag);
            if(tag.isEmpty()||!seen.add(tag))continue;
            JSONObject old=load(c,tag);
            if(!force&&old!=null&&now-old.optLong("at")<MIN_INTERVAL)continue;
            fetchOne(c,tag);
        }
    }
    /** Returns an empty string on success, otherwise a short error. */
    public static String fetchOne(Context c,String tag) {
        RankedSettings s=new RankedSettings(c);
        HttpURLConnection conn=null;
        String error;
        try {
            conn=(HttpURLConnection)new URL(s.baseUrl()+"/players/"+URLEncoder.encode(tag,"UTF-8")+"/battlelog").openConnection();
            conn.setConnectTimeout(10000);conn.setReadTimeout(15000);conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Authorization","Bearer "+s.token());
            conn.setRequestProperty("Accept","application/json");conn.setRequestProperty("User-Agent","Clash-Ledger");
            int status=conn.getResponseCode();
            if(status==200) {
                String body=read(conn.getInputStream());
                prefs(c).edit().putString("lastRaw",body.length()>60000?body.substring(0,60000):body).apply();
                RankedParser.Summary sum=RankedParser.parse(body,System.currentTimeMillis(),s.weekly());
                save(c,tag,sum.done(),sum.rankedAttacks,sum.hasTimes,"");
                return "";
            }
            error=describe(status);
        } catch(JSONException e) { error="Unexpected response from the API";
        } catch(Exception e) { error=e.getMessage()==null?"Request failed":e.getMessage();
        } finally { if(conn!=null)conn.disconnect(); }
        save(c,tag,0,0,false,error);
        return error;
    }
    public static String lastRaw(Context c) { return prefs(c).getString("lastRaw",""); }
    private static String describe(int status) {
        if(status==400)return "Bad player tag";
        if(status==403)return "API key rejected. Check the key and its allowed IP address";
        if(status==404)return "Player not found";
        if(status==429)return "Too many requests. Try later";
        if(status==503)return "Game servers are in maintenance";
        return "API returned HTTP "+status;
    }
    private static String read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream stream=in) {
            byte[] buffer=new byte[8192];int n;
            while((n=stream.read(buffer))!=-1) { if(bytes.size()+n>1_500_000)throw new java.io.IOException("Response is too large");bytes.write(buffer,0,n); }
        }
        return bytes.toString("UTF-8");
    }
    private RankedTracker() { }
}
