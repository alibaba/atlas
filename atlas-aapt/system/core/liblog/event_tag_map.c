/*
 * Copyright (C) 2007 The Android Open Source Project
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

#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>

#include <log/event_tag_map.h>
#include <log/log.h>

#include "log_portability.h"

#define OUT_TAG "EventTagMap"

/*
 * Single entry.
 */
typedef struct EventTag {
    unsigned int    tagIndex;
    const char*     tagStr;
} EventTag;

/*
 * Map.
 */
struct EventTagMap {
    /* memory-mapped source file; we get strings from here */
    void*           mapAddr;
    size_t          mapLen;

    /* array of event tags, sorted numerically by tag index */
    EventTag*       tagArray;
    int             numTags;
};

/* fwd */
static int processFile(EventTagMap* map);
static int countMapLines(const EventTagMap* map);
static int parseMapLines(EventTagMap* map);
static int scanTagLine(char** pData, EventTag* tag, int lineNum);
static int sortTags(EventTagMap* map);


/*
 * Open the map file and allocate a structure to manage it.
 *
 * We create a private mapping because we want to terminate the log tag
 * strings with '\0'.
 */
LIBLOG_ABI_PUBLIC EventTagMap* android_openEventTagMap(const char* fileName)
{
    EventTagMap* newTagMap;
    off_t end;
    int fd = -1;

    newTagMap = calloc(1, sizeof(EventTagMap));
    if (newTagMap == NULL)
        return NULL;

    fd = open(fileName, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        fprintf(stderr, "%s: unable to open map '%s': %s\n",
            OUT_TAG, fileName, strerror(errno));
        goto fail;
    }

    end = lseek(fd, 0L, SEEK_END);
    (void) lseek(fd, 0L, SEEK_SET);
    if (end < 0) {
        fprintf(stderr, "%s: unable to seek map '%s'\n", OUT_TAG, fileName);
        goto fail;
    }

    newTagMap->mapAddr = mmap(NULL, end, PROT_READ | PROT_WRITE, MAP_PRIVATE,
                                fd, 0);
    if (newTagMap->mapAddr == MAP_FAILED) {
        fprintf(stderr, "%s: mmap(%s) failed: %s\n",
            OUT_TAG, fileName, strerror(errno));
        goto fail;
    }
    newTagMap->mapLen = end;

    if (processFile(newTagMap) != 0)
        goto fail;

    return newTagMap;

fail:
    android_closeEventTagMap(newTagMap);
    if (fd >= 0)
        close(fd);
    return NULL;
}

/*
 * Close the map.
 */
LIBLOG_ABI_PUBLIC void android_closeEventTagMap(EventTagMap* map)
{
    if (map == NULL)
        return;

    munmap(map->mapAddr, map->mapLen);
    free(map);
}

/*
 * Look up an entry in the map.
 *
 * The entries are sorted by tag number, so we can do a binary search.
 */
LIBLOG_ABI_PUBLIC const char* android_lookupEventTag(const EventTagMap* map,
                                                     int tag)
{
    int hi, lo, mid;

    lo = 0;
    hi = map->numTags-1;

    while (lo <= hi) {
        int cmp;

        mid = (lo+hi)/2;
        cmp = map->tagArray[mid].tagIndex - tag;
        if (cmp < 0) {
            /* tag is bigger */
            lo = mid + 1;
        } else if (cmp > 0) {
            /* tag is smaller */
            hi = mid - 1;
        } else {
            /* found */
            return map->tagArray[mid].tagStr;
        }
    }

    return NULL;
}



/*
 * Determine whether "c" is a whitespace char.
 */
static inline int isCharWhitespace(char c)
{
    return (c == ' ' || c == '\n' || c == '\r' || c == '\t');
}

/*
 * Determine whether "c" is a valid tag char.
 */
static inline int isCharValidTag(char c)
{
    return ((c >= 'A' && c <= 'Z') ||
            (c >= 'a' && c <= 'z') ||
            (c >= '0' && c <= '9') ||
            (c == '_'));
}

/*
 * Determine whether "c" is a valid decimal digit.
 */
static inline int isCharDigit(char c)
{
    return (c >= '0' && c <= '9');
}


/*
 * Crunch through the file, parsing the contents and creating a tag index.
 */
static int processFile(EventTagMap* map)
{
    /* get a tag count */
    map->numTags = countMapLines(map);
    if (map->numTags < 0)
        return -1;

    //printf("+++ found %d tags\n", map->numTags);

    /* allocate storage for the tag index array */
    map->tagArray = calloc(1, sizeof(EventTag) * map->numTags);
    if (map->tagArray == NULL)
        return -1;

    /* parse the file, null-terminating tag strings */
    if (parseMapLines(map) != 0) {
        fprintf(stderr, "%s: file parse failed\n", OUT_TAG);
        return -1;
    }

    /* sort the tags and check for duplicates */
    if (sortTags(map) != 0)
        return -1;

    return 0;
}

/*
 * Run through all lines in the file, determining whether they're blank,
 * comments, or possibly have a tag entry.
 *
 * This is a very "loose" scan.  We don't try to detect syntax errors here.
 * The later pass is more careful, but the number of tags found there must
 * match the number of tags found here.
 *
 * Returns the number of potential tag entries found.
 */
