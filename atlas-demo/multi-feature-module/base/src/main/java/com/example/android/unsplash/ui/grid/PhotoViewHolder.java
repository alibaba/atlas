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

import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.example.android.unsplash.data.model.Photo;

public class PhotoViewHolder extends RecyclerView.ViewHolder {

    private String author;
    private long id;

    public PhotoViewHolder(View view, Photo photo) {
        super(view);
        author = photo.author;
        id = photo.id;
    }

    public PhotoViewHolder(View view) {
        super(view);
        author = "";
        id = 0;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
