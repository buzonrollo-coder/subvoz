package com.neko.subvoz;


import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.*;
import android.widget.*;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.*;
import java.util.*;

public class MainActivity extends Activity {
 private Spinner language, region, voiceSpinner, styleSpinner;
 private TextView modelStatus, rateValue, pitchValue, sizeValue;
 private SeekBar rateSeek, pitchSeek, sizeSeek;
 private TextToSpeech tts; private final ArrayList<Voice> spanishVoices=new ArrayList<>();
 private android.content.SharedPreferences prefs;
 private static final int NAVY=Color.rgb(17,26,58), CYAN=Color.rgb(8,199,232), GOLD=Color.rgb(255,192,77);
 private static final String[] LANGUAGES={"Español · leer en español","Inglés · traducir al español","Japonés · traducir al español","Chino · traducir al español","Coreano · traducir al español","Francés · traducir al español","Portugués · traducir al español"};
 private static final String[] ML_LANG={null,TranslateLanguage.ENGLISH,TranslateLanguage.JAPANESE,TranslateLanguage.CHINESE,TranslateLanguage.KOREAN,TranslateLanguage.FRENCH,TranslateLanguage.PORTUGUESE};
 @Override public void onCreate(Bundle b) {
  super.onCreate(b);prefs=getSharedPreferences("subvoz",MODE_PRIVATE);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(245,247,251));setContentView(scroll);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(24),dp(20),dp(30));scroll.addView(root);
  LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(22),dp(24),dp(22),dp(23));hero.setBackground(round(NAVY,22));root.addView(hero);
  TextView brand=new TextView(this);brand.setText("SubVoz");brand.setTextColor(Color.WHITE);brand.setTextSize(30);brand.setTypeface(null,Typeface.BOLD);hero.addView(brand);
  TextView tagline=new TextView(this);tagline.setText("Tus subtítulos, en voz alta.");tagline.setTextColor(Color.rgb(203,221,239));tagline.setTextSize(16);hero.addView(tagline);
  TextView badge=new TextView(this);badge.setText("CAPTURA EN PANTALLA  ·  VOZ EN ESPAÑOL");badge.setTextColor(NAVY);badge.setTextSize(11);badge.setTypeface(null,Typeface.BOLD);badge.setPadding(dp(9),dp(7),dp(9),dp(7));badge.setBackground(round(GOLD,20));LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-2,-2);blp.topMargin=dp(17);hero.addView(badge,blp);
  sectionTitle(root,"Configura tu lectura","Elige el idioma original de los subtítulos.");
  language=new Spinner(this);language.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,LANGUAGES));styleSpinner(language);root.addView(language,fieldMargins());language.setSelection(prefs.getInt("source",1));language.setOnItemSelectedListener(new SimpleSelect(()->refreshModelStatus()));
  TextView regionLabel=smallLabel("Zona de lectura: ajusta el centro de la franja");root.addView(regionLabel);
  region=new Spinner(this);region.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Abajo · franja inferior","Centro · franja central","Arriba · franja superior","Personalizada · usar ajuste fino"}));styleSpinner(region);root.addView(region,fieldMargins());region.setSelection(prefs.getInt("region",0));region.setOnItemSelectedListener(new SimpleSelect(()->prefs.edit().putInt("region",region.getSelectedItemPosition()).apply()));
  sectionTitle(root,"Ajustes","Personaliza modelos, voz y apariencia.");
  modelStatus=new TextView(this);modelStatus.setTextColor(Color.rgb(89,101,121));modelStatus.setTextSize(14);root.addView(modelStatus,fieldMargins());
  LinearLayout modelButtons=new LinearLayout(this);Button download=button("Descargar idioma",true);modelButtons.addView(download,new LinearLayout.LayoutParams(0,-2,1));Button remove=button("Borrar modelo",false);LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(0,-2,1);rlp.leftMargin=dp(8);modelButtons.addView(remove,rlp);root.addView(modelButtons,fieldMargins());download.setOnClickListener(v->downloadSelectedModel());remove.setOnClickListener(v->deleteSelectedModel());
  TextView voiceLabel=smallLabel("Voz española de Android");root.addView(voiceLabel);
  voiceSpinner=new Spinner(this);voiceSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Buscando voces españolas…"}));styleSpinner(voiceSpinner);root.addView(voiceSpinner,fieldMargins());
  tts=new TextToSpeech(this,result->{if(result==TextToSpeech.SUCCESS)loadSpanishVoices();});
  Button test=button("Escuchar voz de prueba",false);root.addView(test,fieldMargins());test.setOnClickListener(v->testVoice());
  Button androidTts=button("Administrar o descargar voces de Android",false);root.addView(androidTts,fieldMargins());androidTts.setOnClickListener(v->{try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e){Toast.makeText(this,"Abre Ajustes de Android → Síntesis de voz",Toast.LENGTH_LONG).show();}});
  rateSeek=addSeek(root,"Velocidad",0,100,prefs.getInt("rate",50),value->{float f=.55f+value/100f*1.05f;rateValue.setText(String.format(Locale.getDefault(),"%.2fx",f));prefs.edit().putInt("rate",value).apply();});
  pitchSeek=addSeek(root,"Tono",0,100,prefs.getInt("pitch",50),value->{float f=.65f+value/100f*0.7f;pitchValue.setText(String.format(Locale.getDefault(),"%.2fx",f));prefs.edit().putInt("pitch",value).apply();});
  TextView styleLabel=smallLabel("Estilo de subtítulos flotantes");root.addView(styleLabel);
  styleSpinner=new Spinner(this);styleSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Oscuro · alto contraste","Claro · fondo blanco","Cian · fondo azul verdoso"}));styleSpinner.setBackground(round(Color.WHITE,13));root.addView(styleSpinner,fieldMargins());styleSpinner.setSelection(prefs.getInt("style",0));styleSpinner.setOnItemSelectedListener(new SimpleSelect(()->prefs.edit().putInt("style",styleSpinner.getSelectedItemPosition()).apply()));
  sizeSeek=addSeek(root,"Tamaño del texto flotante",12,24,prefs.getInt("textSize",16),value->{sizeValue.setText(value+" sp");prefs.edit().putInt("textSize",value).apply();});
  SeekBar position=new SeekBar(this);sectionTitle(root,"Zona personalizada","Ajusta el centro vertical de la franja que SubVoz revisa.");position.setMax(60);position.setProgress(prefs.getInt("zone",30));root.addView(position,fieldMargins());TextView posVal=smallLabel("Centro de franja: "+(20+position.getProgress())+"% de la pantalla");root.addView(posVal);position.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){posVal.setText("Centro de franja: "+(20+p)+"% de la pantalla");if(u)prefs.edit().putInt("zone",p).apply();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
  TextView note=new TextView(this);note.setText("Los modelos de traducción se descargan de Google ML Kit y procesan el texto en el dispositivo. La voz procede de los motores instalados en Android; algunas voces podrían requerir internet.");note.setTextColor(Color.rgb(70,83,105));note.setTextSize(13);note.setPadding(dp(14),dp(13),dp(14),dp(13));note.setBackground(round(Color.rgb(229,247,251),14));root.addView(note,fieldMargins());
  sectionTitle(root,"Permisos","Se requieren para capturar la pantalla compartida y mostrar la ventana flotante.");Button permission=button("1   Permitir ventana flotante",false);root.addView(permission,fieldMargins());permission.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
  Button start=button("2   Iniciar lectura",true);root.addView(start,fieldMargins());start.setOnClickListener(v->{if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Activa primero el permiso de ventana flotante",Toast.LENGTH_LONG).show();return;}MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);startActivityForResult(m.createScreenCaptureIntent(),123);});
  Button stop=button("Detener lectura",false);root.addView(stop,fieldMargins());stop.setOnClickListener(v->stopService(new Intent(this,CaptureService.class)));
  refreshModelStatus();
 }
 private class SimpleSelect implements AdapterView.OnItemSelectedListener{Runnable action;SimpleSelect(Runnable r){action=r;}public void onItemSelected(AdapterView<?> a,View v,int p,long id){action.run();}public void onNothingSelected(AdapterView<?> a){}}
 private void loadSpanishVoices(){if(tts.getVoices()!=null)for(Voice v:tts.getVoices())if(v.getLocale().getLanguage().equals("es"))spanishVoices.add(v);Collections.sort(spanishVoices,(a,b)->a.getName().compareToIgnoreCase(b.getName()));ArrayList<String> names=new ArrayList<>();for(Voice v:spanishVoices)names.add(v.getName()+(v.isNetworkConnectionRequired()?" · en línea":" · sin conexión"));if(names.isEmpty())names.add("No hay voces españolas instaladas");voiceSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));String saved=prefs.getString("voice","");for(int i=0;i<spanishVoices.size();i++)if(spanishVoices.get(i).getName().equals(saved)){voiceSpinner.setSelection(i);break;}}
 private void testVoice(){if(tts==null||spanishVoices.isEmpty()){new AlertDialog.Builder(this).setMessage("No se encontró una voz española. Instala una en Ajustes de Android → Síntesis de voz.").setPositiveButton("Abrir ajustes",(d,w)->startActivity(new Intent("com.android.settings.TTS_SETTINGS"))).setNegativeButton("Cerrar",null).show();return;}saveVoice();int selected=voiceSpinner.getSelectedItemPosition();if(selected>=0&&selected<spanishVoices.size())tts.setVoice(spanishVoices.get(selected));float rate=.55f+prefs.getInt("rate",50)/100f*1.05f;float pitch=.65f+prefs.getInt("pitch",50)/100f*.7f;tts.setSpeechRate(rate);tts.setPitch(pitch);tts.speak("Hola, esta es una prueba de voz de SubVoz.",TextToSpeech.QUEUE_FLUSH,null,"test");}
 private void saveVoice(){int p=voiceSpinner.getSelectedItemPosition();if(p>=0&&p<spanishVoices.size())prefs.edit().putString("voice",spanishVoices.get(p).getName()).apply();}
 private void refreshModelStatus(){if(modelStatus==null||language==null)return;int source=language.getSelectedItemPosition();if(source<=0){modelStatus.setText("Español: no necesita modelo de traducción.");return;}modelStatus.setText("Consultando estado del modelo…");TranslateRemoteModel model=new TranslateRemoteModel.Builder(ML_LANG[source]).build();RemoteModelManager.getInstance().isModelDownloaded(model).addOnSuccessListener(ok->{if(modelStatus!=null)modelStatus.setText((ok?"✓ Descargado · disponible sin conexión":"○ No descargado · requiere internet la primera vez")+"\nIdioma: "+LANGUAGES[source]);}).addOnFailureListener(e->{if(modelStatus!=null)modelStatus.setText("No se pudo consultar el modelo. Revisa la conexión.");});}
 private void downloadSelectedModel(){int source=language.getSelectedItemPosition();if(source<=0){Toast.makeText(this,"Español no necesita traducción",Toast.LENGTH_SHORT).show();return;}modelStatus.setText("Descargando modelo de "+LANGUAGES[source]+"…");TranslateRemoteModel model=new TranslateRemoteModel.Builder(ML_LANG[source]).build();RemoteModelManager.getInstance().download(model,new DownloadConditions.Builder().build()).addOnSuccessListener(v->{modelStatus.setText("✓ Modelo descargado; podrás traducir sin conexión.");Toast.makeText(this,"Descarga completada",Toast.LENGTH_LONG).show();}).addOnFailureListener(e->{modelStatus.setText("No se pudo descargar. Comprueba internet e inténtalo de nuevo.");});}
 private void deleteSelectedModel(){int source=language.getSelectedItemPosition();if(source<=0){Toast.makeText(this,"No hay modelo de español que borrar",Toast.LENGTH_SHORT).show();return;}new AlertDialog.Builder(this).setTitle("Borrar modelo descargado").setMessage("Se eliminará el modelo de "+LANGUAGES[source]+". Para volver a traducir ese idioma tendrás que descargarlo otra vez.").setNegativeButton("Cancelar",null).setPositiveButton("Borrar",(d,w)->{TranslateRemoteModel model=new TranslateRemoteModel.Builder(ML_LANG[source]).build();RemoteModelManager.getInstance().deleteDownloadedModel(model).addOnSuccessListener(v->refreshModelStatus()).addOnFailureListener(e->modelStatus.setText("No se pudo borrar el modelo."));}).show();}
 private SeekBar addSeek(LinearLayout root,String title,int min,int max,int progress,java.util.function.IntConsumer changed){TextView label=smallLabel(title);root.addView(label);SeekBar s=new SeekBar(this);s.setMax(max-min);s.setProgress(Math.max(0,Math.min(max-min,progress-min)));root.addView(s,fieldMargins());TextView value=smallLabel("");root.addView(value);if(title.equals("Velocidad"))rateValue=value;else if(title.equals("Tono"))pitchValue=value;else sizeValue=value;changed.accept(s.getProgress()+min);s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){changed.accept(p+min);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});return s;}
 private void sectionTitle(LinearLayout root,String title,String subtitle){TextView t=new TextView(this);t.setText(title);t.setTextColor(NAVY);t.setTextSize(19);t.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(23);root.addView(t,p);TextView s=new TextView(this);s.setText(subtitle);s.setTextColor(Color.rgb(89,101,121));s.setTextSize(14);LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(-1,-2);q.topMargin=dp(4);q.bottomMargin=dp(10);root.addView(s,q);}
 private TextView smallLabel(String value){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.rgb(52,64,86));t.setTextSize(14);t.setTypeface(null,Typeface.BOLD);return t;}
 private void styleSpinner(Spinner s){s.setPadding(dp(10),dp(8),dp(10),dp(8));s.setBackground(round(Color.WHITE,13));}
 private Button button(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(null,Typeface.BOLD);b.setTextColor(primary?Color.WHITE:NAVY);b.setBackground(round(primary?NAVY:Color.WHITE,15));return b;}
 private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private LinearLayout.LayoutParams fieldMargins(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);return p;}
 private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
 @Override protected void onPause(){super.onPause();if(language!=null&&language.getSelectedItemPosition()>=0)prefs.edit().putInt("source",language.getSelectedItemPosition()).apply();if(region!=null&&region.getSelectedItemPosition()>=0)prefs.edit().putInt("region",region.getSelectedItemPosition()).apply();if(voiceSpinner!=null)saveVoice();if(styleSpinner!=null)prefs.edit().putInt("style",styleSpinner.getSelectedItemPosition()).apply();}
 @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==123&&result==RESULT_OK&&data!=null){saveVoice();Intent i=new Intent(this,CaptureService.class);i.putExtra("code",result);i.putExtra("data",data);i.putExtra("source",language.getSelectedItemPosition());i.putExtra("region",region.getSelectedItemPosition());i.putExtra("zone",prefs.getInt("zone",30));i.putExtra("style",styleSpinner.getSelectedItemPosition());i.putExtra("textSize",prefs.getInt("textSize",16));i.putExtra("rate",prefs.getInt("rate",50));i.putExtra("pitch",prefs.getInt("pitch",50));i.putExtra("voice",prefs.getString("voice",""));startForegroundService(i);}}
}
