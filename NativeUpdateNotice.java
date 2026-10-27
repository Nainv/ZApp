package com.zikra.tv;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;

/** Native Home update badge; transfer state stays here rather than in a bottom message. */
@androidx.annotation.RequiresApi(26)
final class NativeUpdateNotice extends Button {
    private ObjectAnimator pulse;
    private boolean active;
    NativeUpdateNotice(Context context,Typeface face,NativeTvMotion motion,Runnable update){
        super(context);setTypeface(face);setTextSize(13);setTextColor(0xffffeeee);setAllCaps(false);setDefaultFocusHighlightEnabled(false);setPadding(12,0,12,0);setMinHeight(0);setMinimumHeight(0);
        GradientDrawable background=new GradientDrawable();background.setColor(0xff793e48);background.setCornerRadius(14*context.getResources().getDisplayMetrics().density);setBackground(background);setVisibility(GONE);
        setOnFocusChangeListener((view,focused)->{motion.focus(view,focused,false);animatePulse();});setOnClickListener(view->update.run());
    }
    void state(boolean available,boolean busy,String label,boolean resumed){
        setVisibility(available||busy?VISIBLE:GONE);if(!label.contentEquals(getText()))setText(label);active=available&&!busy&&resumed;setContentDescription(label);animatePulse();
    }
    private void animatePulse(){
        boolean run=active&&getVisibility()==VISIBLE&&!hasFocus()&&isAttachedToWindow()&&ValueAnimator.areAnimatorsEnabled();
        if(!run){stopPulse();return;}if(pulse!=null)return;
        pulse=ObjectAnimator.ofFloat(this,View.ALPHA,.55f,1f);pulse.setDuration(850);pulse.setRepeatMode(ValueAnimator.REVERSE);pulse.setRepeatCount(ValueAnimator.INFINITE);pulse.start();
    }
    void stopPulse(){if(pulse!=null){pulse.cancel();pulse=null;}setAlpha(1f);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();animatePulse();}
    @Override protected void onDetachedFromWindow(){stopPulse();super.onDetachedFromWindow();}
}
