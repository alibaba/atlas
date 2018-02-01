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

package com.example.android.unsplash.ui;

import android.app.SharedElementCallback;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.support.annotation.NonNull;
import android.support.v7.view.menu.MenuView;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.android.unsplash.base.R;
import com.example.android.unsplash.IntentUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DetailSharedElementEnterCallback extends SharedElementCallback {

    private final Intent intent;
    private float targetTextSize;
    private ColorStateList targetTextColors;
    private View currentView;
    private Rect targetPadding;

    public DetailSharedElementEnterCallback(Intent intent) {
        this.intent = intent;
    }

    @Override
    public void onSharedElementStart(List<String> sharedElementNames,
                                     List<View> sharedElements,
                                     List<View> sharedElementSnapshots) {
        TextView author = getAuthor();
        targetTextSize = author.getTextSize();
        targetTextColors = author.getTextColors();
        targetPadding = new Rect(author.getPaddingLeft(),
                author.getPaddingTop(),
                author.getPaddingRight(),
                author.getPaddingBottom());
        if (IntentUtil.INSTANCE.hasAll(intent,
                IntentUtil.INSTANCE.getTEXT_COLOR(), IntentUtil.INSTANCE.getFONT_SIZE(), IntentUtil.INSTANCE.getPADDING())) {
            author.setTextColor(intent.getIntExtra(IntentUtil.INSTANCE.getTEXT_COLOR(), Color.BLACK));
            float textSize = intent.getFloatExtra(IntentUtil.INSTANCE.getFONT_SIZE(), targetTextSize);
            author.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
            Rect padding = intent.getParcelableExtra(IntentUtil.INSTANCE.getPADDING());
            author.setPadding(padding.left, padding.top, padding.right, padding.bottom);
        }
    }

    @Override
    public void onSharedElementEnd(List<String> sharedElementNames,
                                   List<View> sharedElements,
                                   List<View> sharedElementSnapshots) {
        TextView author = getAuthor();
        author.setTextSize(TypedValue.COMPLEX_UNIT_PX, targetTextSize);
        if (targetTextColors != null) {
            author.setTextColor(targetTextColors);
        }
        if (targetPadding != null) {
            author.setPadding(targetPadding.left, targetPadding.top,
                    targetPadding.right, targetPadding.bottom);
        }
        if (currentView != null) {
            View descView = currentView.findViewById(R.id.description);
            if (descView != null) {
                forceSharedElementLayout(descView);
            }
        }
    }

    @Override
    public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
        removeObsoleteElements(names, sharedElements, mapObsoleteElements(names));
        mapSharedElement(names, sharedElements, getAuthor());
        mapSharedElement(names, sharedElements, getPhoto());
    }

    public void setView(@NonNull View view) {
        currentView = view;
    }

    private TextView getAuthor() {
        if (currentView != null) {
            return currentView.findViewById(R.id.author);
        } else {
            throw new NullPointerException("Must set a binding before transitioning.");
        }
    }

    private ImageView getPhoto() {
        if (currentView != null) {
            return currentView.findViewById(R.id.photo);
        } else {
            throw new NullPointerException("Must set a binding before transitioning.");
        }
    }

    /**
     * Maps all views that don't start with "android" namespace.
     *
     * @param names All shared element names.
     * @return The obsolete shared element names.
     */
    @NonNull
    private List<String> mapObsoleteElements(List<String> names) {
        List<String> elementsToRemove = new ArrayList<>(names.size());
        for (String name : names) {
            if (name.startsWith("android")) continue;
            elementsToRemove.add(name);
        }
        return elementsToRemove;
    }

    /**
     * Removes obsolete elements from names and shared elements.
     *
     * @param names Shared element names.
     * @param sharedElements Shared elements.
     * @param elementsToRemove The elements that should be removed.
     */
    private void removeObsoleteElements(List<String> names,
                                        Map<String, View> sharedElements,
                                        List<String> elementsToRemove) {
        if (elementsToRemove.size() > 0) {
            names.removeAll(elementsToRemove);
            for (String elementToRemove : elementsToRemove) {
                sharedElements.remove(elementToRemove);
            }
        }
    }

    /**
     * Puts a shared element to transitions and names.
     *
     * @param names The names for this transition.
     * @param sharedElements The elements for this transition.
     * @param view The view to add.
     */
    private void mapSharedElement(List<String> names, Map<String, View> sharedElements, View view) {
        String transitionName = view.getTransitionName();
        names.add(transitionName);
        sharedElements.put(transitionName, view);
    }

    private void forceSharedElementLayout(View view) {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(view.getWidth(),
                View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(view.getHeight(),
                View.MeasureSpec.EXACTLY);
        view.measure(widthSpec, heightSpec);
        view.layout(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
    }

}