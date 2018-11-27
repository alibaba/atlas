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

package com.example.android.unsplash.data.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.util.Locale;

/**
 * Model class representing data returned from unsplash.it
 */
public class Photo implements Parcelable {

    /*{
        "format": "jpeg",
        "width": 5616,
        "height": 3744,
        "filename": "0000_yC-Yzbqy7PY.jpeg",
        "id": 0,
        "author": "Alejandro Escamilla",
        "author_url": "https://unsplash.com/alejandroescamilla",
        "post_url": "https://unsplash.com/photos/yC-Yzbqy7PY"
    }*/

    public final String format;
    public final int width;
    public final int height;
    public final String filename;
    public final long id;
    public final String author;
    public final String author_url;
    public final String post_url;

    private static final String PHOTO_URL_BASE = "https://unsplash.it/%d?image=%d";

    public Photo(String format,
                 int width,
                 int height,
                 String filename,
                 long id,
                 String author,
                 String author_url,
                 String post_url) {
        this.format = format;
        this.width = width;
        this.height = height;
        this.filename = filename;
        this.id = id;
        this.author = author;
        this.author_url = author_url;
        this.post_url = post_url;
    }

    protected Photo(Parcel in) {
        format = in.readString();
        width = in.readInt();
        height = in.readInt();
        filename = in.readString();
        id = in.readLong();
        author = in.readString();
        author_url = in.readString();
        post_url = in.readString();
    }

    public String getPhotoUrl(int requestWidth) {
        return String.format(Locale.getDefault(), PHOTO_URL_BASE, requestWidth, id);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(format);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeString(filename);
        dest.writeLong(id);
        dest.writeString(author);
        dest.writeString(author_url);
        dest.writeString(post_url);
    }

    public static final Creator<Photo> CREATOR = new Creator<Photo>() {
        @Override
        public Photo createFromParcel(Parcel in) {
            return new Photo(in);
        }

        @Override
        public Photo[] newArray(int size) {
            return new Photo[size];
        }
    };
}