static int countMapLines(const EventTagMap* map)
{
    int numTags, unknown;
    const char* cp;
    const char* endp;

    cp = (const char*) map->mapAddr;
    endp = cp + map->mapLen;

    numTags = 0;
    unknown = 1;
    while (cp < endp) {
        if (*cp == '\n') {
            unknown = 1;
        } else if (unknown) {
            if (isCharDigit(*cp)) {
                /* looks like a tag to me */
                numTags++;
                unknown = 0;
            } else if (isCharWhitespace(*cp)) {
                /* might be leading whitespace before tag num, keep going */
            } else {
                /* assume comment; second pass can complain in detail */
                unknown = 0;
            }
        } else {
            /* we've made up our mind; just scan to end of line */
        }
        cp++;
    }

    return numTags;
}

/*
 * Parse the tags out of the file.
 */
static int parseMapLines(EventTagMap* map)
{
    int tagNum, lineStart, lineNum;
    char* cp;
    char* endp;

    cp = (char*) map->mapAddr;
    endp = cp + map->mapLen;

    /* insist on EOL at EOF; simplifies parsing and null-termination */
    if (*(endp-1) != '\n') {
        fprintf(stderr, "%s: map file missing EOL on last line\n", OUT_TAG);
        return -1;
    }

    tagNum = 0;
    lineStart = 1;
    lineNum = 1;
    while (cp < endp) {
        //printf("{%02x}", *cp); fflush(stdout);
        if (*cp == '\n') {
            lineStart = 1;
            lineNum++;
        } else if (lineStart) {
            if (*cp == '#') {
                /* comment; just scan to end */
                lineStart = 0;
            } else if (isCharDigit(*cp)) {
                /* looks like a tag; scan it out */
                if (tagNum >= map->numTags) {
                    fprintf(stderr,
                        "%s: more tags than expected (%d)\n", OUT_TAG, tagNum);
                    return -1;
                }
                if (scanTagLine(&cp, &map->tagArray[tagNum], lineNum) != 0)
                    return -1;
                tagNum++;
                lineNum++;      // we eat the '\n'
                /* leave lineStart==1 */
            } else if (isCharWhitespace(*cp)) {
                /* looks like leading whitespace; keep scanning */
            } else {
                fprintf(stderr,
                    "%s: unexpected chars (0x%02x) in tag number on line %d\n",
                    OUT_TAG, *cp, lineNum);
                return -1;
            }
        } else {
            /* this is a blank or comment line */
        }
        cp++;
    }

    if (tagNum != map->numTags) {
        fprintf(stderr, "%s: parsed %d tags, expected %d\n",
            OUT_TAG, tagNum, map->numTags);
        return -1;
    }

    return 0;
}

/*
 * Scan one tag line.
 *
 * "*pData" should be pointing to the first digit in the tag number.  On
 * successful return, it will be pointing to the last character in the
 * tag line (i.e. the character before the start of the next line).
 *
 * Returns 0 on success, nonzero on failure.
 */
static int scanTagLine(char** pData, EventTag* tag, int lineNum)
{
    char* cp = *pData;
    char* startp;
    char* endp;
    unsigned long val;

    startp = cp;
    while (isCharDigit(*++cp))
        ;
    *cp = '\0';

    val = strtoul(startp, &endp, 10);
    assert(endp == cp);
    if (endp != cp)
        fprintf(stderr, "ARRRRGH\n");

    tag->tagIndex = val;

    while (*++cp != '\n' && isCharWhitespace(*cp))
        ;

    if (*cp == '\n') {
        fprintf(stderr,
            "%s: missing tag string on line %d\n", OUT_TAG, lineNum);
        return -1;
    }

    tag->tagStr = cp;

    while (isCharValidTag(*++cp))
        ;

    if (*cp == '\n') {
        /* null terminate and return */
        *cp = '\0';
    } else if (isCharWhitespace(*cp)) {
        /* CRLF or trailin spaces; zap this char, then scan for the '\n' */
        *cp = '\0';

        /* just ignore the rest of the line till \n
        TODO: read the tag description that follows the tag name
        */
        while (*++cp != '\n') {
        }
    } else {
        fprintf(stderr,
            "%s: invalid tag chars on line %d\n", OUT_TAG, lineNum);
        return -1;
    }

    *pData = cp;

    //printf("+++ Line %d: got %d '%s'\n", lineNum, tag->tagIndex, tag->tagStr);
    return 0;
}

/*
 * Compare two EventTags.
 */
static int compareEventTags(const void* v1, const void* v2)
{
    const EventTag* tag1 = (const EventTag*) v1;
    const EventTag* tag2 = (const EventTag*) v2;

    return tag1->tagIndex - tag2->tagIndex;
}

/*
 * Sort the EventTag array so we can do fast lookups by tag index.  After
 * the sort we do a quick check for duplicate tag indices.
 *
 * Returns 0 on success.
 */
static int sortTags(EventTagMap* map)
{
    int i;

    qsort(map->tagArray, map->numTags, sizeof(EventTag), compareEventTags);

    for (i = 1; i < map->numTags; i++) {
        if (map->tagArray[i].tagIndex == map->tagArray[i-1].tagIndex) {
            fprintf(stderr, "%s: duplicate tag entries (%d:%s and %d:%s)\n",
                OUT_TAG,
                map->tagArray[i].tagIndex, map->tagArray[i].tagStr,
                map->tagArray[i-1].tagIndex, map->tagArray[i-1].tagStr);
            return -1;
        }
    }

    return 0;
}
