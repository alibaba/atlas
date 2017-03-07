/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef ANDROID_UTILS_LRU_CACHE_H
#define ANDROID_UTILS_LRU_CACHE_H

#include <memory>
#include <unordered_set>

#include "utils/TypeHelpers.h"  // hash_t

namespace android {

/**
 * GenerationCache callback used when an item is removed
 */
template<typename EntryKey, typename EntryValue>
class OnEntryRemoved {
public:
    virtual ~OnEntryRemoved() { };
    virtual void operator()(EntryKey& key, EntryValue& value) = 0;
}; // class OnEntryRemoved

template <typename TKey, typename TValue>
class LruCache {
public:
    explicit LruCache(uint32_t maxCapacity);
    virtual ~LruCache();

    enum Capacity {
        kUnlimitedCapacity,
    };

    void setOnEntryRemovedListener(OnEntryRemoved<TKey, TValue>* listener);
    size_t size() const;
    const TValue& get(const TKey& key);
    bool put(const TKey& key, const TValue& value);
    bool remove(const TKey& key);
    bool removeOldest();
    void clear();
    const TValue& peekOldestValue();

private:
    LruCache(const LruCache& that);  // disallow copy constructor

    struct Entry {
        TKey key;
        TValue value;
        Entry* parent;
        Entry* child;

        Entry(TKey key_, TValue value_) : key(key_), value(value_), parent(NULL), child(NULL) {
        }
        const TKey& getKey() const { return key; }
    };

    struct HashForEntry : public std::unary_function<Entry*, hash_t> {
        size_t operator() (const Entry* entry) const {
            return hash_type(entry->key);
        };
    };

    struct EqualityForHashedEntries : public std::unary_function<Entry*, hash_t> {
        bool operator() (const Entry* lhs, const Entry* rhs) const {
            return lhs->key == rhs->key;
        };
    };

    typedef std::unordered_set<Entry*, HashForEntry, EqualityForHashedEntries> LruCacheSet;

    void attachToCache(Entry& entry);
    void detachFromCache(Entry& entry);

    typename LruCacheSet::iterator findByKey(const TKey& key) {
        Entry entryForSearch(key, mNullValue);
        typename LruCacheSet::iterator result = mSet->find(&entryForSearch);
        return result;
    }

    std::unique_ptr<LruCacheSet> mSet;
    OnEntryRemoved<TKey, TValue>* mListener;
    Entry* mOldest;
    Entry* mYoungest;
    uint32_t mMaxCapacity;
    TValue mNullValue;

public:
    // To be used like:
    // while (it.next()) {
    //   it.value(); it.key();
    // }
    class Iterator {
    public:
        Iterator(const LruCache<TKey, TValue>& cache):
                mCache(cache), mIterator(mCache.mSet->begin()), mBeginReturned(false) {
        }

        bool next() {
            if (mIterator == mCache.mSet->end()) {
                return false;
            }
            if (!mBeginReturned) {
                // mIterator has been initialized to the beginning and
                // hasn't been returned. Do not advance:
                mBeginReturned = true;
            } else {
                std::advance(mIterator, 1);
            }
            bool ret = (mIterator != mCache.mSet->end());
            return ret;
        }

        const TValue& value() const {
            return (*mIterator)->value;
        }

        const TKey& key() const {
            return (*mIterator)->key;
        }
    private:
        const LruCache<TKey, TValue>& mCache;
        typename LruCacheSet::iterator mIterator;
        bool mBeginReturned;
    };
};

// Implementation is here, because it's fully templated
template <typename TKey, typename TValue>
LruCache<TKey, TValue>::LruCache(uint32_t maxCapacity)
    : mSet(new LruCacheSet())
    , mListener(NULL)
    , mOldest(NULL)
    , mYoungest(NULL)
    , mMaxCapacity(maxCapacity)
    , mNullValue(NULL) {
    mSet->max_load_factor(1.0);
};

template <typename TKey, typename TValue>
LruCache<TKey, TValue>::~LruCache() {
    // Need to delete created entries.
    clear();
};

template<typename K, typename V>
void LruCache<K, V>::setOnEntryRemovedListener(OnEntryRemoved<K, V>* listener) {
    mListener = listener;
}

template <typename TKey, typename TValue>
size_t LruCache<TKey, TValue>::size() const {
    return mSet->size();
}

template <typename TKey, typename TValue>
const TValue& LruCache<TKey, TValue>::get(const TKey& key) {
    typename LruCacheSet::const_iterator find_result = findByKey(key);
    if (find_result == mSet->end()) {
        return mNullValue;
    }
    Entry *entry = *find_result;
    detachFromCache(*entry);
    attachToCache(*entry);
    return entry->value;
}

template <typename TKey, typename TValue>
bool LruCache<TKey, TValue>::put(const TKey& key, const TValue& value) {
    if (mMaxCapacity != kUnlimitedCapacity && size() >= mMaxCapacity) {
        removeOldest();
    }

    if (findByKey(key) != mSet->end()) {
        return false;
    }

    Entry* newEntry = new Entry(key, value);
    mSet->insert(newEntry);
    attachToCache(*newEntry);
    return true;
}

template <typename TKey, typename TValue>
bool LruCache<TKey, TValue>::remove(const TKey& key) {
    typename LruCacheSet::const_iterator find_result = findByKey(key);
    if (find_result == mSet->end()) {
        return false;
    }
    Entry* entry = *find_result;
    mSet->erase(entry);
    if (mListener) {
        (*mListener)(entry->key, entry->value);
    }
    detachFromCache(*entry);
    delete entry;
    return true;
}

template <typename TKey, typename TValue>
bool LruCache<TKey, TValue>::removeOldest() {
    if (mOldest != NULL) {
        return remove(mOldest->key);
        // TODO: should probably abort if false
    }
    return false;
}

template <typename TKey, typename TValue>
const TValue& LruCache<TKey, TValue>::peekOldestValue() {
    if (mOldest) {
        return mOldest->value;
    }
    return mNullValue;
}

template <typename TKey, typename TValue>
void LruCache<TKey, TValue>::clear() {
    if (mListener) {
        for (Entry* p = mOldest; p != NULL; p = p->child) {
            (*mListener)(p->key, p->value);
        }
    }
    mYoungest = NULL;
    mOldest = NULL;
    for (auto entry : *mSet.get()) {
        delete entry;
    }
    mSet->clear();
}

template <typename TKey, typename TValue>
void LruCache<TKey, TValue>::attachToCache(Entry& entry) {
    if (mYoungest == NULL) {
        mYoungest = mOldest = &entry;
    } else {
        entry.parent = mYoungest;
        mYoungest->child = &entry;
        mYoungest = &entry;
    }
}

template <typename TKey, typename TValue>
void LruCache<TKey, TValue>::detachFromCache(Entry& entry) {
    if (entry.parent != NULL) {
        entry.parent->child = entry.child;
    } else {
        mOldest = entry.child;
    }
    if (entry.child != NULL) {
        entry.child->parent = entry.parent;
    } else {
        mYoungest = entry.parent;
    }

    entry.parent = NULL;
    entry.child = NULL;
}

}
#endif // ANDROID_UTILS_LRU_CACHE_H
