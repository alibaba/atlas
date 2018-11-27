/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.unsplash.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.transition.Transition;
import android.transition.TransitionValues;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Transitions a TextView from one font size to another. This does not
 * do any animation of TextView content and if the text changes, this
 * transition will not run.
 * <p>
 * The animation works by capturing a bitmap of the text at the start
 * and end states. It then scales the start bitmap until it reaches
 * a threshold and switches to the scaled end bitmap for the remainder
 * of the animation. This keeps the jump in bitmaps in the middle of
 * the animation, where it is less noticeable than at the beginning
 * or end of the animation. This transition does not work well with
 * cropped text. TextResize also does not work with changes in
 * TextView gravity.
 */
public class TextResize extends Transition {
    private static final String FONT_SIZE = "TextResize:fontSize";
    private static final String DATA = "TextResize:data";

    private static final String[] PROPERTIES = {
            // We only care about FONT_SIZE. If anything else changes, we don't
            // want this transition to be called to create an Animator.
            FONT_SIZE,
    };

    public TextResize() {
        addTarget(TextView.class);
    }

    /**
     * Constructor used from XML.
     */
    public TextResize(Context context, AttributeSet attrs) {
        super(context, attrs);
        addTarget(TextView.class);
    }

    @Override
    public String[] getTransitionProperties() {
        return PROPERTIES;
    }

    @Override
    public void captureStartValues(TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    @Override
    public void captureEndValues(TransitionValues transitionValues) {
        captureValues(transitionValues);
    }

    private void captureValues(TransitionValues transitionValues) {
        if (!(transitionValues.view instanceof TextView)) {
            return;
        }
        final TextView view = (TextView) transitionValues.view;
        final float fontSize = view.getTextSize();
        transitionValues.values.put(FONT_SIZE, fontSize);
        final TextResizeData data = new TextResizeData(view);
        transitionValues.values.put(DATA, data);
    }

    @Override
    public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues,
                                   TransitionValues endValues) {
        if (startValues == null || endValues == null) {
            return null;
        }

        final TextResizeData startData = (TextResizeData) startValues.values.get(DATA);
        final TextResizeData endData = (TextResizeData) endValues.values.get(DATA);
        if (startData.gravity != endData.gravity) {
            return null; // Can't deal with changes in gravity
        }

        final TextView textView = (TextView) endValues.view;
        float startFontSize = (Float) startValues.values.get(FONT_SIZE);
        // Capture the start bitmap -- we need to set the values to the start values first
        setTextViewData(textView, startData, startFontSize);
        final float startWidth = textView.getPaint().measureText(textView.getText().toString());

        final Bitmap startBitmap = captureTextBitmap(textView);

        if (startBitmap == null) {
            startFontSize = 0;
        }

        float endFontSize = (Float) endValues.values.get(FONT_SIZE);

        // Set the values to the end values
        setTextViewData(textView, endData, endFontSize);

        final float endWidth = textView.getPaint().measureText(textView.getText().toString());

        // Capture the end bitmap
        final Bitmap endBitmap = captureTextBitmap(textView);
        if (endBitmap == null) {
            endFontSize = 0;
        }

        if (startFontSize == 0 && endFontSize == 0) {
            return null; // Can't animate null bitmaps
        }

        // Set the colors of the TextView so that nothing is drawn.
        // Only draw the bitmaps in the overlay.
        final ColorStateList textColors = textView.getTextColors();
        final ColorStateList hintColors = textView.getHintTextColors();
        final int highlightColor = textView.getHighlightColor();
        final ColorStateList linkColors = textView.getLinkTextColors();
        textView.setTextColor(Color.TRANSPARENT);
        textView.setHintTextColor(Color.TRANSPARENT);
        textView.setHighlightColor(Color.TRANSPARENT);
        textView.setLinkTextColor(Color.TRANSPARENT);

        // Create the drawable that will be animated in the TextView's overlay.
        // Ensure that it is showing the start state now.
        final SwitchBitmapDrawable drawable = new SwitchBitmapDrawable(textView, startData.gravity,
                startBitmap, startFontSize, startWidth, endBitmap, endFontSize, endWidth);
        textView.getOverlay().add(drawable);

        // Properties: left, top, font size, text color
        final PropertyValuesHolder leftProp =
                PropertyValuesHolder.ofFloat("left", startData.paddingLeft, endData.paddingLeft);
        final PropertyValuesHolder topProp =
                PropertyValuesHolder.ofFloat("top", startData.paddingTop, endData.paddingTop);
        final PropertyValuesHolder rightProp = PropertyValuesHolder.ofFloat("right",
                startData.width - startData.paddingRight, endData.width - endData.paddingRight);
        final PropertyValuesHolder bottomProp = PropertyValuesHolder.ofFloat("bottom",
                startData.height - startData.paddingBottom, endData.height - endData.paddingBottom);
        final PropertyValuesHolder fontSizeProp =
                PropertyValuesHolder.ofFloat("fontSize", startFontSize, endFontSize);
        final ObjectAnimator animator;
        if (startData.textColor != endData.textColor) {
            final PropertyValuesHolder textColorProp = PropertyValuesHolder.ofObject("textColor",
                    new ArgbEvaluator(), startData.textColor, endData.textColor);
            animator = ObjectAnimator.ofPropertyValuesHolder(drawable,
                    leftProp, topProp, rightProp, bottomProp, fontSizeProp, textColorProp);
        } else {
            animator = ObjectAnimator.ofPropertyValuesHolder(drawable,
                    leftProp, topProp, rightProp, bottomProp, fontSizeProp);
        }

        final float finalFontSize = endFontSize;
        AnimatorListenerAdapter listener = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                textView.getOverlay().remove(drawable);
                textView.setTextColor(textColors);
                textView.setHintTextColor(hintColors);
                textView.setHighlightColor(highlightColor);
                textView.setLinkTextColor(linkColors);
            }

