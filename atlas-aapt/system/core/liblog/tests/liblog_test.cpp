/*
 * Copyright (C) 2013-2014 The Android Open Source Project
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

#include <fcntl.h>
#include <inttypes.h>
#include <signal.h>
#include <string.h>

#include <cutils/properties.h>
#include <gtest/gtest.h>
#include <log/log.h>
#include <log/logger.h>
#include <log/log_read.h>
#include <log/logprint.h>

// enhanced version of LOG_FAILURE_RETRY to add support for EAGAIN and
// non-syscall libs. Since we are only using this in the emergency of
// a signal to stuff a terminating code into the logs, we will spin rather
// than try a usleep.
#define LOG_FAILURE_RETRY(exp) ({  \
    typeof (exp) _rc;              \
    do {                           \
        _rc = (exp);               \
    } while (((_rc == -1)          \
           && ((errno == EINTR)    \
            || (errno == EAGAIN))) \
          || (_rc == -EINTR)       \
          || (_rc == -EAGAIN));    \
    _rc; })

TEST(liblog, __android_log_buf_print) {
    EXPECT_LT(0, __android_log_buf_print(LOG_ID_RADIO, ANDROID_LOG_INFO,
                                         "TEST__android_log_buf_print",
                                         "radio"));
    usleep(1000);
    EXPECT_LT(0, __android_log_buf_print(LOG_ID_SYSTEM, ANDROID_LOG_INFO,
                                         "TEST__android_log_buf_print",
                                         "system"));
    usleep(1000);
    EXPECT_LT(0, __android_log_buf_print(LOG_ID_MAIN, ANDROID_LOG_INFO,
                                         "TEST__android_log_buf_print",
                                         "main"));
    usleep(1000);
}

TEST(liblog, __android_log_buf_write) {
    EXPECT_LT(0, __android_log_buf_write(LOG_ID_RADIO, ANDROID_LOG_INFO,
                                         "TEST__android_log_buf_write",
                                         "radio"));
    usleep(1000);
    EXPECT_LT(0, __android_log_buf_write(LOG_ID_SYSTEM, ANDROID_LOG_INFO,
                                         "TEST__android_log_buf_write",
                                         "system"));
    usleep(1000);
    EXPECT_LT(0, __android_log_buf_write(LOG_ID_MAIN, ANDROID_LOG_INFO,
                                         "TEST__android_log_buf_write",
                                         "main"));
    usleep(1000);
}

TEST(liblog, __android_log_btwrite) {
    int intBuf = 0xDEADBEEF;
    EXPECT_LT(0, __android_log_btwrite(0,
                                      EVENT_TYPE_INT,
                                      &intBuf, sizeof(intBuf)));
    long long longBuf = 0xDEADBEEFA55A5AA5;
    EXPECT_LT(0, __android_log_btwrite(0,
                                      EVENT_TYPE_LONG,
                                      &longBuf, sizeof(longBuf)));
    usleep(1000);
    char Buf[] = "\20\0\0\0DeAdBeEfA55a5aA5";
    EXPECT_LT(0, __android_log_btwrite(0,
                                      EVENT_TYPE_STRING,
                                      Buf, sizeof(Buf) - 1));
    usleep(1000);
}

static void* ConcurrentPrintFn(void *arg) {
    int ret = __android_log_buf_print(LOG_ID_MAIN, ANDROID_LOG_INFO,
                                  "TEST__android_log_print", "Concurrent %" PRIuPTR,
                                  reinterpret_cast<uintptr_t>(arg));
    return reinterpret_cast<void*>(ret);
}

#define NUM_CONCURRENT 64
#define _concurrent_name(a,n) a##__concurrent##n
#define concurrent_name(a,n) _concurrent_name(a,n)

TEST(liblog, concurrent_name(__android_log_buf_print, NUM_CONCURRENT)) {
    pthread_t t[NUM_CONCURRENT];
    int i;
    for (i=0; i < NUM_CONCURRENT; i++) {
        ASSERT_EQ(0, pthread_create(&t[i], NULL,
                                    ConcurrentPrintFn,
                                    reinterpret_cast<void *>(i)));
    }
    int ret = 0;
    for (i=0; i < NUM_CONCURRENT; i++) {
        void* result;
        ASSERT_EQ(0, pthread_join(t[i], &result));
        int this_result = reinterpret_cast<uintptr_t>(result);
        if ((0 == ret) && (0 != this_result)) {
            ret = this_result;
        }
    }
    ASSERT_LT(0, ret);
}

TEST(liblog, __android_log_btwrite__android_logger_list_read) {
    struct logger_list *logger_list;

    pid_t pid = getpid();

    ASSERT_TRUE(NULL != (logger_list = android_logger_list_open(
        LOG_ID_EVENTS, ANDROID_LOG_RDONLY | ANDROID_LOG_NONBLOCK, 1000, pid)));

    log_time ts(CLOCK_MONOTONIC);

    ASSERT_LT(0, __android_log_btwrite(0, EVENT_TYPE_LONG, &ts, sizeof(ts)));
    usleep(1000000);

    int count = 0;

    for (;;) {
        log_msg log_msg;
        if (android_logger_list_read(logger_list, &log_msg) <= 0) {
            break;
        }

        ASSERT_EQ(log_msg.entry.pid, pid);

        if ((log_msg.entry.len != (4 + 1 + 8))
         || (log_msg.id() != LOG_ID_EVENTS)) {
            continue;
        }

        char *eventData = log_msg.msg();

        if (eventData[4] != EVENT_TYPE_LONG) {
            continue;
        }

        log_time tx(eventData + 4 + 1);
        if (ts == tx) {
            ++count;
        }
    }

    EXPECT_EQ(1, count);

    android_logger_list_close(logger_list);
}

static unsigned signaled;
log_time signal_time;

static void caught_blocking(int /*signum*/)
{
    unsigned long long v = 0xDEADBEEFA55A0000ULL;

    v += getpid() & 0xFFFF;

    ++signaled;
    if ((signal_time.tv_sec == 0) && (signal_time.tv_nsec == 0)) {
        signal_time = log_time(CLOCK_MONOTONIC);
        signal_time.tv_sec += 2;
    }

    LOG_FAILURE_RETRY(__android_log_btwrite(0, EVENT_TYPE_LONG, &v, sizeof(v)));
}

