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

package com.taobao.dex;


import com.taobao.dex.util.ByteInput;
import com.taobao.dex.util.CompareUtils;

/**
 * An encoded value or array.
 */
public final class EncodedValue extends TableOfContents.Section.Item<EncodedValue> {
    public byte[] data;

    public EncodedValue(int off, byte[] data) {
        super(off);
        this.data = data;
    }

    public ByteInput asByteInput() {
        return new ByteInput() {
            private int position = 0;

            @Override
            public byte readByte() {
                return data[position++];
            }
        };
    }

    @Override public int compareTo(EncodedValue other) {
        return CompareUtils.uArrCompare(data, other.data);
    }

    @Override
    public int byteCountInDex() {
        return SizeOf.UBYTE * data.length;
    }
}
