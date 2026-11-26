package org.clashledger.app;

import java.util.Iterator;
import java.util.Locale;
import org.json.JSONObject;

/** Works out how many ranked attacks a league gives and whether they reset daily or weekly. Pure Java. */
public final class RankedLeague {
    public static final class Rule {
        public final int attacks;
        public final boolean weekly;
        public final String label;
        Rule(int attacks,boolean weekly,String label) { this.attacks=attacks;this.weekly=weekly;this.label=label; }
    }
    private static final String[][] WEEKLY={{"skeleton","6"},{"barbarian","6"},{"archer","8"},{"wizard","8"},{"valkyrie","10"},{"witch","10"},
        {"golem","12"},{"pekka","12"},{"titan","12"},{"dragon","14"},{"electro","18"}};
    public static Rule fromPlayer(JSONObject player) {
        JSONObject tier=player==null?null:player.optJSONObject("leagueTier");
        return tier==null?null:fromName(nameOf(tier));
    }
    /** The league tier name can be a plain string or an object keyed by language. */
    static String nameOf(JSONObject tier) {
        Object n=tier.opt("name");
        if(n instanceof JSONObject) {
            JSONObject o=(JSONObject)n;
            String en=o.optString("en","");
            if(!en.isEmpty())return en;
            Iterator<String> keys=o.keys();
            return keys.hasNext()?o.optString(keys.next(),""):"";
        }
        return n==null?"":String.valueOf(n);
    }
    public static Rule fromName(String name) {
        if(name==null)return null;
        String lower=name.toLowerCase(Locale.ROOT);
        String letters=lower.replaceAll("[^a-z]","");
        String[] tokens=lower.split("[^a-z0-9]+");
        if(letters.contains("legend")) {
            for(String t:tokens)if(t.equals("iii")||t.equals("3"))return new Rule(24,true,"Legend III");
            for(String t:tokens)if(t.equals("ii")||t.equals("2"))return new Rule(30,true,"Legend II");
            return new Rule(8,false,"Legend I");
        }
        for(String[] league:WEEKLY)if(letters.contains(league[0]))return new Rule(Integer.parseInt(league[1]),true,name.trim());
        return null;
    }
    private RankedLeague() { }
}
