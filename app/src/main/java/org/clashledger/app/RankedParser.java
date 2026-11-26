package org.clashledger.app;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Counts ranked attacks in a /players/{tag}/battlelog response. Pure Java so it is unit-testable. */
public final class RankedParser {
    public static final class Summary {
        public final int rankedAttacks, inWindow;
        public final boolean hasTimes;
        Summary(int rankedAttacks,int inWindow,boolean hasTimes) { this.rankedAttacks=rankedAttacks;this.inWindow=inWindow;this.hasTimes=hasTimes; }
        /** Attacks in the current window when the log is dated, otherwise every ranked attack in the log. */
        public int done() { return hasTimes?inWindow:rankedAttacks; }
    }
    public static Summary parse(String body,long now,boolean weekly) throws JSONException {
        String text=body==null?"":body.trim();
        JSONArray items=text.startsWith("[")?new JSONArray(text):new JSONObject(text).optJSONArray("items");
        if(items==null)items=new JSONArray();
        long start=windowStart(now,weekly);
        int ranked=0,inWindow=0;boolean times=false;
        for(int i=0;i<items.length();i++) {
            JSONObject o=items.optJSONObject(i);
            if(o==null||!o.optBoolean("attack",false)||!isRanked(o.optString("battleType","")))continue;
            ranked++;
            long at=parseTime(stamp(o));
            if(at>=0) { times=true;if(at>=start&&at<=now+60000)inWindow++; }
        }
        return new Summary(ranked,inWindow,times);
    }
    /** The API dates battles with battleTimestamp. battleTime is a number and is only a fallback. */
    static String stamp(JSONObject o) {
        String s=o.optString("battleTimestamp","");
        return s.isEmpty()?o.optString("battleTime",""):s;
    }
    static boolean isRanked(String type) {
        String t=type.toLowerCase(Locale.ROOT);
        return t.contains("ranked")||t.contains("legend");
    }
    /** Latest 05:00 UTC reset, or the Monday 05:00 UTC reset when weekly. */
    public static long windowStart(long now,boolean weekly) {
        Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY,5);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        if(c.getTimeInMillis()>now)c.add(Calendar.DAY_OF_MONTH,-1);
        if(weekly)c.add(Calendar.DAY_OF_MONTH,-((c.get(Calendar.DAY_OF_WEEK)-Calendar.MONDAY+7)%7));
        return c.getTimeInMillis();
    }
    static long parseTime(String s) {
        if(s==null||s.isEmpty())return -1;
        for(String pattern:new String[]{"yyyyMMdd'T'HHmmss.SSS'Z'","yyyyMMdd'T'HHmmss'Z'"}) {
            SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.ROOT);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));f.setLenient(false);
            try { return f.parse(s).getTime(); } catch(ParseException ignored) { }
        }
        return -1;
    }
    private RankedParser() { }
}
