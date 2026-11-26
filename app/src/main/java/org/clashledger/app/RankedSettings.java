package org.clashledger.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Clash of Clans API token and ranked-attack preferences. Stored only on this device. */
public final class RankedSettings {
    public static final String DEFAULT_BASE="https://api.clashofclans.com/v1";
    private final SharedPreferences prefs;
    public RankedSettings(Context c) { prefs=c.getApplicationContext().getSharedPreferences("ranked_api",Context.MODE_PRIVATE); }
    public String token() { return prefs.getString("token","").trim(); }
    public String baseUrl() {
        String s=prefs.getString("baseUrl",DEFAULT_BASE).trim();
        while(s.endsWith("/"))s=s.substring(0,s.length()-1);
        return s.startsWith("https://")?s:DEFAULT_BASE;
    }
    public int target() { return Math.max(1,prefs.getInt("target",8)); }
    public boolean weekly() { return prefs.getBoolean("weekly",false); }
    /** When on, each village's league sets its attack count and daily or weekly window. */
    public boolean autoLeague() { return prefs.getBoolean("auto",true); }
    public boolean configured() { return !token().isEmpty(); }
    public void save(String token,String baseUrl,int target,boolean weekly,boolean auto) {
        prefs.edit().putString("token",token).putString("baseUrl",baseUrl).putInt("target",target).putBoolean("weekly",weekly).putBoolean("auto",auto).apply();
    }
}