// Fill in current process user and system time in 10ms increments
static void get_ticks(unsigned long long *uticks, unsigned long long *sticks)
{
    *uticks = *sticks = 0;

    pid_t pid = getpid();

    char buffer[512];
    snprintf(buffer, sizeof(buffer), "/proc/%u/stat", pid);

    FILE *fp = fopen(buffer, "r");
    if (!fp) {
        return;
    }

    char *cp = fgets(buffer, sizeof(buffer), fp);
    fclose(fp);
    if (!cp) {
        return;
    }

    pid_t d;
    char s[sizeof(buffer)];
    char c;
    long long ll;
    unsigned long long ull;

    if (15 != sscanf(buffer,
      "%d %s %c %lld %lld %lld %lld %lld %llu %llu %llu %llu %llu %llu %llu ",
      &d, s, &c, &ll, &ll, &ll, &ll, &ll, &ull, &ull, &ull, &ull, &ull,
      uticks, sticks)) {
        *uticks = *sticks = 0;
    }
}

TEST(liblog, android_logger_list_read__cpu) {
    struct logger_list *logger_list;
    unsigned long long v = 0xDEADBEEFA55A0000ULL;

    pid_t pid = getpid();

    v += pid & 0xFFFF;

    ASSERT_TRUE(NULL != (logger_list = android_logger_list_open(
        LOG_ID_EVENTS, ANDROID_LOG_RDONLY, 1000, pid)));

    int count = 0;

    int signals = 0;

    unsigned long long uticks_start;
    unsigned long long sticks_start;
    get_ticks(&uticks_start, &sticks_start);

    const unsigned alarm_time = 10;

    memset(&signal_time, 0, sizeof(signal_time));

    signal(SIGALRM, caught_blocking);
    alarm(alarm_time);

    signaled = 0;

    do {
        log_msg log_msg;
        if (android_logger_list_read(logger_list, &log_msg) <= 0) {
            break;
        }

        alarm(alarm_time);

        ++count;

        ASSERT_EQ(log_msg.entry.pid, pid);

        if ((log_msg.entry.len != (4 + 1 + 8))
         || (log_msg.id() != LOG_ID_EVENTS)) {
            continue;
        }

        char *eventData = log_msg.msg();

        if (eventData[4] != EVENT_TYPE_LONG) {
            continue;
        }

        unsigned long long l = eventData[4 + 1 + 0] & 0xFF;
        l |= (unsigned long long) (eventData[4 + 1 + 1] & 0xFF) << 8;
        l |= (unsigned long long) (eventData[4 + 1 + 2] & 0xFF) << 16;
        l |= (unsigned long long) (eventData[4 + 1 + 3] & 0xFF) << 24;
        l |= (unsigned long long) (eventData[4 + 1 + 4] & 0xFF) << 32;
        l |= (unsigned long long) (eventData[4 + 1 + 5] & 0xFF) << 40;
        l |= (unsigned long long) (eventData[4 + 1 + 6] & 0xFF) << 48;
        l |= (unsigned long long) (eventData[4 + 1 + 7] & 0xFF) << 56;

        if (l == v) {
            ++signals;
            break;
        }
    } while (!signaled || (log_time(CLOCK_MONOTONIC) < signal_time));
    alarm(0);
    signal(SIGALRM, SIG_DFL);

    EXPECT_LT(1, count);

    EXPECT_EQ(1, signals);

    android_logger_list_close(logger_list);

    unsigned long long uticks_end;
    unsigned long long sticks_end;
    get_ticks(&uticks_end, &sticks_end);

    // Less than 1% in either user or system time, or both
    const unsigned long long one_percent_ticks = alarm_time;
    unsigned long long user_ticks = uticks_end - uticks_start;
    unsigned long long system_ticks = sticks_end - sticks_start;
    EXPECT_GT(one_percent_ticks, user_ticks);
    EXPECT_GT(one_percent_ticks, system_ticks);
    EXPECT_GT(one_percent_ticks, user_ticks + system_ticks);
}

