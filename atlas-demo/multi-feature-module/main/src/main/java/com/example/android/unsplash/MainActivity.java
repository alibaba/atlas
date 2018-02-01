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

package com.example.android.unsplash;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.transition.Transition;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.android.unsplash.data.PhotoService;
import com.example.android.unsplash.data.model.Photo;
import com.example.android.unsplash.feature.main.R;
import com.example.android.unsplash.ui.DetailSharedElementEnterCallback;
import com.example.android.unsplash.ui.TransitionCallback;
import com.example.android.unsplash.ui.grid.GridMarginDecoration;
import com.example.android.unsplash.ui.grid.OnItemSelectedListener;
import com.example.android.unsplash.ui.grid.PhotoAdapter;
import com.example.android.unsplash.ui.grid.PhotoViewHolder;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private final Transition.TransitionListener sharedExitListener =
            new TransitionCallback() {
                @Override
                public void onTransitionEnd(Transition transition) {
                    setExitSharedElementCallback(null);
                }
            };

    private RecyclerView grid;
    private ProgressBar empty;
    private ArrayList<Photo> relevantPhotos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        postponeEnterTransition();
        // Listener to reset shared element exit transition callbacks.
        getWindow().getSharedElementExitTransition().addListener(sharedExitListener);

        grid = (RecyclerView) findViewById(R.id.image_grid);
        empty = (ProgressBar) findViewById(android.R.id.empty);

        setupRecyclerView();

        if (savedInstanceState != null) {
            relevantPhotos = savedInstanceState.getParcelableArrayList(IntentUtil.INSTANCE.getRELEVANT_PHOTOS());
        }
        displayData();
    }

    private void displayData() {
        if (relevantPhotos != null) {
            populateGrid();
        } else {
            PhotoService.getInstance().getPhotosAsync(new PhotoService.PhotoCallback() {
                @Override
                public void success(ArrayList<Photo> photos) {
                    relevantPhotos = photos;
                    populateGrid();
                }

                @Override
                public void error() {
                    // no-op
                }
            });
        }
    }

    private void populateGrid() {
        grid.setAdapter(new PhotoAdapter(this, relevantPhotos));
        grid.addOnItemTouchListener(new OnItemSelectedListener(MainActivity.this) {
            public void onItemSelected(RecyclerView.ViewHolder holder, int position) {
                if (!(holder instanceof PhotoViewHolder)) {
                    return;
                }
                MainActivity activity = MainActivity.this;
                PhotoViewHolder pvh = (PhotoViewHolder) holder;
                final Intent intent = getDetailActivityStartIntent(
                        activity, position, pvh);
                final ActivityOptions activityOptions = getActivityOptions(pvh);

                activity.startActivityForResult(
                        intent, IntentUtil.INSTANCE.getREQUEST_CODE(), activityOptions.toBundle());
            }
        });
        empty.setVisibility(View.GONE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList(IntentUtil.INSTANCE.getRELEVANT_PHOTOS(), relevantPhotos);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        postponeEnterTransition();
        // Start the postponed transition when the recycler view is ready to be drawn.
        grid.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                grid.getViewTreeObserver().removeOnPreDrawListener(this);
                startPostponedEnterTransition();
                return true;
            }
        });

        if (data == null) {
            return;
        }

        final int selectedItem = data.getIntExtra(IntentUtil.INSTANCE.getSELECTED_ITEM_POSITION(), 0);
        grid.scrollToPosition(selectedItem);

        PhotoViewHolder holder = (PhotoViewHolder) grid.
                findViewHolderForAdapterPosition(selectedItem);
        if (holder == null) {
            Log.w(TAG, "onActivityReenter: Holder is null, remapping cancelled.");
            return;
        }

        DetailSharedElementEnterCallback callback =
                new DetailSharedElementEnterCallback(getIntent());
        callback.setView(holder.itemView);
        setExitSharedElementCallback(callback);

    }

    private void setupRecyclerView() {
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 3);
        grid.setLayoutManager(gridLayoutManager);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                /* emulating https://material-design.storage.googleapis.com/publish/material_v_4/material_ext_publish/0B6Okdz75tqQsck9lUkgxNVZza1U/style_imagery_integration_scale1.png */
                switch (position % 6) {
                    case 5:
                        return 3;
                    case 3:
                        return 2;
                    default:
                        return 1;
                }
            }
        });
        grid.addItemDecoration(new GridMarginDecoration(
                getResources().getDimensionPixelSize(
                        com.example.android.unsplash.base.R.dimen.grid_item_spacing)));
        grid.setHasFixedSize(true);

    }

    @NonNull
    private static Intent getDetailActivityStartIntent(Context context,
                                                       int position,
                                                       PhotoViewHolder holder) {
        final Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://multi-feature.instantappsample.com/detail/" + position));
        intent.setPackage(context.getPackageName());
        intent.addCategory(Intent.CATEGORY_BROWSABLE);

        TextView author =
                holder.itemView.findViewById(com.example.android.unsplash.base.R.id.author);

        // Working around unboxing issues with multiple dex files on platforms prior to N.
        intent.putExtra(IntentUtil.INSTANCE.getSELECTED_ITEM_POSITION(), position);
        intent.putExtra(IntentUtil.INSTANCE.getFONT_SIZE(), author.getTextSize());
        intent.putExtra(IntentUtil.INSTANCE.getPADDING(),
                new Rect(author.getPaddingLeft(),
                        author.getPaddingTop(),
                        author.getPaddingRight(),
                        author.getPaddingBottom()));
        intent.putExtra(IntentUtil.INSTANCE.getTEXT_COLOR(), author.getCurrentTextColor());
        return intent;
    }

    private ActivityOptions getActivityOptions(PhotoViewHolder holder) {
        TextView author =
                holder.itemView.findViewById(com.example.android.unsplash.base.R.id.author);
        ImageView photo =
                holder.itemView.findViewById(com.example.android.unsplash.base.R.id.photo);
        Pair authorPair = Pair.create(author, author.getTransitionName());
        Pair photoPair = Pair.create(photo, photo.getTransitionName());
        View decorView = getWindow().getDecorView();
        View statusBackground = decorView.findViewById(android.R.id.statusBarBackground);
        View navBackground = decorView.findViewById(android.R.id.navigationBarBackground);
        Pair statusPair = Pair.create(statusBackground,
                statusBackground.getTransitionName());

        final ActivityOptions options;
        if (navBackground == null) {
            options = ActivityOptions.makeSceneTransitionAnimation(this,
                    authorPair, photoPair, statusPair);
        } else {
            Pair navPair = Pair.create(navBackground, navBackground.getTransitionName());
            options = ActivityOptions.makeSceneTransitionAnimation(this,
                    authorPair, photoPair, statusPair, navPair);
        }
        return options;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
}
