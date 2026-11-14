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
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
 private Spinner language, region;
 private static final int NAVY=Color.rgb(17,26,58), CYAN=Color.rgb(8,199,232), GOLD=Color.rgb(255,192,77);
 private static final String[] LANGUAGES={"Español · leer en español","Inglés · traducir al español","Japonés · traducir al español","Chino · traducir al español","Coreano · traducir al español","Francés · traducir al español","Portugués · traducir al español"};
 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(245,247,251));setContentView(scroll);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(24),dp(20),dp(28));scroll.addView(root);
  LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(22),dp(24),dp(22),dp(23));hero.setBackground(round(NAVY,22));root.addView(hero);
  TextView brand=new TextView(this);brand.setText("SubVoz");brand.setTextColor(Color.WHITE);brand.setTextSize(30);brand.setTypeface(null,Typeface.BOLD);hero.addView(brand);
  TextView tagline=new TextView(this);tagline.setText("Tus subtítulos, en voz alta.");tagline.setTextColor(Color.rgb(203,221,239));tagline.setTextSize(16);LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2);tlp.topMargin=dp(4);hero.addView(tagline,tlp);
  TextView badge=new TextView(this);badge.setText("  CAPTURA EN PANTALLA  ·  VOZ EN ESPAÑOL  ");badge.setTextColor(NAVY);badge.setTextSize(11);badge.setTypeface(null,Typeface.BOLD);badge.setPadding(dp(9),dp(7),dp(9),dp(7));badge.setBackground(round(GOLD,20));LinearLayout.LayoutParams blp=new LinearLayout.LayoutParams(-2,-2);blp.topMargin=dp(17);hero.addView(badge,blp);
  sectionTitle(root,"Configura tu lectura","Elige el idioma original de los subtítulos.");
  language=new Spinner(this);language.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,LANGUAGES));styleSpinner(language);root.addView(language,fieldMargins());
  TextView regionLabel=smallLabel("¿Dónde aparecen los subtítulos?");root.addView(regionLabel);
  region=new Spinner(this);region.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Abajo · franja inferior","Centro · franja central","Arriba · franja superior"}));styleSpinner(region);root.addView(region,fieldMargins());
  TextView note=new TextView(this);note.setText("La primera vez que uses un idioma, necesitas internet para descargar su modelo. Para oír la traducción, instala una voz española en los ajustes de síntesis de voz de Android.");note.setTextColor(Color.rgb(70,83,105));note.setTextSize(14);note.setPadding(dp(14),dp(13),dp(14),dp(13));note.setBackground(round(Color.rgb(229,247,251),14));LinearLayout.LayoutParams nlp=fieldMargins();nlp.topMargin=dp(4);root.addView(note,nlp);
  sectionTitle(root,"Permisos","Solo se necesitan para mostrar y leer los subtítulos en pantalla.");
  Button permission=button("1   Permitir ventana flotante",false);root.addView(permission,fieldMargins());
  permission.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
  Button start=button("2   Iniciar lectura",true);LinearLayout.LayoutParams slp=fieldMargins();slp.topMargin=dp(10);root.addView(start,slp);
  start.setOnClickListener(v->{if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Activa primero el permiso de ventana flotante",Toast.LENGTH_LONG).show();return;}MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);startActivityForResult(m.createScreenCaptureIntent(),123);});
  Button stop=button("Detener lectura",false);LinearLayout.LayoutParams stp=fieldMargins();stp.topMargin=dp(7);root.addView(stop,stp);stop.setOnClickListener(v->stopService(new Intent(this,CaptureService.class)));
  TextView footer=new TextView(this);footer.setText("SubVoz · prototipo en desarrollo");footer.setTextColor(Color.rgb(115,126,144));footer.setTextSize(12);footer.setGravity(Gravity.CENTER);LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(-1,-2);flp.topMargin=dp(22);root.addView(footer,flp);
 }
 private void sectionTitle(LinearLayout root,String title,String subtitle){TextView t=new TextView(this);t.setText(title);t.setTextColor(NAVY);t.setTextSize(19);t.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(23);root.addView(t,p);TextView s=new TextView(this);s.setText(subtitle);s.setTextColor(Color.rgb(89,101,121));s.setTextSize(14);LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(-1,-2);q.topMargin=dp(4);q.bottomMargin=dp(10);root.addView(s,q);}
 private TextView smallLabel(String value){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.rgb(52,64,86));t.setTextSize(14);t.setTypeface(null,Typeface.BOLD);return t;}
 private void styleSpinner(Spinner s){s.setPadding(dp(10),dp(8),dp(10),dp(8));s.setBackground(round(Color.WHITE,13));}
 private Button button(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(15);b.setTypeface(null,Typeface.BOLD);b.setTextColor(primary?Color.WHITE:NAVY);b.setBackground(round(primary?NAVY:Color.WHITE,15));if(primary)b.setCompoundDrawablePadding(dp(6));return b;}
 private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private LinearLayout.LayoutParams fieldMargins(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);return p;}
 private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==123&&result==RESULT_OK&&data!=null){Intent i=new Intent(this,CaptureService.class);i.putExtra("code",result);i.putExtra("data",data);i.putExtra("source",language.getSelectedItemPosition());i.putExtra("region",region.getSelectedItemPosition());startForegroundService(i);}}
}