static const char max_payload_tag[] = "TEST_max_payload_XXXX";
static const char max_payload_buf[LOGGER_ENTRY_MAX_PAYLOAD
    - sizeof(max_payload_tag) - 1] = "LEONATO\n\
I learn in this letter that Don Peter of Arragon\n\
comes this night to Messina\n\
MESSENGER\n\
He is very near by this: he was not three leagues off\n\
when I left him\n\
LEONATO\n\
How many gentlemen have you lost in this action?\n\
MESSENGER\n\
But few of any sort, and none of name\n\
LEONATO\n\
A victory is twice itself when the achiever brings\n\
home full numbers. I find here that Don Peter hath\n\
bestowed much honour on a young Florentine called Claudio\n\
MESSENGER\n\
Much deserved on his part and equally remembered by\n\
Don Pedro: he hath borne himself beyond the\n\
promise of his age, doing, in the figure of a lamb,\n\
the feats of a lion: he hath indeed better\n\
bettered expectation than you must expect of me to\n\
tell you how\n\
LEONATO\n\
He hath an uncle here in Messina will be very much\n\
glad of it.\n\
MESSENGER\n\
I have already delivered him letters, and there\n\
appears much joy in him; even so much that joy could\n\
not show itself modest enough without a badge of\n\
bitterness.\n\
LEONATO\n\
Did he break out into tears?\n\
MESSENGER\n\
In great measure.\n\
LEONATO\n\
A kind overflow of kindness: there are no faces\n\
truer than those that are so washed. How much\n\
better is it to weep at joy than to joy at weeping!\n\
BEATRICE\n\
I pray you, is Signior Mountanto returned from the\n\
wars or no?\n\
MESSENGER\n\
I know none of that name, lady: there was none such\n\
in the army of any sort.\n\
LEONATO\n\
What is he that you ask for, niece?\n\
HERO\n\
My cousin means Signior Benedick of Padua.\n\
MESSENGER\n\
O, he's returned; and as pleasant as ever he was.\n\
BEATRICE\n\
He set up his bills here in Messina and challenged\n\
Cupid at the flight; and my uncle's fool, reading\n\
the challenge, subscribed for Cupid, and challenged\n\
him at the bird-bolt. I pray you, how many hath he\n\
killed and eaten in these wars? But how many hath\n\
he killed? for indeed I promised to eat all of his killing.\n\
LEONATO\n\
Faith, niece, you tax Signior Benedick too much;\n\
but he'll be meet with you, I doubt it not.\n\
MESSENGER\n\
He hath done good service, lady, in these wars.\n\
BEATRICE\n\
You had musty victual, and he hath holp to eat it:\n\
he is a very valiant trencherman; he hath an\n\
excellent stomach.\n\
MESSENGER\n\
And a good soldier too, lady.\n\
BEATRICE\n\
And a good soldier to a lady: but what is he to a lord?\n\
MESSENGER\n\
A lord to a lord, a man to a man; stuffed with all\n\
honourable virtues.\n\
BEATRICE\n\
It is so, indeed; he is no less than a stuffed man:\n\
but for the stuffing,--well, we are all mortal.\n\
LEONATO\n\
You must not, sir, mistake my niece. There is a\n\
kind of merry war betwixt Signior Benedick and her:\n\
they never meet but there's a skirmish of wit\n\
between them.\n\
BEATRICE\n\
Alas! he gets nothing by that. In our last\n\
conflict four of his five wits went halting off, and\n\
now is the whole man governed with one: so that if\n\
he have wit enough to keep himself warm, let him\n\
bear it for a difference between himself and his\n\
horse; for it is all the wealth that he hath left,\n\
to be known a reasonable creature. Who is his\n\
companion now? He hath every month a new sworn brother.\n\
MESSENGER\n\
Is't possible?\n\
BEATRICE\n\
Very easily possible: he wears his faith but as\n\
the fashion of his hat; it ever changes with the\n\
next block.\n\
MESSENGER\n\
I see, lady, the gentleman is not in your books.\n\
BEATRICE\n\
No; an he were, I would burn my study. But, I pray\n\
you, who is his companion? Is there no young\n\
squarer now that will make a voyage with him to the devil?\n\
MESSENGER\n\
He is most in the company of the right noble Claudio.\n\
BEATRICE\n\
O Lord, he will hang upon him like a disease: he\n\
is sooner caught than the pestilence, and the taker\n\
runs presently mad. God help the noble Claudio! if\n\
he have caught the Benedick, it will cost him a\n\
thousand pound ere a' be cured.\n\
MESSENGER\n\
I will hold friends with you, lady.\n\
BEATRICE\n\
Do, good friend.\n\
LEONATO\n\
You will never run mad, niece.\n\
BEATRICE\n\
No, not till a hot January.\n\
MESSENGER\n\
Don Pedro is approached.\n\
Enter DON PEDRO, DON JOHN, CLAUDIO, BENEDICK, and BALTHASAR\n\
\n\
DON PEDRO\n\
Good Signior Leonato, you are come to meet your\n\
trouble: the fashion of the world is to avoid\n\
cost, and you encounter it\n\
LEONATO\n\
Never came trouble to my house in the likeness";

TEST(liblog, max_payload) {
    pid_t pid = getpid();
    char tag[sizeof(max_payload_tag)];
    memcpy(tag, max_payload_tag, sizeof(tag));
    snprintf(tag + sizeof(tag) - 5, 5, "%04X", pid & 0xFFFF);

    LOG_FAILURE_RETRY(__android_log_buf_write(LOG_ID_SYSTEM, ANDROID_LOG_INFO,
                                              tag, max_payload_buf));
    sleep(2);

    struct logger_list *logger_list;

    ASSERT_TRUE(NULL != (logger_list = android_logger_list_open(
        LOG_ID_SYSTEM, ANDROID_LOG_RDONLY, 100, 0)));

    bool matches = false;
    ssize_t max_len = 0;

    for(;;) {
        log_msg log_msg;
        if (android_logger_list_read(logger_list, &log_msg) <= 0) {
            break;
        }

        if ((log_msg.entry.pid != pid) || (log_msg.id() != LOG_ID_SYSTEM)) {
            continue;
        }

        char *data = log_msg.msg() + 1;

        if (strcmp(data, tag)) {
            continue;
        }

        data += strlen(data) + 1;

        const char *left = data;
        const char *right = max_payload_buf;
        while (*left && *right && (*left == *right)) {
            ++left;
            ++right;
        }

        if (max_len <= (left - data)) {
            max_len = left - data + 1;
        }

        if (max_len > 512) {
            matches = true;
            break;
        }
    }

    android_logger_list_close(logger_list);

    EXPECT_EQ(true, matches);

    EXPECT_LE(sizeof(max_payload_buf), static_cast<size_t>(max_len));
}

