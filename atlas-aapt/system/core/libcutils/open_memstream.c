/*
 * Copyright (C) 2010 The Android Open Source Project
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

#if defined(__APPLE__)

/*
 * Implementation of the POSIX open_memstream() function, which Linux has
 * but BSD lacks.
 *
 * Summary:
 * - Works like a file-backed FILE* opened with fopen(name, "w"), but the
 *   backing is a chunk of memory rather than a file.
 * - The buffer expands as you write more data.  Seeking past the end
 *   of the file and then writing to it zero-fills the gap.
 * - The values at "*bufp" and "*sizep" should be considered read-only,
 *   and are only valid immediately after an fflush() or fclose().
 * - A '\0' is maintained just past the end of the file. This is not included
 *   in "*sizep".  (The behavior w.r.t. fseek() is not clearly defined.
 *   The spec says the null byte is written when a write() advances EOF,
 *   but it looks like glibc ensures the null byte is always found at EOF,
 *   even if you just seeked backwards.  The example on the opengroup.org
 *   page suggests that this is the expected behavior.  The null must be
 *   present after a no-op fflush(), which we can't see, so we have to save
 *   and restore it.  Annoying, but allows file truncation.)
 * - After fclose(), the caller must eventually free(*bufp).
 *
 * This is built out of funopen(), which BSD has but Linux lacks.  There is
 * no flush() operator, so we need to keep the user pointers up to date
 * after each operation.
 *
 * I don't think Windows has any of the above, but we don't need to use
 * them there, so we just supply a stub.
 */
#include <cutils/open_memstream.h>
#include <stdlib.h>
#include <sys/types.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <assert.h>

#if 0
# define DBUG(x) printf x
#else
# define DBUG(x) ((void)0)
#endif

/*
 * Definition of a seekable, write-only memory stream.
 */
typedef struct {
    char**      bufp;       /* pointer to buffer pointer */
    size_t*     sizep;      /* pointer to eof */

    size_t      allocSize;  /* size of buffer */
    size_t      eof;        /* furthest point we've written to */
    size_t      offset;     /* current write offset */
    char        saved;      /* required by NUL handling */
} MemStream;

#define kInitialSize    1024

/*
 * Ensure that we have enough storage to write "size" bytes at the
 * current offset.  We also have to take into account the extra '\0'
 * that we maintain just past EOF.
 *
 * Returns 0 on success.
 */
static int ensureCapacity(MemStream* stream, int writeSize)
{
    DBUG(("+++ ensureCap off=%d size=%d\n", stream->offset, writeSize));

    size_t neededSize = stream->offset + writeSize + 1;
    if (neededSize <= stream->allocSize)
        return 0;

    size_t newSize;

    if (stream->allocSize == 0) {
        newSize = kInitialSize;
    } else {
        newSize = stream->allocSize;
        newSize += newSize / 2;             /* expand by 3/2 */
    }

    if (newSize < neededSize)
        newSize = neededSize;
    DBUG(("+++ realloc %p->%p to size=%d\n",
        stream->bufp, *stream->bufp, newSize));
    char* newBuf = (char*) realloc(*stream->bufp, newSize);
    if (newBuf == NULL)
        return -1;

    *stream->bufp = newBuf;
    stream->allocSize = newSize;
    return 0;
}

/*
 * Write data to a memstream, expanding the buffer if necessary.
 *
 * If we previously seeked beyond EOF, zero-fill the gap.
 *
 * Returns the number of bytes written.
 */
static int write_memstream(void* cookie, const char* buf, int size)
{
    MemStream* stream = (MemStream*) cookie;

    if (ensureCapacity(stream, size) < 0)
        return -1;

    /* seeked past EOF earlier? */
    if (stream->eof < stream->offset) {
        DBUG(("+++ zero-fill gap from %d to %d\n",
            stream->eof, stream->offset-1));
        memset(*stream->bufp + stream->eof, '\0',
            stream->offset - stream->eof);
    }

    /* copy data, advance write pointer */
    memcpy(*stream->bufp + stream->offset, buf, size);
    stream->offset += size;

    if (stream->offset > stream->eof) {
        /* EOF has advanced, update it and append null byte */
        DBUG(("+++ EOF advanced to %d, appending nul\n", stream->offset));
        assert(stream->offset < stream->allocSize);
        stream->eof = stream->offset;
    } else {
        /* within previously-written area; save char we're about to stomp */
        DBUG(("+++ within written area, saving '%c' at %d\n",
            *(*stream->bufp + stream->offset), stream->offset));
        stream->saved = *(*stream->bufp + stream->offset);
    }
    *(*stream->bufp + stream->offset) = '\0';
    *stream->sizep = stream->offset;

    return size;
}

/*
 * Seek within a memstream.
 *
 * Returns the new offset, or -1 on failure.
 */
