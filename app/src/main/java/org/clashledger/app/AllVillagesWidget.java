package org.clashledger.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Shows the next five upgrades to finish across every saved village. */
public final class AllVillagesWidget extends AppWidgetProvider {
    static final String REFRESH="org.clashledger.REFRESH_ALL_VILLAGES";
    private static final int ROWS=5;
    private static final class Entry {
        final Ledger ledger;final Upgrade upgrade;final long left;
        Entry(Ledger ledger,Upgrade upgrade,long left) { this.ledger=ledger;this.upgrade=upgrade;this.left=left; }
    }
    public static void refresh(Context c) {
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        int[] ids=m.getAppWidgetIds(new ComponentName(c,AllVillagesWidget.class));
        for(int id:ids)render(c,m,id);
        scheduleNext(c,ids.length>0);
    }
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids) { for(int id:ids)render(c,m,id);scheduleNext(c,ids.length>0); }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle b) { render(c,m,id); }
    @Override public void onReceive(Context c,Intent i) {
        super.onReceive(c,i);
        String a=i.getAction();
        if(REFRESH.equals(a)||Intent.ACTION_TIME_CHANGED.equals(a)||Intent.ACTION_TIMEZONE_CHANGED.equals(a))refresh(c);
    }
    @Override public void onDisabled(Context c) { scheduleNext(c,false); }
    private static List<Entry> entries(Context c,long now) {
        List<Entry> all=new ArrayList<>();
        for(Ledger l:Ledger.all(c))for(Upgrade u:l.upgrades) {
            long left=u.wallRemaining(now,l.boostUntil);
            if(left>0)all.add(new Entry(l,u,left));
        }
        all.sort((a,b)->Long.compare(a.left,b.left));
        return all;
    }
    private static void scheduleNext(Context c,boolean enabled) {
        AlarmManager alarms=c.getSystemService(AlarmManager.class);
        PendingIntent intent=PendingIntent.getBroadcast(c,12,new Intent(c,AllVillagesWidget.class).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(intent);
        if(!enabled)return;
        long now=System.currentTimeMillis(),next=Long.MAX_VALUE;
        for(Entry e:entries(c,now))next=Math.min(next,Math.min(e.left,60000));
        if(next!=Long.MAX_VALUE)alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME,SystemClock.elapsedRealtime()+Math.max(1000,next),intent);
    }
    static void render(Context c,AppWidgetManager manager,int id) {
        long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();
        String style=c.getSharedPreferences("display",0).getString("timerStyle","compact");
        boolean clock=style.equals("clock"),oled=DisplaySettings.oled(c);
        RemoteViews root=new RemoteViews(c.getPackageName(),R.layout.widget_next);
        if(oled) {
            root.setInt(R.id.widget_root,"setBackgroundResource",R.drawable.oled_bg);
            root.setInt(R.id.next_refresh,"setBackgroundResource",R.drawable.oled_tile);
        }
        PendingIntent open=PendingIntent.getActivity(c,10,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent refresh=PendingIntent.getBroadcast(c,11,new Intent(c,AllVillagesWidget.class).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        root.setOnClickPendingIntent(R.id.widget_root,open);
        root.setOnClickPendingIntent(R.id.next_refresh,refresh);
        List<Entry> all=entries(c,now);
        root.removeAllViews(R.id.next_rows);
        for(int n=0;n<Math.min(ROWS,all.size());n++) {
            Entry e=all.get(n);Upgrade u=e.upgrade;
            RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_next_row);
            if(oled)row.setInt(R.id.next_row,"setBackgroundResource",R.drawable.oled_tile);
            row.setImageViewResource(R.id.next_art,Artwork.forUpgrade(c,u));
            row.setTextViewText(R.id.next_name,e.ledger.village+" · "+u.name+(u.goblin?" · G":""));
            row.setTextViewText(R.id.next_sub,(u.isForge()?"Gold":"→ "+u.level)+" · "+u.group+" · "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(now+e.left)));
            row.setChronometerCountDown(R.id.next_clock,true);
            row.setChronometer(R.id.next_clock,elapsed+e.left,null,clock);
            if(!clock)row.setTextViewText(R.id.next_clock,TimerFormat.format(e.left,style));
            root.addView(R.id.next_rows,row);
        }
        root.setViewVisibility(R.id.next_empty,all.isEmpty()?View.VISIBLE:View.GONE);
        root.setTextViewText(R.id.next_meta,"ALL "+Ledger.all(c).size()+" VILLAGES · "+all.size()+" RUNNING · "+DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(now)));
        manager.updateAppWidget(id,root);
    }
}