TEST(liblog, too_big_payload) {
    pid_t pid = getpid();
    static const char big_payload_tag[] = "TEST_big_payload_XXXX";
    char tag[sizeof(big_payload_tag)];
    memcpy(tag, big_payload_tag, sizeof(tag));
    snprintf(tag + sizeof(tag) - 5, 5, "%04X", pid & 0xFFFF);

    std::string longString(3266519, 'x');

    ssize_t ret = LOG_FAILURE_RETRY(__android_log_buf_write(LOG_ID_SYSTEM,
                                    ANDROID_LOG_INFO, tag, longString.c_str()));

    struct logger_list *logger_list;

    ASSERT_TRUE(NULL != (logger_list = android_logger_list_open(
        LOG_ID_SYSTEM, ANDROID_LOG_RDONLY | ANDROID_LOG_NONBLOCK, 100, 0)));

    ssize_t max_len = 0;

    for(;;) {
        log_msg log_msg;
        if (android_logger_list_read(logger_list, &log_msg) <= 0) {
            break;
        }

        if ((log_msg.entry.pid != pid) || (log_msg.id() != LOG_ID_SYSTEM)) {
            continue;
        }

        char *data = log_msg.msg() + 1;

        if (strcmp(data, tag)) {
            continue;
        }

        data += strlen(data) + 1;

        const char *left = data;
        const char *right = longString.c_str();
        while (*left && *right && (*left == *right)) {
            ++left;
            ++right;
        }

        if (max_len <= (left - data)) {
            max_len = left - data + 1;
        }
    }

    android_logger_list_close(logger_list);

    EXPECT_LE(LOGGER_ENTRY_MAX_PAYLOAD - sizeof(big_payload_tag),
              static_cast<size_t>(max_len));

    EXPECT_EQ(ret, max_len + static_cast<ssize_t>(sizeof(big_payload_tag)));
}

TEST(liblog, dual_reader) {
    struct logger_list *logger_list1;

    // >25 messages due to liblog.__android_log_buf_print__concurrentXX above.
    ASSERT_TRUE(NULL != (logger_list1 = android_logger_list_open(
        LOG_ID_MAIN, ANDROID_LOG_RDONLY | ANDROID_LOG_NONBLOCK, 25, 0)));

    struct logger_list *logger_list2;

    if (NULL == (logger_list2 = android_logger_list_open(
            LOG_ID_MAIN, ANDROID_LOG_RDONLY | ANDROID_LOG_NONBLOCK, 15, 0))) {
        android_logger_list_close(logger_list1);
        ASSERT_TRUE(NULL != logger_list2);
    }

    int count1 = 0;
    bool done1 = false;
    int count2 = 0;
    bool done2 = false;

    do {
        log_msg log_msg;

        if (!done1) {
            if (android_logger_list_read(logger_list1, &log_msg) <= 0) {
                done1 = true;
            } else {
                ++count1;
            }
        }

        if (!done2) {
            if (android_logger_list_read(logger_list2, &log_msg) <= 0) {
                done2 = true;
            } else {
                ++count2;
            }
        }
    } while ((!done1) || (!done2));

    android_logger_list_close(logger_list1);
    android_logger_list_close(logger_list2);

    EXPECT_EQ(25, count1);
    EXPECT_EQ(15, count2);
}

TEST(liblog, android_logger_get_) {
    struct logger_list * logger_list = android_logger_list_alloc(ANDROID_LOG_WRONLY, 0, 0);

    for(int i = LOG_ID_MIN; i < LOG_ID_MAX; ++i) {
        log_id_t id = static_cast<log_id_t>(i);
        const char *name = android_log_id_to_name(id);
        if (id != android_name_to_log_id(name)) {
            continue;
        }
        fprintf(stderr, "log buffer %s\r", name);
        struct logger * logger;
        EXPECT_TRUE(NULL != (logger = android_logger_open(logger_list, id)));
        EXPECT_EQ(id, android_logger_get_id(logger));
        /* crash buffer is allowed to be empty, that is actually healthy! */
        if (android_logger_get_log_size(logger) || strcmp("crash", name)) {
            EXPECT_LT(0, android_logger_get_log_size(logger));
        }
        EXPECT_LT(0, android_logger_get_log_readable_size(logger));
        EXPECT_LT(0, android_logger_get_log_version(logger));
    }

    android_logger_list_close(logger_list);
}

static bool checkPriForTag(AndroidLogFormat *p_format, const char *tag, android_LogPriority pri) {
    return android_log_shouldPrintLine(p_format, tag, pri)
        && !android_log_shouldPrintLine(p_format, tag, (android_LogPriority)(pri - 1));
}