static fpos_t seek_memstream(void* cookie, fpos_t offset, int whence)
{
    MemStream* stream = (MemStream*) cookie;
    off_t newPosn = (off_t) offset;

    if (whence == SEEK_CUR) {
        newPosn += stream->offset;
    } else if (whence == SEEK_END) {
        newPosn += stream->eof;
    }

    if (newPosn < 0 || ((fpos_t)((size_t) newPosn)) != newPosn) {
        /* bad offset - negative or huge */
        DBUG(("+++ bogus seek offset %ld\n", (long) newPosn));
        errno = EINVAL;
        return (fpos_t) -1;
    }

    if (stream->offset < stream->eof) {
        /*
         * We were pointing to an area we'd already written to, which means
         * we stomped on a character and must now restore it.
         */
        DBUG(("+++ restoring char '%c' at %d\n",
            stream->saved, stream->offset));
        *(*stream->bufp + stream->offset) = stream->saved;
    }

    stream->offset = (size_t) newPosn;

    if (stream->offset < stream->eof) {
        /*
         * We're seeked backward into the stream.  Preserve the character
         * at EOF and stomp it with a NUL.
         */
        stream->saved = *(*stream->bufp + stream->offset);
        *(*stream->bufp + stream->offset) = '\0';
        *stream->sizep = stream->offset;
    } else {
        /*
         * We're positioned at, or possibly beyond, the EOF.  We want to
         * publish the current EOF, not the current position.
         */
        *stream->sizep = stream->eof;
    }

    return newPosn;
}

/*
 * Close the memstream.  We free everything but the data buffer.
 */
static int close_memstream(void* cookie)
{
    free(cookie);
    return 0;
}

/*
 * Prepare a memstream.
 */
FILE* open_memstream(char** bufp, size_t* sizep)
{
    FILE* fp;
    MemStream* stream;

    if (bufp == NULL || sizep == NULL) {
        errno = EINVAL;
        return NULL;
    }

    stream = (MemStream*) calloc(1, sizeof(MemStream));
    if (stream == NULL)
        return NULL;

    fp = funopen(stream,
        NULL, write_memstream, seek_memstream, close_memstream);
    if (fp == NULL) {
        free(stream);
        return NULL;
    }

    *sizep = 0;
    *bufp = NULL;
    stream->bufp = bufp;
    stream->sizep = sizep;

    return fp;
}




#if 0
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/*
 * Simple regression test.
 *
 * To test on desktop Linux with valgrind, it's possible to make a simple
 * change to open_memstream() to use fopencookie instead:
 *
 *  cookie_io_functions_t iofuncs =
 *      { NULL, write_memstream, seek_memstream, close_memstream };
 *  fp = fopencookie(stream, "w", iofuncs);
 *
 * (Some tweaks to seek_memstream are also required, as that takes a
 * pointer to an offset rather than an offset, and returns 0 or -1.)
 */
int testMemStream(void)
{
    FILE *stream;
    char *buf;
    size_t len;
    off_t eob;

    printf("Test1\n");

    /* std example */
    stream = open_memstream(&buf, &len);
    fprintf(stream, "hello my world");
    fflush(stream);
    printf("buf=%s, len=%zu\n", buf, len);
    eob = ftello(stream);
    fseeko(stream, 0, SEEK_SET);
    fprintf(stream, "good-bye");
    fseeko(stream, eob, SEEK_SET);
    fclose(stream);
    printf("buf=%s, len=%zu\n", buf, len);
    free(buf);

    printf("Test2\n");

    /* std example without final seek-to-end */
    stream = open_memstream(&buf, &len);
    fprintf(stream, "hello my world");
    fflush(stream);
    printf("buf=%s, len=%zu\n", buf, len);
    eob = ftello(stream);
    fseeko(stream, 0, SEEK_SET);
    fprintf(stream, "good-bye");
    //fseeko(stream, eob, SEEK_SET);
    fclose(stream);
    printf("buf=%s, len=%zu\n", buf, len);
    free(buf);

    printf("Test3\n");

    /* fancy example; should expand buffer with writes */
    static const int kCmpLen = 1024 + 128;
    char* cmp = malloc(kCmpLen);
    memset(cmp, 0, 1024);
    memset(cmp+1024, 0xff, kCmpLen-1024);
    sprintf(cmp, "This-is-a-tes1234");
    sprintf(cmp + 1022, "abcdef");

    stream = open_memstream (&buf, &len);
    setvbuf(stream, NULL, _IONBF, 0);   /* note: crashes in glibc with this */
    fprintf(stream, "This-is-a-test");
    fseek(stream, -1, SEEK_CUR);    /* broken in glibc; can use {13,SEEK_SET} */
    fprintf(stream, "1234");
    fseek(stream, 1022, SEEK_SET);
    fputc('a', stream);
    fputc('b', stream);
    fputc('c', stream);
    fputc('d', stream);
    fputc('e', stream);
    fputc('f', stream);
    fflush(stream);

    if (memcmp(buf, cmp, len+1) != 0) {
        printf("mismatch\n");
    } else {
        printf("match\n");
    }

    printf("Test4\n");
    stream = open_memstream (&buf, &len);
    fseek(stream, 5000, SEEK_SET);
    fseek(stream, 4096, SEEK_SET);
    fseek(stream, -1, SEEK_SET);        /* should have no effect */
    fputc('x', stream);
    if (ftell(stream) == 4097)
        printf("good\n");
    else
        printf("BAD: offset is %ld\n", ftell(stream));

    printf("DONE\n");

    return 0;
}

/* expected output:
Test1
buf=hello my world, len=14
buf=good-bye world, len=14
Test2
buf=hello my world, len=14
buf=good-bye, len=8
Test3
match
Test4
good
DONE
*/

#endif

#endif /* __APPLE__ */
