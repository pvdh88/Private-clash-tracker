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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Fetches each saved village's battle log, league and clan war status from the Clash of Clans API. */
public final class RankedTracker {
    public static final int PENDING=0, UNKNOWN=1, DONE=2;
    private static final long MIN_INTERVAL=10*60*1000L;
    public static final class Line {
        public final String village,status,detail;
        public final int state;
        Line(String village,String status,String detail,int state) { this.village=village;this.status=status;this.detail=detail;this.state=state; }
    }
    /** A village that still has clan war attacks to make. */
    public static final class War {
        public final String village;
        public final int left,per;
        public final long end;
        War(String village,int left,int per,long end) { this.village=village;this.left=left;this.per=per;this.end=end; }
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
    private static void saveError(Context c,String tag,String error) {
        JSONObject o=load(c,tag);
        if(o==null)o=new JSONObject();
        try { prefs(c).edit().putString(tag,o.put("error",error).put("at",System.currentTimeMillis()).toString()).apply(); } catch(JSONException ignored) { }
    }
    private static void save(Context c,String tag,int done,int ranked,boolean times,int target,boolean weekly,String league,JSONObject war) {
        try {
            prefs(c).edit().putString(tag,new JSONObject().put("done",done).put("ranked",ranked).put("times",times).put("at",System.currentTimeMillis())
                .put("error","").put("target",target).put("weekly",weekly).put("league",league).put("war",war).toString()).apply();
        } catch(JSONException ignored) { }
    }
    /** Replaces a placeholder village name (blank, "My village" or the tag itself) with the in-game name. Custom names are left alone. */
    private static void applyName(Context c,String tag,String name) {
        if(name==null||name.trim().isEmpty())return;
        try {
            for(Ledger l:Ledger.all(c)) {
                if(!normalizeTag(l.playerTag).equals(tag))continue;
                String current=l.village==null?"":l.village.trim();
                if(current.isEmpty()||current.equals("My village")||normalizeTag(current).equals(tag)) { l.village=name.trim();l.save(); }
            }
        } catch(RuntimeException ignored) { }
    }
    private static String warText(JSONObject o,long now) {
        JSONObject war=o.optJSONObject("war");
        if(war==null)return "";
        String state=war.optString("state");
        if("error".equals(state))return " \u00b7 War: "+war.optString("msg");
        long end=war.optLong("end");
        if("inWar".equals(state)&&(end==0||end>now))return " \u00b7 War: "+war.optInt("left")+" left";
        return "";
    }
    /** Villages with clan war attacks still to make, soonest war end first. */
    public static List<War> warLines(Context c) {
        List<War> out=new ArrayList<>();
        long now=System.currentTimeMillis();
        for(Ledger l:Ledger.all(c)) {
            String tag=normalizeTag(l.playerTag);
            if(tag.isEmpty())continue;
            JSONObject o=load(c,tag);
            JSONObject war=o==null?null:o.optJSONObject("war");
            if(war==null||!"inWar".equals(war.optString("state")))continue;
            long end=war.optLong("end");
            if(end!=0&&end<=now)continue;
            int left=war.optInt("left");
            if(left>0)out.add(new War(l.village,left,war.optInt("per",2),end));
        }
        out.sort((a,b)->Long.compare(a.end==0?Long.MAX_VALUE:a.end,b.end==0?Long.MAX_VALUE:b.end));
        return out;
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
            if(!error.isEmpty()) { out.add(new Line(l.village,"Error",error+" \u00b7 "+when,UNKNOWN));continue; }
            boolean times=o.optBoolean("times");
            boolean weekly=o.has("weekly")?o.optBoolean("weekly"):s.weekly();
            int target=o.has("target")?o.optInt("target"):s.target();
            if(!times) { out.add(new Line(l.village,o.optInt("ranked")+" in log","Battle log has no dates, so today's count is unknown \u00b7 "+when,UNKNOWN));continue; }
            if(o.optLong("at")<RankedParser.windowStart(now,weekly)) { out.add(new Line(l.village,"Refresh","Last fetched before the latest reset \u00b7 "+when,UNKNOWN));continue; }
            int done=o.optInt("done");
            boolean complete=done>=target;
            String league=o.optString("league","");
            out.add(new Line(l.village,done+"/"+target+(complete?" \u2713":""),(league.isEmpty()?"":league+" \u00b7 ")+(weekly?"This week":"Today")+" \u00b7 "+when+warText(o,now),complete?DONE:PENDING));
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
        String error;
        try {
            String encoded=URLEncoder.encode(tag,"UTF-8");
            String body=get(s,"/players/"+encoded+"/battlelog");
            prefs(c).edit().putString("lastRaw",body.length()>60000?body.substring(0,60000):body).apply();
            RankedLeague.Rule rule=null;
            JSONObject war=null;
            try {
                JSONObject profile=new JSONObject(get(s,"/players/"+encoded));
                JSONObject tier=profile.optJSONObject("leagueTier");
                prefs(c).edit().putString("lastLeague",tier==null?"no leagueTier field":tier.toString()).apply();
                applyName(c,tag,profile.optString("name",""));
                if(s.autoLeague())rule=RankedLeague.fromPlayer(profile);
                JSONObject clan=profile.optJSONObject("clan");
                war=fetchWar(s,tag,clan==null?"":clan.optString("tag",""));
            } catch(Exception ignored) { }
            boolean weekly=rule!=null?rule.weekly:s.weekly();
            int target=rule!=null?rule.attacks:s.target();
            RankedParser.Summary sum=RankedParser.parse(body,System.currentTimeMillis(),weekly);
            save(c,tag,sum.done(),sum.rankedAttacks,sum.hasTimes,target,weekly,rule!=null?rule.label:"",war);
            return "";
        } catch(JSONException e) { error="Unexpected response from the API";
        } catch(Exception e) { error=e.getMessage()==null?"Request failed":e.getMessage(); }
        saveError(c,tag,error);
        return error;
    }
    private static JSONObject member(JSONObject side,String tag) {
        JSONArray members=side==null?null:side.optJSONArray("members");
        if(members==null)return null;
        for(int i=0;i<members.length();i++) {
            JSONObject m=members.optJSONObject(i);
            if(m!=null&&normalizeTag(m.optString("tag")).equals(tag))return m;
        }
        return null;
    }
    /** Looks up the player's attacks in the clan's current regular war. Never returns null. */
    private static JSONObject fetchWar(RankedSettings s,String tag,String clanTag) {
        try {
            JSONObject out=new JSONObject();
            if(clanTag.isEmpty())return out.put("state","noclan");
            JSONObject war=new JSONObject(get(s,"/clans/"+URLEncoder.encode(clanTag,"UTF-8")+"/currentwar"));
            String state=war.optString("state","notInWar");
            out.put("state",state);
            if(!"inWar".equals(state))return out;
            JSONObject me=member(war.optJSONObject("clan"),tag);
            if(me==null)me=member(war.optJSONObject("opponent"),tag);
            if(me==null)return out.put("state","notInRoster");
            int per=war.optInt("attacksPerMember",2);
            JSONArray used=me.optJSONArray("attacks");
            out.put("per",per).put("left",Math.max(0,per-(used==null?0:used.length()))).put("end",parseWarTime(war.optString("endTime","")));
            return out;
        } catch(Exception e) {
            String message=e.getMessage()==null?"failed":e.getMessage();
            if(message.startsWith("API key rejected"))message="war log is private";
            try { return new JSONObject().put("state","error").put("msg",message); } catch(JSONException x) { return new JSONObject(); }
        }
    }
    private static long parseWarTime(String value) {
        try {
            java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss.SSS'Z'",Locale.US);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return format.parse(value).getTime();
        } catch(Exception e) { return 0; }
    }
    private static String get(RankedSettings s,String path) throws java.io.IOException {
        HttpURLConnection conn=null;
        try {
            conn=(HttpURLConnection)new URL(s.baseUrl()+path).openConnection();
            conn.setConnectTimeout(10000);conn.setReadTimeout(15000);conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Authorization","Bearer "+s.token());
            conn.setRequestProperty("Accept","application/json");conn.setRequestProperty("User-Agent","Clash-Ledger");
            int status=conn.getResponseCode();
            if(status!=200)throw new java.io.IOException(describe(status));
            return read(conn.getInputStream());
        } finally { if(conn!=null)conn.disconnect(); }
    }
    public static String lastRaw(Context c) { return prefs(c).getString("lastRaw",""); }
    public static String lastLeague(Context c) { return prefs(c).getString("lastLeague",""); }
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
