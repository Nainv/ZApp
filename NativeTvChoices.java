package com.zikra.tv;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Native themed options; both horizontal remote keys always move between choices. */
@androidx.annotation.RequiresApi(26)
final class NativeTvChoices {
    private NativeTvChoices(){}
    static Dialog show(Context context,Typeface font,boolean rtl,String title,String[] values,IntConsumer choose,IntConsumer preview,Runnable dismissed){
        Dialog dialog=new Dialog(context);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout frame=new FrameLayout(context);frame.setBackground(background(0xff10232a,context));frame.setLayoutDirection(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);
        LinearLayout box=new LinearLayout(context);box.setOrientation(LinearLayout.VERTICAL);int padding=dp(context,22);box.setPadding(padding,padding,padding,padding);frame.addView(box,new FrameLayout.LayoutParams(-1,-1));
        TextView heading=new TextView(context);heading.setTypeface(font);heading.setText(title);heading.setTextSize(24);heading.setTextColor(0xffeff4ef);box.addView(heading);
        ScrollView scroll=new ScrollView(context);scroll.setClipToPadding(false);scroll.setPadding(0,dp(context,12),0,dp(context,12));box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        GridLayout grid=new GridLayout(context);int columns=values.length==1?1:2;grid.setColumnCount(columns);grid.setLayoutDirection(rtl?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        NativeTvMotion motion=new NativeTvMotion(frame);Button[] buttons=new Button[values.length];int[] selected={0};
        for(int i=0;i<values.length;i++){final int index=i;Button button=new Button(context);buttons[i]=button;button.setDefaultFocusHighlightEnabled(false);button.setTypeface(font);button.setText(values[i]);button.setTextSize(17);button.setTextColor(0xffeff4ef);button.setAllCaps(false);button.setMaxLines(2);button.setGravity(Gravity.CENTER);button.setBackground(background(0xff1b3038,context));button.setPadding(dp(context,12),dp(context,6),dp(context,12),dp(context,6));
            GridLayout.LayoutParams size=new GridLayout.LayoutParams(GridLayout.spec(i/columns),GridLayout.spec(i%columns,1f));size.width=0;size.height=dp(context,64);size.setMargins(dp(context,7),dp(context,7),dp(context,7),dp(context,7));grid.addView(button,size);
            button.setOnFocusChangeListener((view,on)->{view.setBackground(background(on?0xff294950:0xff1b3038,context));motion.focus(view,on,false);if(on){selected[0]=index;if(preview!=null)preview.accept(index);}});
            button.setOnClickListener(view->{dialog.dismiss();choose.accept(index);});
        }
        Button back=new Button(context);back.setDefaultFocusHighlightEnabled(false);back.setText("رجوع");back.setTypeface(font);back.setTextColor(0xffeff4ef);back.setBackground(background(0xff1b3038,context));back.setOnFocusChangeListener((view,on)->motion.focus(view,on,false));back.setOnClickListener(view->dialog.dismiss());box.addView(back,new LinearLayout.LayoutParams(-1,dp(context,44)));
        dialog.setContentView(frame);dialog.setOnDismissListener(d->{motion.close();if(dismissed!=null)dismissed.run();});
        dialog.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;if(buttons.length==0)return true;if(event.getAction()==KeyEvent.ACTION_DOWN){int delta=key==KeyEvent.KEYCODE_DPAD_LEFT?(rtl?1:-1):(rtl?-1:1);int next=Math.floorMod(selected[0]+delta,buttons.length);buttons[next].requestFocus();}return true;});
        dialog.show();Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setLayout(Math.round(context.getResources().getDisplayMetrics().widthPixels*.80f),Math.round(context.getResources().getDisplayMetrics().heightPixels*.80f));}
        motion.enter(box,false);if(buttons.length>0)buttons[0].post(()->buttons[0].requestFocus());return dialog;
    }
    private static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private static GradientDrawable background(int color,Context context){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(context,16));return d;}
}
