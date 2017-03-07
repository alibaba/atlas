/*
 * Copyright (C) 2012-2014 The Android Open Source Project
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

#include <benchmark.h>

#include <inttypes.h>
#include <math.h>
#include <regex.h>
#include <stdio.h>
#include <stdlib.h>

#include <string>
#include <map>
#include <vector>

static uint64_t gBytesProcessed;
static uint64_t gBenchmarkTotalTimeNs;
static uint64_t gBenchmarkTotalTimeNsSquared;
static uint64_t gBenchmarkNum;
static uint64_t gBenchmarkStartTimeNs;

typedef std::vector< ::testing::Benchmark* > BenchmarkList;
static BenchmarkList* gBenchmarks;

static int Round(int n) {
  int base = 1;
  while (base*10 < n) {
    base *= 10;
  }
  if (n < 2*base) {
    return 2*base;
  }
  if (n < 5*base) {
    return 5*base;
  }
  return 10*base;
}

static uint64_t NanoTime() {
  struct timespec t;
  t.tv_sec = t.tv_nsec = 0;
  clock_gettime(CLOCK_MONOTONIC, &t);
  return static_cast<uint64_t>(t.tv_sec) * 1000000000ULL + t.tv_nsec;
}

namespace testing {

int PrettyPrintInt(char* str, int len, unsigned int arg)
{
  if (arg >= (1<<30) && arg % (1<<30) == 0) {
    return snprintf(str, len, "%uGi", arg/(1<<30));
  } else if (arg >= (1<<20) && arg % (1<<20) == 0) {
    return snprintf(str, len, "%uMi", arg/(1<<20));
  } else if (arg >= (1<<10) && arg % (1<<10) == 0) {
    return snprintf(str, len, "%uKi", arg/(1<<10));
  } else if (arg >= 1000000000 && arg % 1000000000 == 0) {
    return snprintf(str, len, "%uG", arg/1000000000);
  } else if (arg >= 1000000 && arg % 1000000 == 0) {
    return snprintf(str, len, "%uM", arg/1000000);
  } else if (arg >= 1000 && arg % 1000 == 0) {
    return snprintf(str, len, "%uK", arg/1000);
  } else {
    return snprintf(str, len, "%u", arg);
  }
}

bool ShouldRun(Benchmark* b, int argc, char* argv[]) {
  if (argc == 1) {
    return true;  // With no arguments, we run all benchmarks.
  }
  // Otherwise, we interpret each argument as a regular expression and
  // see if any of our benchmarks match.
  for (int i = 1; i < argc; i++) {
    regex_t re;
    if (regcomp(&re, argv[i], 0) != 0) {
      fprintf(stderr, "couldn't compile \"%s\" as a regular expression!\n", argv[i]);
      exit(EXIT_FAILURE);
    }
    int match = regexec(&re, b->Name(), 0, NULL, 0);
    regfree(&re);
    if (match != REG_NOMATCH) {
      return true;
    }
  }
  return false;
}

void BenchmarkRegister(Benchmark* b) {
  if (gBenchmarks == NULL) {
    gBenchmarks = new BenchmarkList;
  }
  gBenchmarks->push_back(b);
}

void RunRepeatedly(Benchmark* b, int iterations) {
  gBytesProcessed = 0;
  ResetBenchmarkTiming();
  uint64_t StartTimeNs = NanoTime();
  b->RunFn(iterations);
  // Catch us if we fail to log anything.
  if ((gBenchmarkTotalTimeNs == 0)
   && (StartTimeNs != 0)
   && (gBenchmarkStartTimeNs == 0)) {
    gBenchmarkTotalTimeNs = NanoTime() - StartTimeNs;
  }
}

void Run(Benchmark* b) {
  // run once in case it's expensive
  unsigned iterations = 1;
  uint64_t s = NanoTime();
  RunRepeatedly(b, iterations);
  s = NanoTime() - s;
  while (s < 2e9 && gBenchmarkTotalTimeNs < 1e9 && iterations < 1e9) {
    unsigned last = iterations;
    if (gBenchmarkTotalTimeNs/iterations == 0) {
      iterations = 1e9;
    } else {
      iterations = 1e9 / (gBenchmarkTotalTimeNs/iterations);
    }
    iterations = std::max(last + 1, std::min(iterations + iterations/2, 100*last));
    iterations = Round(iterations);
    s = NanoTime();
    RunRepeatedly(b, iterations);
    s = NanoTime() - s;
  }

  char throughput[100];
  throughput[0] = '\0';
  if (gBenchmarkTotalTimeNs > 0 && gBytesProcessed > 0) {
    double mib_processed = static_cast<double>(gBytesProcessed)/1e6;
    double seconds = static_cast<double>(gBenchmarkTotalTimeNs)/1e9;
    snprintf(throughput, sizeof(throughput), " %8.2f MiB/s", mib_processed/seconds);
  }

  char full_name[100];
  snprintf(full_name, sizeof(full_name), "%s%s%s", b->Name(),
           b->ArgName() ? "/" : "",
           b->ArgName() ? b->ArgName() : "");

  uint64_t mean = gBenchmarkTotalTimeNs / iterations;
  uint64_t sdev = 0;
  if (gBenchmarkNum == iterations) {
    mean = gBenchmarkTotalTimeNs / gBenchmarkNum;
    uint64_t nXvariance = gBenchmarkTotalTimeNsSquared * gBenchmarkNum
                        - (gBenchmarkTotalTimeNs * gBenchmarkTotalTimeNs);
    sdev = (sqrt((double)nXvariance) / gBenchmarkNum / gBenchmarkNum) + 0.5;
  }
  if (mean > (10000 * sdev)) {
    printf("%-25s %10" PRIu64 " %10" PRIu64 "%s\n", full_name,
            static_cast<uint64_t>(iterations), mean, throughput);
  } else {
    printf("%-25s %10" PRIu64 " %10" PRIu64 "(\317\203%" PRIu64 ")%s\n", full_name,
           static_cast<uint64_t>(iterations), mean, sdev, throughput);
  }
  fflush(stdout);
}

}  // namespace testing

void SetBenchmarkBytesProcessed(uint64_t x) {
  gBytesProcessed = x;
}

void ResetBenchmarkTiming() {
  gBenchmarkStartTimeNs = 0;
  gBenchmarkTotalTimeNs = 0;
  gBenchmarkTotalTimeNsSquared = 0;
  gBenchmarkNum = 0;
}

void StopBenchmarkTiming(void) {
  if (gBenchmarkStartTimeNs != 0) {
    int64_t diff = NanoTime() - gBenchmarkStartTimeNs;
    gBenchmarkTotalTimeNs += diff;
    gBenchmarkTotalTimeNsSquared += diff * diff;
    ++gBenchmarkNum;
  }
  gBenchmarkStartTimeNs = 0;
}

void StartBenchmarkTiming(void) {
  if (gBenchmarkStartTimeNs == 0) {
    gBenchmarkStartTimeNs = NanoTime();
  }
}

void StopBenchmarkTiming(uint64_t NanoTime) {
  if (gBenchmarkStartTimeNs != 0) {
    int64_t diff = NanoTime - gBenchmarkStartTimeNs;
    gBenchmarkTotalTimeNs += diff;
    gBenchmarkTotalTimeNsSquared += diff * diff;
    if (NanoTime != 0) {
      ++gBenchmarkNum;
    }
  }
  gBenchmarkStartTimeNs = 0;
}

void StartBenchmarkTiming(uint64_t NanoTime) {
  if (gBenchmarkStartTimeNs == 0) {
    gBenchmarkStartTimeNs = NanoTime;
  }
}

int main(int argc, char* argv[]) {
  if (gBenchmarks->empty()) {
    fprintf(stderr, "No benchmarks registered!\n");
    exit(EXIT_FAILURE);
  }

  bool need_header = true;
  for (auto b : *gBenchmarks) {
    if (ShouldRun(b, argc, argv)) {
      if (need_header) {
        printf("%-25s %10s %10s\n", "", "iterations", "ns/op");
        fflush(stdout);
        need_header = false;
      }
      Run(b);
    }
  }

  if (need_header) {
    fprintf(stderr, "No matching benchmarks!\n");
    fprintf(stderr, "Available benchmarks:\n");
    for (auto b : *gBenchmarks) {
      fprintf(stderr, "  %s\n", b->Name());
    }
    exit(EXIT_FAILURE);
  }

  return 0;
}
