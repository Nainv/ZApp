package com.zikra.tv;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** CleanYourTV's native tile zoom, glass-position springs and panel entrance.
 * Derived from Gathin's MIT source; see assets/licenses/CleanYourTV-MIT.txt. */
@androidx.annotation.RequiresApi(26)
final class NativeTvMotion {
    final FrameLayout root;
    private final View glass;
    private final SpringAnimation x,y;
    NativeTvMotion(FrameLayout root) {
        this.root=root;glass=new View(root.getContext());
        GradientDrawable shape=new GradientDrawable();shape.setColor(Color.TRANSPARENT);shape.setCornerRadius(dp(17));
        glass.setBackground(shape);glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(glass,new FrameLayout.LayoutParams(1,1));glass.setVisibility(View.INVISIBLE);
        x=new SpringAnimation(glass,SpringAnimation.TRANSLATION_X);
        y=new SpringAnimation(glass,SpringAnimation.TRANSLATION_Y);
        for(SpringAnimation spring:new SpringAnimation[]{x,y})spring.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(.58f));
    }
    private int dp(float value){return Math.round(value*root.getResources().getDisplayMetrics().density);}
    private boolean reduced(){return !ValueAnimator.areAnimatorsEnabled();}
    void focus(View view,boolean focused,boolean tile) {
        view.animate().cancel();float scale=focused?(tile?1.10f:1.04f):1f;
        if(reduced()){view.setScaleX(scale);view.setScaleY(scale);}else view.animate().scaleX(scale).scaleY(scale)
                .setDuration(focused?260:180).setInterpolator(new OvershootInterpolator(focused?1.6f:0f)).withLayer().start();
        view.setElevation(focused?dp(8):0);
        if(focused){view.post(()->position(view));}
    }
    void position(View view) {
        if(!view.isAttachedToWindow()||!view.hasFocus())return;
        Rect rect=new Rect(0,0,view.getWidth(),view.getHeight());root.offsetDescendantRectToMyCoords(view,rect);
        ViewGroup.LayoutParams size=glass.getLayoutParams();size.width=rect.width()+dp(8);size.height=rect.height()+dp(8);glass.setLayoutParams(size);
        float left=rect.left-dp(4),top=rect.top-dp(4);
        if(glass.getVisibility()!=View.VISIBLE||reduced()){x.cancel();y.cancel();glass.setTranslationX(left);glass.setTranslationY(top);}else{x.animateToFinalPosition(left);y.animateToFinalPosition(top);}
        glass.setVisibility(View.VISIBLE);glass.bringToFront();
    }
    void hide(){x.cancel();y.cancel();glass.setVisibility(View.INVISIBLE);}
    void enter(View panel,boolean back){
        hide();if(reduced())return;panel.animate().cancel();panel.setAlpha(0f);panel.setTranslationX(dp(back?-28:28));
        panel.animate().alpha(1f).translationX(0).setDuration(220).setInterpolator(new DecelerateInterpolator(1.6f)).withLayer().start();
    }
    void press(View view){if(reduced())return;view.animate().cancel();view.setAlpha(.64f);view.animate().alpha(1f).setDuration(120).start();}
    void close(){hide();root.removeView(glass);}
}
