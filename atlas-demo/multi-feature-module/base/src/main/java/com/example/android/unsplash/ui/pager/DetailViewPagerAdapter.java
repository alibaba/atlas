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

package com.example.android.unsplash.ui.pager;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.android.unsplash.base.R;
import com.example.android.unsplash.data.model.Photo;
import com.example.android.unsplash.ui.DetailSharedElementEnterCallback;
import com.example.android.unsplash.ui.ImageSize;
import com.example.android.unsplash.ui.ThreeTwoImageView;

import java.util.List;

/**
 * Adapter for paging detail views.
 */

public class DetailViewPagerAdapter extends PagerAdapter {

    private final List<Photo> allPhotos;
    private final LayoutInflater layoutInflater;
    private final int photoWidth;
    private final Activity host;
    private DetailSharedElementEnterCallback sharedElementCallback;
    private final String authorTransitionFormat;
    private final String photoTransitionFormat;

    public DetailViewPagerAdapter(@NonNull Activity activity, @NonNull List<Photo> photos,
                                  @NonNull DetailSharedElementEnterCallback callback) {
        layoutInflater = LayoutInflater.from(activity);
        allPhotos = photos;
        photoWidth = activity.getResources().getDisplayMetrics().widthPixels;
        host = activity;
        sharedElementCallback = callback;
        authorTransitionFormat = activity.getResources().getString(R.string.transition_author);
        photoTransitionFormat = activity.getResources().getString(R.string.transition_photo);
    }

    @Override
    public int getCount() {
        return allPhotos.size();
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        View view = layoutInflater.inflate(R.layout.detail_view, container, false);
        ThreeTwoImageView photoview = view.findViewById(R.id.photo);
        TextView authorview = view.findViewById(R.id.author);
        Photo photo = allPhotos.get(position);
        photoview.setTransitionName(String.format(photoTransitionFormat, photo.id));
        authorview.setText(photo.author);
        authorview.setTransitionName(String.format(authorTransitionFormat, photo.id));
        onViewBound(photoview, photo);
        container.addView(view);
        return view;
    }

    private void onViewBound(ImageView imageView, Photo photo) {
        Glide.with(host)
                .load(photo.getPhotoUrl(photoWidth))
                .placeholder(R.color.placeholder)
                .override(ImageSize.NORMAL[0], ImageSize.NORMAL[1])
                .into(imageView);
    }

    @Override
    public void setPrimaryItem(ViewGroup container, int position, Object object) {
        if (object instanceof View) {
            int widthSpec = View.MeasureSpec.makeMeasureSpec(((View) object).getWidth(),
                    View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(((View) object).getHeight(),
                    View.MeasureSpec.EXACTLY);
            ((View) object).measure(widthSpec, heightSpec);
            sharedElementCallback.setView((View) object);
        }
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view.equals(object);
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View) object);
    }
}
