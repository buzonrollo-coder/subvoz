package com.neko.subvoz;

import android.app.*;
import android.content.*;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
 private Spinner language, region;
 private static final String[] LANGUAGES={
  "Español → voz en español", "Inglés → español", "Japonés → español",
  "Chino → español", "Coreano → español", "Francés → español", "Portugués → español"
 };
 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(32,40,32,24); setContentView(root);
  TextView title=new TextView(this); title.setText("SubVoz · prototipo\nSubtítulos visibles → voz en español"); title.setTextSize(23); root.addView(title);
  TextView info=new TextView(this); info.setText("Detecta los subtítulos en la pantalla, los traduce y los lee en voz alta en español. Las frases detectadas se ponen en cola para no perderlas mientras la voz habla.\n\nElige el idioma original. La primera vez se descargan los modelos de traducción. Usa una voz española instalada en Android. No escucha el micrófono ni guarda el video.\n"); root.addView(info);
  language=new Spinner(this); language.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,LANGUAGES)); root.addView(language);
  region=new Spinner(this); region.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Franja inferior (último 35%)","Franja central (35–70%)","Franja superior (primer 35%)"})); root.addView(region);
  Button permission=new Button(this); permission.setText("1. Permitir ventana flotante"); root.addView(permission);
  permission.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
  Button start=new Button(this); start.setText("2. Iniciar lectura"); root.addView(start);
  start.setOnClickListener(v->{if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Activa primero el permiso de ventana flotante",Toast.LENGTH_LONG).show();return;} MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);startActivityForResult(m.createScreenCaptureIntent(),123);});
  Button stop=new Button(this); stop.setText("Detener"); root.addView(stop); stop.setOnClickListener(v->stopService(new Intent(this,CaptureService.class)));
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==123 && result==RESULT_OK && data!=null){Intent i=new Intent(this,CaptureService.class);i.putExtra("code",result);i.putExtra("data",data);i.putExtra("source",language.getSelectedItemPosition());i.putExtra("region",region.getSelectedItemPosition());startForegroundService(i);}}
}