TEST(liblog, filterRule) {
    static const char tag[] = "random";

    AndroidLogFormat *p_format = android_log_format_new();

    android_log_addFilterRule(p_format,"*:i");

    EXPECT_TRUE(checkPriForTag(p_format, tag, ANDROID_LOG_INFO));
    EXPECT_TRUE(android_log_shouldPrintLine(p_format, tag, ANDROID_LOG_DEBUG) == 0);
    android_log_addFilterRule(p_format, "*");
    EXPECT_TRUE (checkPriForTag(p_format, tag, ANDROID_LOG_DEBUG));
    EXPECT_TRUE(android_log_shouldPrintLine(p_format, tag, ANDROID_LOG_DEBUG) > 0);
    android_log_addFilterRule(p_format, "*:v");
    EXPECT_TRUE (checkPriForTag(p_format, tag, ANDROID_LOG_VERBOSE));
    EXPECT_TRUE(android_log_shouldPrintLine(p_format, tag, ANDROID_LOG_DEBUG) > 0);
    android_log_addFilterRule(p_format, "*:i");
    EXPECT_TRUE (checkPriForTag(p_format, tag, ANDROID_LOG_INFO));
    EXPECT_TRUE(android_log_shouldPrintLine(p_format, tag, ANDROID_LOG_DEBUG) == 0);

    android_log_addFilterRule(p_format, tag);
    EXPECT_TRUE (checkPriForTag(p_format, tag, ANDROID_LOG_VERBOSE));
    EXPECT_TRUE(android_log_shouldPrintLine(p_format, tag, ANDROID_LOG_DEBUG) > 0);
    android_log_addFilterRule(p_format, "random:v");
    EXPECT_TRUE (checkPriForTag(p_format, tag, ANDROID_LOG_VERBOSE));
    EXPECT_TRUE(android_log_shouldPrintLine(p_format, tag, ANDROID_LOG_DEBUG) > 0);
    android_log_addFilterRule(p_format, "random:d");
    EXPECT_TRUE (checkPriForTag(p_format, tag, ANDROID_LOG_DEBUG));
    EXPECT_TRUE(android_log_shouldPrintLine(p_format, tag, ANDROID_LOG_DEBUG) > 0);
    android_log_addFilterRule(p_format, "random:w");
    EXPECT_TRUE (checkPriForTag(p_format, tag, ANDROID_LOG_WARN));
    EXPECT_TRUE(android_log_shouldPrintLine(p_format, tag, ANDROID_LOG_DEBUG) == 0);

    android_log_addFilterRule(p_format, "crap:*");
    EXPECT_TRUE (checkPriForTag(p_format, "crap", ANDROID_LOG_VERBOSE));
    EXPECT_TRUE(android_log_shouldPrintLine(p_format, "crap", ANDROID_LOG_VERBOSE) > 0);

    // invalid expression
    EXPECT_TRUE (android_log_addFilterRule(p_format, "random:z") < 0);
    EXPECT_TRUE (checkPriForTag(p_format, tag, ANDROID_LOG_WARN));
    EXPECT_TRUE(android_log_shouldPrintLine(p_format, tag, ANDROID_LOG_DEBUG) == 0);

    // Issue #550946
    EXPECT_TRUE(android_log_addFilterString(p_format, " ") == 0);
    EXPECT_TRUE(checkPriForTag(p_format, tag, ANDROID_LOG_WARN));

    // note trailing space
    EXPECT_TRUE(android_log_addFilterString(p_format, "*:s random:d ") == 0);
    EXPECT_TRUE(checkPriForTag(p_format, tag, ANDROID_LOG_DEBUG));

    EXPECT_TRUE(android_log_addFilterString(p_format, "*:s random:z") < 0);

#if 0 // bitrot, seek update
    char defaultBuffer[512];

    android_log_formatLogLine(p_format,
        defaultBuffer, sizeof(defaultBuffer), 0, ANDROID_LOG_ERROR, 123,
        123, 123, tag, "nofile", strlen("Hello"), "Hello", NULL);

    fprintf(stderr, "%s\n", defaultBuffer);
#endif

    android_log_format_free(p_format);
}

