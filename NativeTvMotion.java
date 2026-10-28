package com.zikra.tv;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Gravity;
import android.widget.Button;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** CleanYourTV's native tile zoom, glass-position springs and panel entrance.
 * Derived from Gathin's MIT source; see assets/licenses/CleanYourTV-MIT.txt. */
@androidx.annotation.RequiresApi(25)
final class NativeTvMotion {
    final FrameLayout root;
    private final View glass;
    private final SpringAnimation x,y;
    private View target;
    private float targetX=Float.NaN,targetY=Float.NaN;
    private final Rect viewBounds=new Rect(),rootBounds=new Rect();
    private final android.view.ViewTreeObserver.OnPreDrawListener drawWatch;
    private final android.view.ViewTreeObserver.OnGlobalFocusChangeListener focusWatch;
    private final android.view.ViewTreeObserver.OnWindowFocusChangeListener windowWatch;
    NativeTvMotion(FrameLayout root) {
        this.root=root;glass=new View(root.getContext());
        GradientDrawable shape=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0x3479c9b2,0x0a79c9b2});shape.setCornerRadius(dp(17));
        glass.setBackground(shape);glass.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        glass.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);glass.setFocusable(false);glass.setClickable(false);
        root.addView(glass,new FrameLayout.LayoutParams(1,1,Gravity.TOP|Gravity.LEFT));glass.setVisibility(View.INVISIBLE);
        x=new SpringAnimation(glass,SpringAnimation.TRANSLATION_X);
        y=new SpringAnimation(glass,SpringAnimation.TRANSLATION_Y);
        // Keep the focus glass attached to the focused item with almost no spring bounce.
        for(SpringAnimation spring:new SpringAnimation[]{x,y})spring.setSpring(new SpringForce().setStiffness(520f).setDampingRatio(.98f));
        drawWatch=()->{if(root.hasWindowFocus()&&target!=null&&target.hasFocus())syncBounds(target);return true;};root.getViewTreeObserver().addOnPreDrawListener(drawWatch);
        focusWatch=(previous,current)->{if(current==null){hide();return;}if(current.getOnFocusChangeListener()==null)current.post(()->{if(root.findFocus()==current)position(current);});};
        windowWatch=focused->{if(!focused){hide();return;}View current=target!=null&&target.hasFocus()?target:root.findFocus();if(current!=null)current.post(()->{if(current.hasFocus())position(current);});};
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(focusWatch);root.getViewTreeObserver().addOnWindowFocusChangeListener(windowWatch);
    }
    private int dp(float value){return Math.round(value*root.getResources().getDisplayMetrics().density);}
    private boolean reduced(){return !(android.os.Build.VERSION.SDK_INT<26||ValueAnimator.areAnimatorsEnabled());}
    void focus(View view,boolean focused,boolean tile) {
        view.animate().cancel();float scale=focused?(view instanceof Button?1.025f:1.012f):1f;
        if(reduced()){view.setScaleX(scale);view.setScaleY(scale);}else view.animate().scaleX(scale).scaleY(scale)
                .setDuration(focused?145:110).setInterpolator(new DecelerateInterpolator(1.4f)).withLayer().start();
        // The glass highlight is the focus cue; elevation casts a second shadow that drifts away from tiles.
        view.setElevation(0f);view.setTranslationZ(0f);
        if(focused){view.post(()->position(view));}
    }
    void position(View view) {
        if(!view.isAttachedToWindow()||!view.hasFocus())return;
        target=view;syncBounds(view);
    }
    private void syncBounds(View view){
        if(!view.getGlobalVisibleRect(viewBounds)||!root.getGlobalVisibleRect(rootBounds)){hide();return;}
        // Physical window bounds include RTL placement, nested scrolling, panel translations and zoom.
        ViewGroup.LayoutParams size=glass.getLayoutParams();int width=viewBounds.width()+dp(8),height=viewBounds.height()+dp(8);
        if(size.width!=width||size.height!=height){size.width=width;size.height=height;glass.setLayoutParams(size);}
        float left=viewBounds.left-rootBounds.left-dp(4)-glass.getLeft(),top=viewBounds.top-rootBounds.top-dp(4)-glass.getTop();
        if(glass.getVisibility()!=View.VISIBLE||reduced()){x.cancel();y.cancel();glass.setTranslationX(left);glass.setTranslationY(top);targetX=left;targetY=top;}
        else{if(left!=targetX){targetX=left;x.animateToFinalPosition(left);}if(top!=targetY){targetY=top;y.animateToFinalPosition(top);}}
        glass.setVisibility(View.VISIBLE);if(root.getChildAt(root.getChildCount()-1)!=glass)glass.bringToFront();
    }
    void hide(){target=null;x.cancel();y.cancel();targetX=Float.NaN;targetY=Float.NaN;glass.setVisibility(View.INVISIBLE);}
    void enter(View panel,boolean back){
        hide();if(reduced()){View current=root.findFocus();if(current!=null)current.post(()->position(current));return;}panel.animate().cancel();panel.setAlpha(0f);panel.setTranslationX(dp(back?-12:12));
        panel.animate().alpha(1f).translationX(0).setDuration(160).setInterpolator(new DecelerateInterpolator(1.4f)).withEndAction(()->{View current=target!=null&&target.hasFocus()?target:root.findFocus();if(current!=null)position(current);}).withLayer().start();
    }
    void press(View view){if(reduced())return;android.animation.ObjectAnimator flash=android.animation.ObjectAnimator.ofFloat(view,View.ALPHA,.84f,1f);flash.setDuration(120);flash.start();android.animation.ObjectAnimator jump=android.animation.ObjectAnimator.ofFloat(view,View.TRANSLATION_Y,view.getTranslationY(),-dp(1),0);jump.setDuration(130);jump.setInterpolator(new DecelerateInterpolator(1.4f));jump.start();}
    void close(){hide();if(root.getViewTreeObserver().isAlive()){root.getViewTreeObserver().removeOnPreDrawListener(drawWatch);root.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusWatch);root.getViewTreeObserver().removeOnWindowFocusChangeListener(windowWatch);}root.removeView(glass);}
}
