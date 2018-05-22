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

package com.example.android.unsplash.data;

import android.util.Log;

import com.example.android.unsplash.data.model.Photo;

import java.util.ArrayList;
import java.util.List;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Only gets photos once per runtime.
 */
public class PhotoService {

    public interface PhotoCallback {
        void success(ArrayList<Photo> photos);
        void error();
    }

    private static final int PHOTO_COUNT = 12;
    private static final String TAG = "PhotoService";
    private static ArrayList<Photo> mPhotos;
    private static PhotoService sPhotoService;

    public static PhotoService getInstance() {
        if (sPhotoService == null) {
            sPhotoService = new PhotoService();
        }
        return sPhotoService;
    }

    public void getPhotosAsync(final PhotoCallback callback) {
        if (mPhotos == null) {
            new RestAdapter.Builder()
                    .setEndpoint(UnsplashService.ENDPOINT)
                    .build()
                    .create(UnsplashService.class).getFeed(new Callback<List<Photo>>() {
                @Override
                public void success(List<Photo> photos, Response response) {
                    // the first items not interesting to us, get the last <n>
                    mPhotos = new ArrayList<>(photos.subList(photos.size() - PHOTO_COUNT,
                            photos.size()));
                    callback.success(mPhotos);
                }

                @Override
                public void failure(RetrofitError error) {
                    callback.error();
                    Log.e(TAG, "Could not load photos, " + error);
                }
            });
        } else {
            callback.success(mPhotos);
        }
    }
}
