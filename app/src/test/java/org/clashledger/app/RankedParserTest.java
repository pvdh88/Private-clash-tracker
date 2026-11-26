package org.clashledger.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;

public class RankedParserTest {
    private static long utc(int y,int month,int d,int h) {
        Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();c.set(y,month-1,d,h,0,0);
        return c.getTimeInMillis();
    }
    @Test public void countsOnlyRankedAttacksSinceDailyReset() throws Exception {
        String body="{\"items\":["
            +"{\"battleType\":\"ranked\",\"attack\":true,\"battleTime\":\"20261126T060000.000Z\"},"
            +"{\"battleType\":\"ranked\",\"attack\":true,\"battleTime\":\"20261125T200000.000Z\"},"
            +"{\"battleType\":\"ranked\",\"attack\":false,\"battleTime\":\"20261126T070000.000Z\"},"
            +"{\"battleType\":\"homeVillage\",\"attack\":true,\"battleTime\":\"20261126T080000.000Z\"}]}";
        RankedParser.Summary s=RankedParser.parse(body,utc(2026,11,26,14),false);
        assertEquals(2,s.rankedAttacks);
        assertEquals(1,s.inWindow);
        assertTrue(s.hasTimes);
        assertEquals(1,s.done());
    }
    @Test public void undatedLogCountsEveryRankedAttack() throws Exception {
        String body="{\"items\":[{\"battleType\":\"ranked\",\"attack\":true},{\"battleType\":\"legend\",\"attack\":true}]}";
        RankedParser.Summary s=RankedParser.parse(body,utc(2026,11,26,14),false);
        assertFalse(s.hasTimes);
        assertEquals(2,s.done());
    }
    @Test public void windowStartRollsBackBeforeFiveAmUtc() {
        assertEquals(utc(2026,11,25,5),RankedParser.windowStart(utc(2026,11,26,3),false));
        assertEquals(utc(2026,11,26,5),RankedParser.windowStart(utc(2026,11,26,14),false));
    }
    @Test public void weeklyWindowStartsOnMonday() {
        assertEquals(utc(2026,11,23,5),RankedParser.windowStart(utc(2026,11,26,14),true));
    }
}
