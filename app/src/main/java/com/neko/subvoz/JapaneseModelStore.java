package com.neko.subvoz;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads and installs Vosk's official small Japanese offline model. */
public final class JapaneseModelStore {
 public interface Progress { void update(int percent); void done(File modelDir, Exception error); }
 private static final String MODEL_URL="https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip";
 private JapaneseModelStore(){}
 public static File modelDir(Context c){return new File(c.getFilesDir(),"vosk-ja/model");}
 public static boolean isInstalled(Context c){File d=modelDir(c);return new File(d,"am").isDirectory()&&new File(d,"conf").isDirectory()&&new File(d,"graph").isDirectory();}
 public static void download(Context context,Progress callback){
  Context app=context.getApplicationContext();new Thread(()->{
   File zip=new File(app.getCacheDir(),"vosk-ja.zip");File base=new File(app.getFilesDir(),"vosk-ja");File temp=new File(base,"model.tmp");File target=new File(base,"model");
   try{
    base.mkdirs();HttpURLConnection conn=(HttpURLConnection)new URL(MODEL_URL).openConnection();conn.setConnectTimeout(20000);conn.setReadTimeout(30000);conn.setInstanceFollowRedirects(true);conn.connect();
    int total=conn.getContentLength();try(InputStream in=new BufferedInputStream(conn.getInputStream());FileOutputStream out=new FileOutputStream(zip)){
     byte[] buf=new byte[64*1024];long count=0;int n,last=-1;while((n=in.read(buf))!=-1){out.write(buf,0,n);count+=n;if(total>0){int pct=(int)(count*100/total);if(pct!=last){last=pct;callback.update(pct);}}}
    }finally{conn.disconnect();}
    delete(temp);temp.mkdirs();String rootPrefix=null;
    try(ZipInputStream zin=new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))){
     ZipEntry e;byte[] buf=new byte[64*1024];while((e=zin.getNextEntry())!=null){String name=e.getName().replace('\\','/');if(name.startsWith("/")||name.contains("../"))throw new IOException("Invalid model archive");String[] parts=name.split("/",2);if(parts.length<2)continue;String rel=parts[1];if(rel.isEmpty())continue;File outFile=new File(temp,rel);if(!outFile.getCanonicalPath().startsWith(temp.getCanonicalPath()+File.separator))throw new IOException("Invalid model path");if(e.isDirectory()){outFile.mkdirs();}else{File parent=outFile.getParentFile();if(parent!=null)parent.mkdirs();try(FileOutputStream out=new FileOutputStream(outFile)){int n;while((n=zin.read(buf))!=-1)out.write(buf,0,n);}}zin.closeEntry();}
    }
    if(!new File(temp,"am").isDirectory()||!new File(temp,"conf").isDirectory()||!new File(temp,"graph").isDirectory())throw new IOException("The downloaded archive is missing model files");
    delete(target);if(!temp.renameTo(target))throw new IOException("Could not install model");zip.delete();callback.done(target,null);
   }catch(Exception e){callback.done(null,e);}
  },"subvoz-ja-model-download").start();
 }
 private static void delete(File f){if(!f.exists())return;if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File c:children)delete(c);}f.delete();}
}
