/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Transfer a large list of Parcelable objects across an IPC.  Splits into
 * multiple transactions if needed.
 *
 * Caveat: for efficiency and security, all elements must be the same concrete type.
 * In order to avoid writing the class name of each object, we must ensure that
 * each object is the same type, or else unparceling then reparceling the data may yield
 * a different result if the class name encoded in the Parcelable is a Base type.
 * See b/17671747.
 *
 * @hide
 */
public class ParceledListSlice<T extends Parcelable> {
    private static String TAG = "ParceledListSlice";
    private static boolean DEBUG = false;

    /*
     * TODO get this number from somewhere else. For now set it to a quarter of
     * the 1MB limit.
     */


    public static <T extends Parcelable> ParceledListSlice<T> emptyList() {
        return new ParceledListSlice<T>(Collections.<T> emptyList());
    }

    public ParceledListSlice(List<T> list) {
    }

    @SuppressWarnings("unchecked")
    private ParceledListSlice(Parcel p, ClassLoader loader) {

    }

    private static void verifySameType(final Class<?> expected, final Class<?> actual) {
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException("Can't unparcel type "
                    + actual.getName() + " in list of type "
                    + expected.getName());
        }
    }

    public List<T> getList() {
        return null;
    }




}