TEST(liblog, is_loggable) {
    static const char tag[] = "is_loggable";
    static const char log_namespace[] = "persist.log.tag.";
    static const size_t base_offset = 8; /* skip "persist." */
    // sizeof("string") = strlen("string") + 1
    char key[sizeof(log_namespace) + sizeof(tag) - 1];
    char hold[4][PROP_VALUE_MAX];
    static const struct {
        int level;
        char type;
    } levels[] = {
        { ANDROID_LOG_VERBOSE, 'v' },
        { ANDROID_LOG_DEBUG  , 'd' },
        { ANDROID_LOG_INFO   , 'i' },
        { ANDROID_LOG_WARN   , 'w' },
        { ANDROID_LOG_ERROR  , 'e' },
        { ANDROID_LOG_FATAL  , 'a' },
        { -1                 , 's' },
        { -2                 , 'g' }, // Illegal value, resort to default
    };

    // Set up initial test condition
    memset(hold, 0, sizeof(hold));
    snprintf(key, sizeof(key), "%s%s", log_namespace, tag);
    property_get(key, hold[0], "");
    property_set(key, "");
    property_get(key + base_offset, hold[1], "");
    property_set(key + base_offset, "");
    strcpy(key, log_namespace);
    key[sizeof(log_namespace) - 2] = '\0';
    property_get(key, hold[2], "");
    property_set(key, "");
    property_get(key, hold[3], "");
    property_set(key + base_offset, "");

    // All combinations of level and defaults
    for(size_t i = 0; i < (sizeof(levels) / sizeof(levels[0])); ++i) {
        if (levels[i].level == -2) {
            continue;
        }
        for(size_t j = 0; j < (sizeof(levels) / sizeof(levels[0])); ++j) {
            if (levels[j].level == -2) {
                continue;
            }
            fprintf(stderr, "i=%zu j=%zu\r", i, j);
            if ((levels[i].level < levels[j].level)
                    || (levels[j].level == -1)) {
                EXPECT_FALSE(__android_log_is_loggable(levels[i].level, tag,
                                                       levels[j].level));
            } else {
                EXPECT_TRUE(__android_log_is_loggable(levels[i].level, tag,
                                                      levels[j].level));
            }
        }
    }

    // All combinations of level and tag and global properties
    for(size_t i = 0; i < (sizeof(levels) / sizeof(levels[0])); ++i) {
        if (levels[i].level == -2) {
            continue;
        }
        for(size_t j = 0; j < (sizeof(levels) / sizeof(levels[0])); ++j) {
            char buf[2];
            buf[0] = levels[j].type;
            buf[1] = '\0';

            snprintf(key, sizeof(key), "%s%s", log_namespace, tag);
            fprintf(stderr, "i=%zu j=%zu property_set(\"%s\",\"%s\")\r",
                    i, j, key, buf);
            property_set(key, buf);
            if ((levels[i].level < levels[j].level)
                    || (levels[j].level == -1)
                    || ((levels[i].level < ANDROID_LOG_DEBUG)
                        && (levels[j].level == -2))) {
                EXPECT_FALSE(__android_log_is_loggable(levels[i].level, tag,
                                                       ANDROID_LOG_DEBUG));
            } else {
                EXPECT_TRUE(__android_log_is_loggable(levels[i].level, tag,
                                                      ANDROID_LOG_DEBUG));
            }
            property_set(key, "");

            fprintf(stderr, "i=%zu j=%zu property_set(\"%s\",\"%s\")\r",
                    i, j, key + base_offset, buf);
            property_set(key + base_offset, buf);
            if ((levels[i].level < levels[j].level)
                    || (levels[j].level == -1)
                    || ((levels[i].level < ANDROID_LOG_DEBUG)
                        && (levels[j].level == -2))) {
                EXPECT_FALSE(__android_log_is_loggable(levels[i].level, tag,
                                                       ANDROID_LOG_DEBUG));
            } else {
                EXPECT_TRUE(__android_log_is_loggable(levels[i].level, tag,
                                                      ANDROID_LOG_DEBUG));
            }
            property_set(key + base_offset, "");

            strcpy(key, log_namespace);
            key[sizeof(log_namespace) - 2] = '\0';
            fprintf(stderr, "i=%zu j=%zu property_set(\"%s\",\"%s\")\r",
                    i, j, key, buf);
            property_set(key, buf);
            if ((levels[i].level < levels[j].level)
                    || (levels[j].level == -1)
                    || ((levels[i].level < ANDROID_LOG_DEBUG)
                        && (levels[j].level == -2))) {
                EXPECT_FALSE(__android_log_is_loggable(levels[i].level, tag,
                                                       ANDROID_LOG_DEBUG));
            } else {
                EXPECT_TRUE(__android_log_is_loggable(levels[i].level, tag,
                                                      ANDROID_LOG_DEBUG));
            }
            property_set(key, "");

            fprintf(stderr, "i=%zu j=%zu property_set(\"%s\",\"%s\")\r",
                    i, j, key + base_offset, buf);
            property_set(key + base_offset, buf);
            if ((levels[i].level < levels[j].level)
                    || (levels[j].level == -1)
                    || ((levels[i].level < ANDROID_LOG_DEBUG)
                        && (levels[j].level == -2))) {
                EXPECT_FALSE(__android_log_is_loggable(levels[i].level, tag,
                                                       ANDROID_LOG_DEBUG));
            } else {
                EXPECT_TRUE(__android_log_is_loggable(levels[i].level, tag,
                                                      ANDROID_LOG_DEBUG));
            }
            property_set(key + base_offset, "");
        }
    }

    // All combinations of level and tag properties, but with global set to INFO
    strcpy(key, log_namespace);
    key[sizeof(log_namespace) - 2] = '\0';
    property_set(key, "I");
    snprintf(key, sizeof(key), "%s%s", log_namespace, tag);
    for(size_t i = 0; i < (sizeof(levels) / sizeof(levels[0])); ++i) {
        if (levels[i].level == -2) {
            continue;
        }
        for(size_t j = 0; j < (sizeof(levels) / sizeof(levels[0])); ++j) {
            char buf[2];
            buf[0] = levels[j].type;
            buf[1] = '\0';

            fprintf(stderr, "i=%zu j=%zu property_set(\"%s\",\"%s\")\r",
                    i, j, key, buf);
            property_set(key, buf);
            if ((levels[i].level < levels[j].level)
                    || (levels[j].level == -1)
                    || ((levels[i].level < ANDROID_LOG_INFO) // Yes INFO
                        && (levels[j].level == -2))) {
                EXPECT_FALSE(__android_log_is_loggable(levels[i].level, tag,
                                                       ANDROID_LOG_DEBUG));
            } else {
                EXPECT_TRUE(__android_log_is_loggable(levels[i].level, tag,
                                                      ANDROID_LOG_DEBUG));
            }
            property_set(key, "");

            fprintf(stderr, "i=%zu j=%zu property_set(\"%s\",\"%s\")\r",
                    i, j, key + base_offset, buf);
            property_set(key + base_offset, buf);
            if ((levels[i].level < levels[j].level)
                    || (levels[j].level == -1)
                    || ((levels[i].level < ANDROID_LOG_INFO) // Yes INFO
                        && (levels[j].level == -2))) {
                EXPECT_FALSE(__android_log_is_loggable(levels[i].level, tag,
                                                       ANDROID_LOG_DEBUG));
            } else {
                EXPECT_TRUE(__android_log_is_loggable(levels[i].level, tag,
                                                      ANDROID_LOG_DEBUG));
            }
            property_set(key + base_offset, "");
        }
    }

    // reset parms
    snprintf(key, sizeof(key), "%s%s", log_namespace, tag);
    property_set(key, hold[0]);
    property_set(key + base_offset, hold[1]);
    strcpy(key, log_namespace);
    key[sizeof(log_namespace) - 2] = '\0';
    property_set(key, hold[2]);
    property_set(key + base_offset, hold[3]);
}

