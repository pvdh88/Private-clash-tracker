package org.clashledger.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/** Lists every saved village with its ranked attack status, outstanding villages first. */
public final class RankedWidget extends AppWidgetProvider {
    static final String FETCH="org.clashledger.FETCH_RANKED";
    public static void refresh(Context c) {
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        for(int id:m.getAppWidgetIds(new ComponentName(c,RankedWidget.class)))render(c,m,id);
    }
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids) {
        for(int id:ids)render(c,m,id);
        if(ids.length>0&&new RankedSettings(c).configured())RankedTracker.refreshInBackground(c,false,goAsync());
    }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle b) { render(c,m,id); }
    @Override public void onReceive(Context c,Intent i) {
        super.onReceive(c,i);
        String a=i.getAction();
        if(FETCH.equals(a)) {
            if(new RankedSettings(c).configured())RankedTracker.refreshInBackground(c,true,goAsync());
            else refresh(c);
        } else if(Intent.ACTION_TIME_CHANGED.equals(a)||Intent.ACTION_TIMEZONE_CHANGED.equals(a))refresh(c);
    }
    static void render(Context c,AppWidgetManager manager,int id) {
        RankedSettings settings=new RankedSettings(c);
        boolean oled=DisplaySettings.oled(c);
        List<RankedTracker.Line> lines=new ArrayList<>(RankedTracker.lines(c));
        lines.sort(Comparator.comparingInt(l->l.state));
        RemoteViews root=new RemoteViews(c.getPackageName(),R.layout.widget_ranked);
        if(oled) {
            root.setInt(R.id.widget_root,"setBackgroundResource",R.drawable.oled_bg);
            root.setInt(R.id.ranked_refresh,"setBackgroundResource",R.drawable.oled_tile);
            root.setInt(R.id.ranked_settings,"setBackgroundResource",R.drawable.oled_tile);
        }
        PendingIntent settingsIntent=PendingIntent.getActivity(c,15,new Intent(c,ApiSettingsActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open=settings.configured()?PendingIntent.getActivity(c,16,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE):settingsIntent;
        PendingIntent fetch=PendingIntent.getBroadcast(c,17,new Intent(c,RankedWidget.class).setAction(FETCH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        root.setOnClickPendingIntent(R.id.widget_root,open);
        root.setOnClickPendingIntent(R.id.ranked_settings,settingsIntent);
        root.setOnClickPendingIntent(R.id.ranked_refresh,fetch);
        int pending=0,unknown=0;
        root.removeAllViews(R.id.ranked_rows);
        for(RankedTracker.Line line:lines) {
            if(line.state==RankedTracker.PENDING)pending++;
            if(line.state==RankedTracker.UNKNOWN)unknown++;
            RemoteViews row=new RemoteViews(c.getPackageName(),R.layout.widget_ranked_row);
            if(oled)row.setInt(R.id.rk_row,"setBackgroundResource",R.drawable.oled_tile);
            row.setTextViewText(R.id.rk_name,line.village);
            row.setTextViewText(R.id.rk_status,line.status);
            row.setTextColor(R.id.rk_status,line.state==RankedTracker.DONE?0xFFA6DD98:line.state==RankedTracker.PENDING?0xFFF2C46C:0xFFA4B0C1);
            row.setTextViewText(R.id.rk_detail,line.detail);
            root.addView(R.id.ranked_rows,row);
        }
        String meta=!settings.configured()?"TAP ⚙ TO LINK YOUR CLASH API":lines.isEmpty()?"NO VILLAGES SAVED":pending>0?pending+" TO DO":unknown>0?"CHECK STATUS":"ALL DONE ✓";
        root.setTextViewText(R.id.ranked_meta,meta+" · "+DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
        root.setViewVisibility(R.id.ranked_empty,lines.isEmpty()?View.VISIBLE:View.GONE);
        manager.updateAppWidget(id,root);
    }
}