            @Override
            public void onAnimationPause(Animator animation) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, drawable.getFontSize());
                final int paddingLeft = Math.round(drawable.getLeft());
                final int paddingTop = Math.round(drawable.getTop());
                final float fraction = animator.getAnimatedFraction();
                final int paddingRight = Math.round(interpolate(startData.paddingRight,
                        endData.paddingRight, fraction));
                final int paddingBottom = Math.round(interpolate(startData.paddingBottom,
                        endData.paddingBottom, fraction));
                textView.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
                textView.setTextColor(drawable.getTextColor());
            }

            @Override
            public void onAnimationResume(Animator animation) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, finalFontSize);
                textView.setPadding(endData.paddingLeft, endData.paddingTop,
                        endData.paddingRight, endData.paddingBottom);
                textView.setTextColor(endData.textColor);
            }
        };
        animator.addListener(listener);
        animator.addPauseListener(listener);
        return animator;
    }

    private static void setTextViewData(TextView view, TextResizeData data, float fontSize) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
        view.setPadding(data.paddingLeft, data.paddingTop, data.paddingRight, data.paddingBottom);
        view.setRight(view.getLeft() + data.width);
        view.setBottom(view.getTop() + data.height);
        view.setTextColor(data.textColor);
        int widthSpec = View.MeasureSpec.makeMeasureSpec(view.getWidth(), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(view.getHeight(), View.MeasureSpec.EXACTLY);
        view.measure(widthSpec, heightSpec);
        view.layout(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
    }

    private static Bitmap captureTextBitmap(TextView textView) {
        Drawable background = textView.getBackground();
        textView.setBackground(null);
        int width = textView.getWidth() - textView.getPaddingLeft() - textView.getPaddingRight();
        int height = textView.getHeight() - textView.getPaddingTop() - textView.getPaddingBottom();
        if (width == 0 || height == 0) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(-textView.getPaddingLeft(), -textView.getPaddingTop());
        textView.draw(canvas);
        textView.setBackground(background);
        return bitmap;
    }

    private static float interpolate(float start, float end, float fraction) {
        return start + (fraction * (end - start));
    }

    /**
     * This Drawable is used to scale the start and end bitmaps and switch between them
     * at the appropriate progress.
     */
    private static class SwitchBitmapDrawable extends Drawable {
        private final TextView view;
        private final int horizontalGravity;
        private final int verticalGravity;
        private final Bitmap startBitmap;
        private final Bitmap endBitmap;
        private final Paint paint = new Paint();
        private final float startFontSize;
        private final float endFontSize;
        private final float startWidth;
        private final float endWidth;
        private float fontSize;
        private float left;
        private float top;
        private float right;
        private float bottom;
        private int textColor;

        public SwitchBitmapDrawable(TextView view, int gravity,
                                    Bitmap startBitmap, float startFontSize, float startWidth,
                                    Bitmap endBitmap, float endFontSize, float endWidth) {
            this.view = view;
            this.horizontalGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
            this.verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;
            this.startBitmap = startBitmap;
            this.endBitmap = endBitmap;
            this.startFontSize = startFontSize;
            this.endFontSize = endFontSize;
            this.startWidth = startWidth;
            this.endWidth = endWidth;
        }

        @Override
        public void invalidateSelf() {
            super.invalidateSelf();
            view.invalidate();
        }

        /**
         * Sets the font size that the text should be displayed at.
         *
         * @param fontSize The font size in pixels of the scaled bitmap text.
         */
        public void setFontSize(float fontSize) {
            this.fontSize = fontSize;
            invalidateSelf();
        }

        /**
         * Sets the color of the text to be displayed.
         *
         * @param textColor The color of the text to be displayed.
         */
        public void setTextColor(int textColor) {
            this.textColor = textColor;
            setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
            invalidateSelf();
        }

        /**
         * Sets the left side of the text. This should be the same as the left padding.
         *
         * @param left The left side of the text in pixels.
         */
        public void setLeft(float left) {
            this.left = left;
            invalidateSelf();
        }

        /**
         * Sets the top of the text. This should be the same as the top padding.
         *
         * @param top The top of the text in pixels.
         */
        public void setTop(float top) {
            this.top = top;
            invalidateSelf();
        }

        /**
         * Sets the right of the drawable.
         *
         * @param right The right pixel of the drawn area.
         */
        public void setRight(float right) {
            this.right = right;
            invalidateSelf();
        }

        /**
         * Sets the bottom of the drawable.
         *
         * @param bottom The bottom pixel of the drawn area.
         */
        public void setBottom(float bottom) {
            this.bottom = bottom;
            invalidateSelf();
        }

        /**
         * @return The left side of the text.
         */
        public float getLeft() {
            return left;
        }

        /**
         * @return The top of the text.
         */
        public float getTop() {
            return top;
        }

        /**
         * @return The right side of the text.
         */
        public float getRight() {
            return right;
        }

        /**
         * @return The bottom of the text.
         */
        public float getBottom() {
            return bottom;
        }

        /**
         * @return The font size of the text in the displayed bitmap.
         */
        public float getFontSize() {
            return fontSize;
        }

        /**
         * @return The color of the text being displayed.
         */
        public int getTextColor() {
            return textColor;
        }

        @Override
        public void draw(Canvas canvas) {
            int saveCount = canvas.save();
            // The threshold changes depending on the target font sizes. Because scaled-up
            // fonts look bad, we want to switch when closer to the smaller font size. This
            // algorithm ensures that null bitmaps (font size = 0) are never used.
            final float threshold = startFontSize / (startFontSize + endFontSize);
            final float fontSize = getFontSize();
            final float progress = (fontSize - startFontSize)/(endFontSize - startFontSize);

            // The drawn text width is a more accurate scale than font size. This avoids
            // jump when switching bitmaps.
            final float expectedWidth = interpolate(startWidth, endWidth, progress);
            if (progress < threshold) {
                // draw start bitmap
                final float scale = expectedWidth / startWidth;
                float tx = getTranslationPoint(horizontalGravity, left, right,
                        startBitmap.getWidth(), scale);
                float ty = getTranslationPoint(verticalGravity, top, bottom,
                        startBitmap.getHeight(), scale);
                canvas.translate(tx, ty);
                canvas.scale(scale, scale);
                canvas.drawBitmap(startBitmap, 0, 0, paint);
            } else {
                // draw end bitmap
                final float scale = expectedWidth / endWidth;
                float tx = getTranslationPoint(horizontalGravity, left, right,
                        endBitmap.getWidth(), scale);
                float ty = getTranslationPoint(verticalGravity, top, bottom,
                        endBitmap.getHeight(), scale);
                canvas.translate(tx, ty);
                canvas.scale(scale, scale);
                canvas.drawBitmap(endBitmap, 0, 0, paint);
            }
            canvas.restoreToCount(saveCount);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        private float getTranslationPoint(int gravity, float start, float end, float dim,
                                          float scale) {
            switch (gravity) {
                case Gravity.CENTER_HORIZONTAL:
                case Gravity.CENTER_VERTICAL:
                    return ((start + end) - (dim * scale))/2f;
                case Gravity.RIGHT:
                case Gravity.BOTTOM:
                    return end - (dim * scale);
                case Gravity.LEFT:
                case Gravity.TOP:
                default:
                    return start;
            }
        }
    }

    /**
     * Contains all the non-font-size data used by the TextResize transition.
     * None of these values should trigger the transition, so they are not listed
     * in PROPERTIES. These are captured together to avoid boxing of all the
     * primitives while adding to TransitionValues.
     */
    static class TextResizeData {
        public final int paddingLeft;
        public final int paddingTop;
        public final int paddingRight;
        public final int paddingBottom;
        public final int width;
        public final int height;
        public final int gravity;
        public final int textColor;

        public TextResizeData(TextView textView) {
            this.paddingLeft = textView.getPaddingLeft();
            this.paddingTop = textView.getPaddingTop();
            this.paddingRight = textView.getPaddingRight();
            this.paddingBottom = textView.getPaddingBottom();
            this.width = textView.getWidth();
            this.height = textView.getHeight();
            this.gravity = textView.getGravity();
            this.textColor = textView.getCurrentTextColor();
        }
    }
}
