package org.clashledger.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Exports and restores every village, setting and the API key as one JSON file. */
public final class BackupActivity extends Activity {
    private static final int EXPORT=801, IMPORT=802;
    private static final String FORMAT="clash-ledger-backup";
    private float dp;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp=getResources().getDisplayMetrics().density;
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setPadding(pad(16),pad(16),pad(16),pad(16));
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(0xFF0B1220);scroll.setFitsSystemWindows(true);scroll.addView(root);
        add(root,label("Backup & restore",20,0xFFF2F5F7,true),0);
        add(root,label("Export saves all your villages, upgrade timers, settings and your Clash API key into one file. Import that file into any new build to restore everything.",13,0xFFA4B0C1,false),8);
        add(root,label("The file contains your API token. Keep it private and don't share it.",13,0xFFF2C46C,false),8);
        add(root,button("Export backup",this::exportBackup),16);
        add(root,button("Import backup",this::importBackup),8);
        setContentView(scroll);
    }
    private int pad(int v) { return Math.round(v*dp); }
    private void add(LinearLayout root,View v,int topDp) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin=pad(topDp);root.addView(v,lp);
    }
    private TextView label(String text,int sp,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(text);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(t.getTypeface(),android.graphics.Typeface.BOLD);
        return t;
    }
    private Button button(String text,Runnable action) {
        Button button=new Button(this);
        button.setText(text);button.setOnClickListener(v->action.run());
        return button;
    }
    private static boolean skip(String name) {
        return name.equals("ranked_state")||name.equals("watch_sync")||name.startsWith("com.google")||name.startsWith("androidx")||name.startsWith("WebView");
    }
    private void exportBackup() {
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE,"clash-ledger-backup-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date())+".json");
        startActivityForResult(intent,EXPORT);
    }
    private void importBackup() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent,IMPORT);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==EXPORT)writeBackup(uri);
        if(request==IMPORT) {
            new AlertDialog.Builder(this).setTitle("Replace everything?")
                .setMessage("This replaces your villages, settings and API key with the contents of the backup.")
                .setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->restoreBackup(uri)).show();
        }
    }
    private static JSONObject encode(Object value) throws Exception {
        JSONObject o=new JSONObject();
        if(value instanceof Boolean)return o.put("t","b").put("v",((Boolean)value).booleanValue());
        if(value instanceof Integer)return o.put("t","i").put("v",((Integer)value).intValue());
        if(value instanceof Long)return o.put("t","l").put("v",((Long)value).longValue());
        if(value instanceof Float)return o.put("t","f").put("v",((Float)value).doubleValue());
        if(value instanceof Set) {
            JSONArray items=new JSONArray();
            for(Object item:(Set<?>)value)items.put(String.valueOf(item));
            return o.put("t","ss").put("v",items);
        }
        return o.put("t","s").put("v",String.valueOf(value));
    }
    private JSONObject build() throws Exception {
        JSONObject all=new JSONObject();
        File[] files=new File(getApplicationInfo().dataDir,"shared_prefs").listFiles();
        if(files!=null)for(File file:files) {
            String name=file.getName();
            if(!name.endsWith(".xml"))continue;
            name=name.substring(0,name.length()-4);
            if(skip(name))continue;
            JSONObject group=new JSONObject();
            for(Map.Entry<String,?> entry:getSharedPreferences(name,MODE_PRIVATE).getAll().entrySet()) {
                if(entry.getValue()!=null)group.put(entry.getKey(),encode(entry.getValue()));
            }
            all.put(name,group);
        }
        return new JSONObject().put("format",FORMAT).put("version",1).put("exportedAt",System.currentTimeMillis()).put("prefs",all);
    }
    private void writeBackup(Uri uri) {
        try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
            if(out==null)throw new java.io.IOException("Cannot open the file");
            out.write(build().toString(2).getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this,"Backup saved. It contains your API token, so keep it private.",Toast.LENGTH_LONG).show();
        } catch(Exception e) {
            Toast.makeText(this,"Could not save the backup: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
    private void restoreBackup(Uri uri) {
        try(InputStream in=getContentResolver().openInputStream(uri)) {
            if(in==null)throw new java.io.IOException("Cannot open the file");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1) {
                if(bytes.size()+n>5_000_000)throw new java.io.IOException("File is too large");
                bytes.write(buffer,0,n);
            }
            JSONObject root=new JSONObject(bytes.toString("UTF-8"));
            if(!FORMAT.equals(root.optString("format")))throw new java.io.IOException("This is not a Clash Ledger backup");
            JSONObject all=root.getJSONObject("prefs");
            int restored=0;
            Iterator<String> names=all.keys();
            while(names.hasNext()) {
                String name=names.next();
                if(skip(name))continue;
                JSONObject group=all.getJSONObject(name);
                SharedPreferences.Editor editor=getSharedPreferences(name,MODE_PRIVATE).edit().clear();
                Iterator<String> keys=group.keys();
                while(keys.hasNext()) {
                    String key=keys.next();
                    JSONObject item=group.getJSONObject(key);
                    switch(item.optString("t")) {
                        case "b": editor.putBoolean(key,item.optBoolean("v"));break;
                        case "i": editor.putInt(key,item.optInt("v"));break;
                        case "l": editor.putLong(key,item.optLong("v"));break;
                        case "f": editor.putFloat(key,(float)item.optDouble("v"));break;
                        case "ss": {
                            Set<String> set=new HashSet<>();
                            JSONArray items=item.optJSONArray("v");
                            if(items!=null)for(int i=0;i<items.length();i++)set.add(items.optString(i));
                            editor.putStringSet(key,set);
                            break;
                        }
                        default: editor.putString(key,item.optString("v"));
                    }
                }
                editor.commit();
                restored++;
            }
            AllVillagesWidget.refresh(this);
            RankedWidget.refresh(this);
            Toast.makeText(this,"Restored "+restored+" groups of settings and villages",Toast.LENGTH_LONG).show();
            finish();
        } catch(Exception e) {
            Toast.makeText(this,"Could not restore: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
}
