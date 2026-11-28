package com.neko.subvoz;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** On-demand installer for official Vosk small mobile models. Model files stay in app-private storage. */
public final class OfflineSpeechModelStore {
 public static final String[] IDS={"es","en","ja","fr","pt"};
 public static final String[] NAMES={"Español","Inglés (EE. UU.)","Japonés","Francés","Portugués (Brasil)"};
 public static final String[] URLS={
  "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
  "https://alphacephei.com/vosk/models/vosk-model-small-en-us-zamia-0.5.zip",
  "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
  "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
  "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip"};
 public interface Progress { void update(int percent); void done(File modelDir, Exception error); }
 private OfflineSpeechModelStore(){}
 public static int indexOf(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return -1;}
 public static File modelDir(Context c,String id){return new File(new File(c.getFilesDir(),"offline-speech"),id+"/model");}
 public static boolean isInstalled(Context c,String id){File d=modelDir(c,id);return new File(d,"am").isDirectory()&&new File(d,"conf").isDirectory()&&new File(d,"graph").isDirectory();}
 public static void delete(Context c,String id){File base=modelDir(c,id).getParentFile();deleteTree(base);}
 public static void download(Context context,String id,Progress callback){
  int ix=indexOf(id);if(ix<0){callback.done(null,new IllegalArgumentException("Unknown model"));return;}
  Context app=context.getApplicationContext();new Thread(()->{
   File base=modelDir(app,id).getParentFile(),zip=new File(app.getCacheDir(),"vosk-"+id+".zip"),temp=new File(base,"model.tmp"),target=modelDir(app,id);
   try{
    if(!base.exists()&&!base.mkdirs())throw new IOException("Cannot create model directory");
    HttpURLConnection conn=(HttpURLConnection)new URL(URLS[ix]).openConnection();conn.setConnectTimeout(20000);conn.setReadTimeout(30000);conn.setInstanceFollowRedirects(true);conn.connect();int code=conn.getResponseCode();if(code<200||code>=300)throw new IOException("Model download HTTP "+code);int total=conn.getContentLength();
    try(InputStream in=new BufferedInputStream(conn.getInputStream());FileOutputStream out=new FileOutputStream(zip)){byte[] buf=new byte[65536];long count=0;int n,last=-1;while((n=in.read(buf))!=-1){out.write(buf,0,n);count+=n;if(total>0){int pct=(int)(count*100/total);if(pct!=last){last=pct;callback.update(pct);}}}}finally{conn.disconnect();}
    deleteTree(temp);if(!temp.mkdirs())throw new IOException("Cannot create temporary model directory");
    try(ZipInputStream zin=new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))){ZipEntry e;byte[] buf=new byte[65536];while((e=zin.getNextEntry())!=null){String name=e.getName().replace('\\','/');if(name.startsWith("/")||name.contains("../"))throw new IOException("Invalid model archive");String[] parts=name.split("/",2);if(parts.length<2||parts[1].isEmpty())continue;File outFile=new File(temp,parts[1]);if(!outFile.getCanonicalPath().startsWith(temp.getCanonicalPath()+File.separator))throw new IOException("Invalid model path");if(e.isDirectory())outFile.mkdirs();else{File parent=outFile.getParentFile();if(parent!=null)parent.mkdirs();try(FileOutputStream out=new FileOutputStream(outFile)){int n;while((n=zin.read(buf))!=-1)out.write(buf,0,n);}}zin.closeEntry();}}
    if(!new File(temp,"am").isDirectory()||!new File(temp,"conf").isDirectory()||!new File(temp,"graph").isDirectory())throw new IOException("Archive is missing Vosk model files");deleteTree(target);if(!temp.renameTo(target))throw new IOException("Could not install model");zip.delete();callback.done(target,null);
   }catch(Exception e){deleteTree(temp);callback.done(null,e);}
  },"subvoz-model-"+id).start();
 }
 private static void deleteTree(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File child:children)deleteTree(child);}f.delete();}
}