static inline int32_t get4LE(const char* src)
{
    return src[0] | (src[1] << 8) | (src[2] << 16) | (src[3] << 24);
}

TEST(liblog, android_errorWriteWithInfoLog__android_logger_list_read__typical) {
    const int TAG = 123456781;
    const char SUBTAG[] = "test-subtag";
    const int UID = -1;
    const int DATA_LEN = 200;
    struct logger_list *logger_list;

    pid_t pid = getpid();

    ASSERT_TRUE(NULL != (logger_list = android_logger_list_open(
        LOG_ID_EVENTS, ANDROID_LOG_RDONLY | ANDROID_LOG_NONBLOCK, 1000, pid)));

    ASSERT_LT(0, android_errorWriteWithInfoLog(
            TAG, SUBTAG, UID, max_payload_buf, DATA_LEN));

    sleep(2);

    int count = 0;

    for (;;) {
        log_msg log_msg;
        if (android_logger_list_read(logger_list, &log_msg) <= 0) {
            break;
        }

        char *eventData = log_msg.msg();

        // Tag
        int tag = get4LE(eventData);
        eventData += 4;

        if (tag != TAG) {
            continue;
        }

        // List type
        ASSERT_EQ(EVENT_TYPE_LIST, eventData[0]);
        eventData++;

        // Number of elements in list
        ASSERT_EQ(3, eventData[0]);
        eventData++;

        // Element #1: string type for subtag
        ASSERT_EQ(EVENT_TYPE_STRING, eventData[0]);
        eventData++;

        ASSERT_EQ((int) strlen(SUBTAG), get4LE(eventData));
        eventData +=4;

        if (memcmp(SUBTAG, eventData, strlen(SUBTAG))) {
            continue;
        }
        eventData += strlen(SUBTAG);

        // Element #2: int type for uid
        ASSERT_EQ(EVENT_TYPE_INT, eventData[0]);
        eventData++;

        ASSERT_EQ(UID, get4LE(eventData));
        eventData += 4;

        // Element #3: string type for data
        ASSERT_EQ(EVENT_TYPE_STRING, eventData[0]);
        eventData++;

        ASSERT_EQ(DATA_LEN, get4LE(eventData));
        eventData += 4;

        if (memcmp(max_payload_buf, eventData, DATA_LEN)) {
            continue;
        }

        ++count;
    }

    EXPECT_EQ(1, count);

    android_logger_list_close(logger_list);
}

TEST(liblog, android_errorWriteWithInfoLog__android_logger_list_read__data_too_large) {
    const int TAG = 123456782;
    const char SUBTAG[] = "test-subtag";
    const int UID = -1;
    const int DATA_LEN = sizeof(max_payload_buf);
    struct logger_list *logger_list;

    pid_t pid = getpid();

    ASSERT_TRUE(NULL != (logger_list = android_logger_list_open(
        LOG_ID_EVENTS, ANDROID_LOG_RDONLY | ANDROID_LOG_NONBLOCK, 1000, pid)));

    ASSERT_LT(0, android_errorWriteWithInfoLog(
            TAG, SUBTAG, UID, max_payload_buf, DATA_LEN));

    sleep(2);

    int count = 0;

    for (;;) {
        log_msg log_msg;
        if (android_logger_list_read(logger_list, &log_msg) <= 0) {
            break;
        }

        char *eventData = log_msg.msg();
        char *original = eventData;

        // Tag
        int tag = get4LE(eventData);
        eventData += 4;

        if (tag != TAG) {
            continue;
        }

        // List type
        ASSERT_EQ(EVENT_TYPE_LIST, eventData[0]);
        eventData++;

        // Number of elements in list
        ASSERT_EQ(3, eventData[0]);
        eventData++;

        // Element #1: string type for subtag
        ASSERT_EQ(EVENT_TYPE_STRING, eventData[0]);
        eventData++;

        ASSERT_EQ((int) strlen(SUBTAG), get4LE(eventData));
        eventData +=4;

        if (memcmp(SUBTAG, eventData, strlen(SUBTAG))) {
            continue;
        }
        eventData += strlen(SUBTAG);

        // Element #2: int type for uid
        ASSERT_EQ(EVENT_TYPE_INT, eventData[0]);
        eventData++;

        ASSERT_EQ(UID, get4LE(eventData));
        eventData += 4;

        // Element #3: string type for data
        ASSERT_EQ(EVENT_TYPE_STRING, eventData[0]);
        eventData++;

        size_t dataLen = get4LE(eventData);
        eventData += 4;

        if (memcmp(max_payload_buf, eventData, dataLen)) {
            continue;
        }
        eventData += dataLen;

        // 4 bytes for the tag, and 512 bytes for the log since the max_payload_buf should be
        // truncated.
        ASSERT_EQ(4 + 512, eventData - original);

        ++count;
    }

    EXPECT_EQ(1, count);

    android_logger_list_close(logger_list);
}

