package com.airbnb.lottiesplash;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.OnCompositionLoadedListener;

import java.util.ArrayList;
import java.util.List;

public class LottieFontViewGroup extends FrameLayout {
  private final List<LottieAnimationView> views = new ArrayList<>();

  @Nullable
  private LottieAnimationView cursorView;

  public LottieFontViewGroup(Context context) {
    super(context);
    init();
  }

  public LottieFontViewGroup(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public LottieFontViewGroup(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private int textSize;

  private void init() {
    final float density = getContext().getResources().getDisplayMetrics().density;
    textSize = (int)(density*120);

    for(int x=0; x<5; x++) {
      cursorView = new LottieAnimationView(getContext());
      cursorView.setLayoutParams(new LayoutParams(
          textSize,
          textSize
      ));
      addView(cursorView);
      views.add(cursorView);
    }



    this.postDelayed(new Runnable() {
      @Override public void run() {
        LottieComposition.Factory.fromAssetFileName(getContext(), "A.json",
            new OnCompositionLoadedListener() {
              @Override
              public void onCompositionLoaded(LottieComposition composition) {
                  views.get(0).setBackgroundDrawable(new ColorDrawable(0x112233));
                views.get(0).setComposition(composition);
                views.get(0).playAnimation();
              }
            });
      }
    },300);

    this.postDelayed(new Runnable() {
      @Override public void run() {
        LottieComposition.Factory.fromAssetFileName(getContext(), "T.json",
            new OnCompositionLoadedListener() {
              @Override
              public void onCompositionLoaded(LottieComposition composition) {
                views.get(1).setComposition(composition);
                views.get(1).playAnimation();
              }
            });
      }
    },300);

    this.postDelayed(new Runnable() {
      @Override public void run() {
        LottieComposition.Factory.fromAssetFileName(getContext(), "L.json",
            new OnCompositionLoadedListener() {
              @Override
              public void onCompositionLoaded(LottieComposition composition) {
                views.get(2).setComposition(composition);
                views.get(2).playAnimation();
              }
            });
      }
    },300);

    this.postDelayed(new Runnable() {
      @Override public void run() {
        LottieComposition.Factory.fromAssetFileName(getContext(), "A.json",
            new OnCompositionLoadedListener() {
              @Override
              public void onCompositionLoaded(LottieComposition composition) {
                views.get(3).setComposition(composition);
                views.get(3).playAnimation();
              }
            });
      }
    },300);

    this.postDelayed(new Runnable() {
      @Override public void run() {
        LottieComposition.Factory.fromAssetFileName(getContext(), "S.json",
            new OnCompositionLoadedListener() {
              @Override
              public void onCompositionLoaded(LottieComposition composition) {
                views.get(4).setComposition(composition);
                views.get(4).playAnimation();
              }
            });
      }
    },300);

  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    int currentX = (getWidth()-textSize/2*5)/2;
    int currentY = getPaddingLeft();

    for (int i = 0; i < views.size(); i++) {
      View view = views.get(i);
      view.layout(currentX, currentY, currentX + view.getMeasuredWidth()/2,
          currentY + view.getMeasuredHeight());
          currentX += view.getMeasuredWidth()/2;
    }
  }
}
