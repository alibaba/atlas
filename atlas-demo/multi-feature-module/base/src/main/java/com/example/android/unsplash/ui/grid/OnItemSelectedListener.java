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

package com.example.android.unsplash.ui.grid;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

public abstract class OnItemSelectedListener implements RecyclerView.OnItemTouchListener {

    private final GestureDetector gestureDetector;

    public OnItemSelectedListener(Context context) {
        gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        return true;
                    }
                });
    }

    public abstract void onItemSelected(RecyclerView.ViewHolder holder, int position);

    @Override
    public final boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        if (gestureDetector.onTouchEvent(e)) {
            View touchedView = rv.findChildViewUnder(e.getX(), e.getY());
            onItemSelected(rv.findContainingViewHolder(touchedView),
                    rv.getChildAdapterPosition(touchedView));
        }
        return false;
    }



    @Override
    public final void onTouchEvent(RecyclerView rv, MotionEvent e) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public final void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
