package org.clashledger.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class RankedLeagueTest {
    @Test public void legendITiersUseDailyAndWeeklyCounts() {
        RankedLeague.Rule one=RankedLeague.fromName("Legend League I");
        assertEquals(8,one.attacks);
        assertFalse(one.weekly);
        RankedLeague.Rule two=RankedLeague.fromName("Legend League II");
        assertEquals(30,two.attacks);
        assertTrue(two.weekly);
        RankedLeague.Rule three=RankedLeague.fromName("Legend League III");
        assertEquals(24,three.attacks);
        assertTrue(three.weekly);
    }
    @Test public void bareLegendLeagueIsTreatedAsDaily() {
        RankedLeague.Rule rule=RankedLeague.fromName("Legend League");
        assertEquals(8,rule.attacks);
        assertFalse(rule.weekly);
    }
    @Test public void numberedLeaguesAreWeekly() {
        assertEquals(6,RankedLeague.fromName("Skeleton League 2").attacks);
        assertEquals(12,RankedLeague.fromName("P.E.K.K.A. League 22").attacks);
        assertEquals(18,RankedLeague.fromName("Electro League 31").attacks);
        assertTrue(RankedLeague.fromName("Wizard League 10").weekly);
    }
    @Test public void unknownNamesGiveNoRule() {
        assertNull(RankedLeague.fromName("Unranked"));
        assertNull(RankedLeague.fromName(null));
    }
    @Test public void readsTheNameFromAPlayerProfile() throws Exception {
        RankedLeague.Rule plain=RankedLeague.fromPlayer(new JSONObject("{\"leagueTier\":{\"id\":1,\"name\":\"Legend League III\"}}"));
        assertEquals(24,plain.attacks);
        RankedLeague.Rule localized=RankedLeague.fromPlayer(new JSONObject("{\"leagueTier\":{\"id\":1,\"name\":{\"en\":\"Legend League II\"}}}"));
        assertEquals(30,localized.attacks);
        assertNotNull(localized);
        assertNull(RankedLeague.fromPlayer(new JSONObject("{}")));
    }
}
