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
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.android.unsplash.data.model.Photo;
import com.example.android.unsplash.base.R;
import com.example.android.unsplash.ui.ImageSize;

import java.util.ArrayList;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoViewHolder> {

    private final ArrayList<Photo> photos;
    private final int requestedPhotoWidth;
    private final LayoutInflater layoutInflater;
    private final String authorTransitionFormat;
    private final String photoTransitionFormat;

    public PhotoAdapter(@NonNull Context context, @NonNull ArrayList<Photo> photos) {
        this.photos = photos;
        requestedPhotoWidth = context.getResources().getDisplayMetrics().widthPixels;
        authorTransitionFormat = context.getResources().getString(R.string.transition_author);
        photoTransitionFormat = context.getResources().getString(R.string.transition_photo);
        layoutInflater = LayoutInflater.from(context);
    }

    @Override
    public PhotoViewHolder onCreateViewHolder(final ViewGroup parent, int viewType) {
        return new PhotoViewHolder(layoutInflater.inflate(R.layout.photo_item, parent, false));
    }

    @Override
    public void onBindViewHolder(final PhotoViewHolder holder, final int position) {
        Photo data = photos.get(position);
        TextView authorview = holder.itemView.findViewById(R.id.author);
        ImageView photoview = holder.itemView.findViewById(R.id.photo);
        holder.setAuthor(data.author);
        photoview.setTransitionName(String.format(photoTransitionFormat, data.id));
        authorview.setText(data.author);
        authorview.setTransitionName(String.format(authorTransitionFormat, data.id));
        holder.setId(data.id);
        Glide.with(layoutInflater.getContext())
                .load(data.getPhotoUrl(requestedPhotoWidth))
                .placeholder(R.color.placeholder)
                .override(ImageSize.NORMAL[0], ImageSize.NORMAL[1])
                .into((ImageView) holder.itemView.findViewById(R.id.photo));
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    @Override
    public long getItemId(int position) {
        return photos.get(position).id;
    }
}
