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



/**
 * An annotation.
 */
public final class Annotation extends TableOfContents.Section.Item<Annotation> {
    public byte visibility;
    public EncodedValue encodedAnnotation;

    public Annotation(int off, byte visibility, EncodedValue encodedAnnotation) {
        super(off);
        this.visibility = visibility;
        this.encodedAnnotation = encodedAnnotation;
    }

    public EncodedValueReader getReader() {
        return new EncodedValueReader(encodedAnnotation, EncodedValueReader.ENCODED_ANNOTATION);
    }

    public int getTypeIndex() {
        EncodedValueReader reader = getReader();
        reader.readAnnotation();
        return reader.getAnnotationType();
    }

    @Override public int compareTo(Annotation other) {
        return encodedAnnotation.compareTo(other.encodedAnnotation);
    }

    @Override
    public int byteCountInDex() {
        return SizeOf.UBYTE + encodedAnnotation.byteCountInDex();
    }
}