TEST(liblog, android_errorWriteWithInfoLog__android_logger_list_read__null_data) {
    const int TAG = 123456783;
    const char SUBTAG[] = "test-subtag";
    const int UID = -1;
    const int DATA_LEN = 200;
    struct logger_list *logger_list;

    pid_t pid = getpid();

    ASSERT_TRUE(NULL != (logger_list = android_logger_list_open(
        LOG_ID_EVENTS, ANDROID_LOG_RDONLY | ANDROID_LOG_NONBLOCK, 1000, pid)));

    ASSERT_GT(0, android_errorWriteWithInfoLog(
            TAG, SUBTAG, UID, NULL, DATA_LEN));

    sleep(2);

    int count = 0;

    for (;;) {
        log_msg log_msg;
        if (android_logger_list_read(logger_list, &log_msg) <= 0) {
            break;
        }

        char *eventData = log_msg.msg();

        // Tag
        int tag = get4LE(eventData);
        eventData += 4;

        if (tag == TAG) {
            // This tag should not have been written because the data was null
            count++;
            break;
        }
    }

    EXPECT_EQ(0, count);

    android_logger_list_close(logger_list);
}

TEST(liblog, android_errorWriteWithInfoLog__android_logger_list_read__subtag_too_long) {
    const int TAG = 123456784;
    const char SUBTAG[] = "abcdefghijklmnopqrstuvwxyz now i know my abc";
    const int UID = -1;
    const int DATA_LEN = 200;
    struct logger_list *logger_list;

    pid_t pid = getpid();

    ASSERT_TRUE(NULL != (logger_list = android_logger_list_open(
        LOG_ID_EVENTS, ANDROID_LOG_RDONLY | ANDROID_LOG_NONBLOCK, 1000, pid)));

    ASSERT_LT(0, android_errorWriteWithInfoLog(
            TAG, SUBTAG, UID, max_payload_buf, DATA_LEN));

    sleep(2);

    int count = 0;

    for (;;) {
        log_msg log_msg;
        if (android_logger_list_read(logger_list, &log_msg) <= 0) {
            break;
        }

        char *eventData = log_msg.msg();

        // Tag
        int tag = get4LE(eventData);
        eventData += 4;

        if (tag != TAG) {
            continue;
        }

        // List type
        ASSERT_EQ(EVENT_TYPE_LIST, eventData[0]);
        eventData++;

        // Number of elements in list
        ASSERT_EQ(3, eventData[0]);
        eventData++;

        // Element #1: string type for subtag
        ASSERT_EQ(EVENT_TYPE_STRING, eventData[0]);
        eventData++;

        // The subtag is longer than 32 and should be truncated to that.
        ASSERT_EQ(32, get4LE(eventData));
        eventData +=4;

        if (memcmp(SUBTAG, eventData, 32)) {
            continue;
        }
        eventData += 32;

        // Element #2: int type for uid
        ASSERT_EQ(EVENT_TYPE_INT, eventData[0]);
        eventData++;

        ASSERT_EQ(UID, get4LE(eventData));
        eventData += 4;

        // Element #3: string type for data
        ASSERT_EQ(EVENT_TYPE_STRING, eventData[0]);
        eventData++;

        ASSERT_EQ(DATA_LEN, get4LE(eventData));
        eventData += 4;

        if (memcmp(max_payload_buf, eventData, DATA_LEN)) {
            continue;
        }

        ++count;
    }

    EXPECT_EQ(1, count);

    android_logger_list_close(logger_list);
}

TEST(liblog, android_errorWriteLog__android_logger_list_read__success) {
    const int TAG = 123456785;
    const char SUBTAG[] = "test-subtag";
    struct logger_list *logger_list;

    pid_t pid = getpid();

    ASSERT_TRUE(NULL != (logger_list = android_logger_list_open(
        LOG_ID_EVENTS, ANDROID_LOG_RDONLY | ANDROID_LOG_NONBLOCK, 1000, pid)));

    ASSERT_LT(0, android_errorWriteLog(TAG, SUBTAG));

    sleep(2);

    int count = 0;

    for (;;) {
        log_msg log_msg;
        if (android_logger_list_read(logger_list, &log_msg) <= 0) {
            break;
        }

        char *eventData = log_msg.msg();

        // Tag
        int tag = get4LE(eventData);
        eventData += 4;

        if (tag != TAG) {
            continue;
        }

        // List type
        ASSERT_EQ(EVENT_TYPE_LIST, eventData[0]);
        eventData++;

        // Number of elements in list
        ASSERT_EQ(3, eventData[0]);
        eventData++;

        // Element #1: string type for subtag
        ASSERT_EQ(EVENT_TYPE_STRING, eventData[0]);
        eventData++;

        ASSERT_EQ((int) strlen(SUBTAG), get4LE(eventData));
        eventData +=4;

        if (memcmp(SUBTAG, eventData, strlen(SUBTAG))) {
            continue;
        }
        ++count;
    }

    EXPECT_EQ(1, count);

    android_logger_list_close(logger_list);
}

TEST(liblog, android_errorWriteLog__android_logger_list_read__null_subtag) {
    const int TAG = 123456786;
    struct logger_list *logger_list;

    pid_t pid = getpid();

    ASSERT_TRUE(NULL != (logger_list = android_logger_list_open(
        LOG_ID_EVENTS, ANDROID_LOG_RDONLY | ANDROID_LOG_NONBLOCK, 1000, pid)));

    ASSERT_GT(0, android_errorWriteLog(TAG, NULL));

    sleep(2);

    int count = 0;

    for (;;) {
        log_msg log_msg;
        if (android_logger_list_read(logger_list, &log_msg) <= 0) {
            break;
        }

        char *eventData = log_msg.msg();

        // Tag
        int tag = get4LE(eventData);
        eventData += 4;

        if (tag == TAG) {
            // This tag should not have been written because the data was null
            count++;
            break;
        }
    }

    EXPECT_EQ(0, count);

    android_logger_list_close(logger_list);
}
