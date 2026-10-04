package tv.iptvibe.mobile;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Cloud-hosted updates: bounded download, pinned installed signer, Android installer. */
public final class UpdateManager {
    private static final String MANIFEST = "https://raw.githubusercontent.com/frittcat/IPTVibe-Runtime/main/updates/mobile.json";
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> active = new WeakReference<>(null);
    private static boolean checking, downloading, installing;
    private static long lastCheck, deferredUntil;
    private static Release release;
    private static AlertDialog dialog;
    private static Intent confirmation;
    private static final Runnable periodic = new Runnable() {
        public void run() { Activity a=active.get(); if(eligible(a)){check(a,false);UI.postDelayed(this,UpdatePolicy.CHECK_INTERVAL_MS);} }
    };
    private static final class Release {
        long code, minimum, size; int sdk; String version, url, sha, notes;
        Release(JSONObject j) throws Exception {
            if(j.getInt("schemaVersion") != 1 || !"tv.iptvibe.mobile".equals(j.getString("packageName"))) throw new IOException();
            code=j.getLong("versionCode"); minimum=j.getLong("minimumSupportedVersionCode"); size=j.getLong("sizeBytes");
            version=j.getString("versionName"); url=j.getString("apkUrl"); sha=j.getString("sha256");
            sdk=j.getInt("minSdk"); notes=j.optString("releaseNotes", "Melhorias e correções do IPTVibe.");
            if(!UpdatePolicy.valid(code,minimum,size,sha,url) || sdk<23 || sdk>Build.VERSION.SDK_INT || version.length()>40 || notes.length()>4000) throw new IOException();
        }
    }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("premium_updates",0); }
    private static long code(PackageInfo p) { return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode; }
    private static PackageInfo installed(Context c) throws Exception {
        return c.getPackageManager().getPackageInfo(c.getPackageName(), Build.VERSION.SDK_INT>=28?(PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_SIGNATURES):PackageManager.GET_SIGNATURES);
    }
    private static boolean eligible(Activity a) {
        return a instanceof NativeHomeActivity && !a.isFinishing() && !a.isDestroyed() && ((NativeHomeActivity)a).isUpdateScreen();
    }
    public static void resumed(Activity a) {
        if(!eligible(a))return;
        active=new WeakReference<>(a);
        UI.removeCallbacks(periodic);UI.postDelayed(periodic,UpdatePolicy.CHECK_INTERVAL_MS);
        String message=prefs(a).getString("result", "");
        if(!message.isEmpty()){prefs(a).edit().remove("result").apply();Toast.makeText(a,message,Toast.LENGTH_LONG).show();}
        UI.postDelayed(()->{if(active.get()==a && eligible(a)){present();check(a,false);}},2500);
    }
    public static void paused(Activity a) {
        if(active.get()==a){UI.removeCallbacks(periodic);active.clear();if(dialog!=null){dialog.dismiss();dialog=null;}}
    }
    public static void check(Context context, boolean manual) {
        final Context c=context.getApplicationContext();
        final boolean requested=manual || context.getClass().getSimpleName().equals("AjustesActivity");
        UI.post(()->{
            if(context instanceof Activity && eligible((Activity)context))active=new WeakReference<>((Activity)context);
            if(requested)deferredUntil=0;
            if(checking || downloading){if(requested)toast("Verificando ou baixando a atualização…");return;}
            if(!UpdatePolicy.checkDue(SystemClock.elapsedRealtime(),lastCheck,requested))return;
            checking=true;lastCheck=SystemClock.elapsedRealtime();
            WORK.execute(()->{
                try {
                    byte[] bytes=read(MANIFEST+"?check="+System.currentTimeMillis(),512*1024);
                    Release r=new Release(new JSONObject(new String(bytes,"UTF-8")));
                    boolean newer=UpdatePolicy.newer(code(installed(c)),r.code);android.util.Log.i("IPTVibeUpdate", "Manifest verified; newer="+newer);
                    UI.post(()->{
                        checking=false;release=newer?r:null;
                        if(newer)download(c,r);else if(requested)toast("IPTVibe atualizado. Versão "+r.version+".");
                    });
                }catch(Exception e){android.util.Log.w("IPTVibeUpdate","Manifest check failed: "+e.getClass().getSimpleName());UI.post(()->{checking=false;if(requested)toast("Não foi possível verificar atualizações. Tente novamente.");});}
            });
        });
    }
    private static HttpURLConnection open(String address) throws Exception {
        URL u=new URL(address);
        for(int n=0;n<6;n++){
            String h=u.getHost();
            if(!"https".equals(u.getProtocol()) || !(h.equals("github.com") || h.equals("raw.githubusercontent.com") || h.equals("release-assets.githubusercontent.com") || h.equals("objects.githubusercontent.com")))throw new IOException();
            HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setUseCaches(false);c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent","IPTVibe-Updater");c.setRequestProperty("Accept-Encoding","identity");
            int status=c.getResponseCode();
            if(status>=300 && status<400){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException();u=new URL(u,location);continue;}
            if(status!=200){android.util.Log.w("IPTVibeUpdate","HTTP failure "+status);c.disconnect();throw new IOException();}return c;
        }throw new IOException();
    }
    private static byte[] read(String address,int max) throws Exception {
        HttpURLConnection c=open(address);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;long deadline=SystemClock.elapsedRealtime()+30000;
            while((n=in.read(b))!=-1){if(out.size()+n>max || SystemClock.elapsedRealtime()>deadline)throw new IOException();out.write(b,0,n);}return out.toByteArray();
        }finally{c.disconnect();}
    }
    private static File apk(Context c,Release r){return new File(c.getCacheDir(),"iptvibe-verified-"+r.code+".apk");}
    private static String hash(File f) throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte b:d.digest())s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
    private static Set<String> signatures(PackageInfo p) {
        android.content.pm.Signature[] s=p.signatures;
        if((s==null || s.length==0) && Build.VERSION.SDK_INT>=28 && p.signingInfo!=null)s=p.signingInfo.getApkContentsSigners();
        Set<String> values=new HashSet<>();if(s!=null)for(android.content.pm.Signature v:s)values.add(v.toCharsString());return values;
    }
    private static void verify(Context c,File f,Release r) throws Exception {
        if(f.length()!=r.size || !r.sha.equalsIgnoreCase(hash(f))){android.util.Log.w("IPTVibeUpdate","APK size or digest mismatch");throw new IOException();}
        PackageInfo archive=c.getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(),Build.VERSION.SDK_INT>=28?(PackageManager.GET_SIGNING_CERTIFICATES|PackageManager.GET_SIGNATURES):PackageManager.GET_SIGNATURES);
        PackageInfo local=installed(c);
        if(archive==null || !c.getPackageName().equals(archive.packageName) || code(archive)!=r.code || !UpdatePolicy.newer(code(local),r.code)
                || signatures(archive).isEmpty() || !signatures(local).equals(signatures(archive))){android.util.Log.w("IPTVibeUpdate","APK identity mismatch: parsed="+(archive!=null)+", code="+(archive==null?-1:code(archive))+", installed="+code(local)+", localSigners="+signatures(local).size()+", archiveSigners="+(archive==null?-1:signatures(archive).size())+", signer="+(archive!=null && signatures(local).equals(signatures(archive))));throw new IOException();}
    }
    private static void download(Context c,Release r) {
        if(downloading)return;downloading=true;
        WORK.execute(()->{
            File target=apk(c,r),partial=new File(target.getPath()+".part.apk");String phase="cached";
            try {
                if(target.exists()){try{verify(c,target,r);}catch(Exception e){target.delete();}}
                if(!target.exists()){
                    if(c.getCacheDir().getUsableSpace()<r.size*2+10*1024*1024)throw new IOException();
                    phase="connect";HttpURLConnection connection=open(r.url+"?digest="+r.sha);phase="transfer";
                    try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(partial)){
                        byte[] b=new byte[65536];int n;long count=0,deadline=SystemClock.elapsedRealtime()+10*60*1000;
                        while((n=in.read(b))!=-1){count+=n;if(count>r.size || SystemClock.elapsedRealtime()>deadline)throw new IOException();out.write(b,0,n);}
                    }finally{connection.disconnect();}
                    phase="validation";verify(c,partial,r);if(!partial.renameTo(target))throw new IOException();
                }
                UI.post(()->{downloading=false;release=r;present();});
            }catch(Exception e){android.util.Log.w("IPTVibeUpdate","Download failed at "+phase+": "+e.getClass().getSimpleName());partial.delete();UI.post(()->{downloading=false;toast("Não foi possível preparar a atualização. Seus dados foram mantidos.");});}
        });
    }
    private static void present() {
        Activity a=active.get();Release r=release;
        if(!eligible(a) || installing || dialog!=null || SystemClock.elapsedRealtime()<deferredUntil)return;
        if(confirmation!=null){Intent i=confirmation;confirmation=null;try{a.startActivity(i);}catch(Exception e){toast("Abra o app novamente para concluir a atualização.");}return;}
        if(r==null || !apk(a,r).exists())return;
        try{if(!UpdatePolicy.newer(code(installed(a)),r.code)){release=null;return;}}catch(Exception e){return;}
        // Download is automatic; Android retains final installation authority.
        install(a,r);
    }
    private static void install(Activity a,Release r) {
        if(Build.VERSION.SDK_INT>=26 && !a.getPackageManager().canRequestPackageInstalls()){
            dialog=new AlertDialog.Builder(a).setTitle("Atualização do IPTVibe")
                .setMessage("A versão "+r.version+" já foi baixada. Autorize o IPTVibe a instalar atualizações nas configurações do aparelho e volte ao app.")
                .setPositiveButton("Autorizar",(d,w)->{try{a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())));}catch(Exception e){toast("Ative a instalação de apps para o IPTVibe nas configurações do aparelho.");}})
                .setNegativeButton("Depois",(d,w)->deferredUntil=SystemClock.elapsedRealtime()+5*60*1000).create();
            dialog.setOnDismissListener(d->dialog=null);dialog.show();return;
        }
        installing=true;
        WORK.execute(()->{
            int id=-1;
            try{
                File file=apk(a,r);verify(a,file,r);
                PackageInstaller installer=a.getPackageManager().getPackageInstaller();
                int previous=prefs(a).getInt("session",-1);if(previous>=0)try{installer.abandonSession(previous);}catch(Exception ignored){}
                PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(a.getPackageName());params.setSize(r.size);
                if(Build.VERSION.SDK_INT>=31)params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                id=installer.createSession(params);
                prefs(a).edit().putInt("session",id).commit();
                try(PackageInstaller.Session session=installer.openSession(id);InputStream in=new FileInputStream(file);OutputStream out=session.openWrite("base.apk",0,r.size)){
                    byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);session.fsync(out);
                }
                final int sessionId=id;
                UI.post(()->{
                    try{
                        if(!eligible(active.get())){installer.abandonSession(sessionId);prefs(a).edit().remove("session").apply();installing=false;return;}
                        Intent callback=new Intent(a,UpdateReceiver.class).setAction(a.getPackageName()+".UPDATE_RESULT");
                        int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=PendingIntent.FLAG_MUTABLE;
                        PendingIntent pending=PendingIntent.getBroadcast(a,sessionId,callback,flags);
                        try(PackageInstaller.Session session=installer.openSession(sessionId)){session.commit(pending.getIntentSender());}
                    }catch(Exception e){try{installer.abandonSession(sessionId);}catch(Exception ignored){}installing=false;toast("Não foi possível abrir a instalação. Tente novamente.");}
                });
            }catch(Exception e){if(id>=0)try{a.getPackageManager().getPackageInstaller().abandonSession(id);}catch(Exception ignored){}UI.post(()->{installing=false;deferredUntil=SystemClock.elapsedRealtime()+5*60*1000;toast("Não foi possível iniciar a instalação. Seus dados foram mantidos.");});}
        });
    }
    public static void result(Context c,Intent intent) {
        int expected=prefs(c).getInt("session",-1),received=intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-2);
        if(expected<0 || expected!=received)return;
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        installing=false;
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){
            confirmation=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirmation!=null && eligible(active.get()))present();
            return;
        }
        prefs(c).edit().remove("session").putString("result",status==PackageInstaller.STATUS_SUCCESS?"IPTVibe atualizado com sucesso.":"Atualização não concluída. Você pode tentar novamente em Configurações.").apply();
        deferredUntil=SystemClock.elapsedRealtime()+5*60*1000;
    }
    private static void toast(String text){Activity a=active.get();if(eligible(a))Toast.makeText(a,text,Toast.LENGTH_LONG).show();}
}
