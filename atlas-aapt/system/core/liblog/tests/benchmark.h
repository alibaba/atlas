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

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <vector>

#ifndef BIONIC_BENCHMARK_H_
#define BIONIC_BENCHMARK_H_

namespace testing {

class Benchmark;
template <typename T> class BenchmarkWantsArg;
template <typename T> class BenchmarkWithArg;

void BenchmarkRegister(Benchmark* bm);
int PrettyPrintInt(char* str, int len, unsigned int arg);

class Benchmark {
 public:
  Benchmark(const char* name, void (*fn)(int)) : name_(strdup(name)), fn_(fn) {
    BenchmarkRegister(this);
  }
  Benchmark(const char* name) : name_(strdup(name)), fn_(NULL) {}

  virtual ~Benchmark() {
    free(name_);
  }

  const char* Name() { return name_; }
  virtual const char* ArgName() { return NULL; }
  virtual void RunFn(int iterations) { fn_(iterations); }

 protected:
  char* name_;

 private:
  void (*fn_)(int);
};

template <typename T>
class BenchmarkWantsArgBase : public Benchmark {
 public:
  BenchmarkWantsArgBase(const char* name, void (*fn)(int, T)) : Benchmark(name) {
    fn_arg_ = fn;
  }

  BenchmarkWantsArgBase<T>* Arg(const char* arg_name, T arg) {
    BenchmarkRegister(new BenchmarkWithArg<T>(name_, fn_arg_, arg_name, arg));
    return this;
  }

 protected:
  virtual void RunFn(int) { printf("can't run arg benchmark %s without arg\n", Name()); }
  void (*fn_arg_)(int, T);
};

template <typename T>
class BenchmarkWithArg : public BenchmarkWantsArg<T> {
 public:
  BenchmarkWithArg(const char* name, void (*fn)(int, T), const char* arg_name, T arg) :
      BenchmarkWantsArg<T>(name, fn), arg_(arg) {
    arg_name_ = strdup(arg_name);
  }

  virtual ~BenchmarkWithArg() {
    free(arg_name_);
  }

  virtual const char* ArgName() { return arg_name_; }

 protected:
  virtual void RunFn(int iterations) { BenchmarkWantsArg<T>::fn_arg_(iterations, arg_); }

 private:
  T arg_;
  char* arg_name_;
};

template <typename T>
class BenchmarkWantsArg : public BenchmarkWantsArgBase<T> {
 public:
  BenchmarkWantsArg<T>(const char* name, void (*fn)(int, T)) :
    BenchmarkWantsArgBase<T>(name, fn) { }
};

template <>
class BenchmarkWantsArg<int> : public BenchmarkWantsArgBase<int> {
 public:
  BenchmarkWantsArg<int>(const char* name, void (*fn)(int, int)) :
    BenchmarkWantsArgBase<int>(name, fn) { }

  BenchmarkWantsArg<int>* Arg(int arg) {
    char arg_name[100];
    PrettyPrintInt(arg_name, sizeof(arg_name), arg);
    BenchmarkRegister(new BenchmarkWithArg<int>(name_, fn_arg_, arg_name, arg));
    return this;
  }
};

static inline Benchmark* BenchmarkFactory(const char* name, void (*fn)(int)) {
  return new Benchmark(name, fn);
}

template <typename T>
static inline BenchmarkWantsArg<T>* BenchmarkFactory(const char* name, void (*fn)(int, T)) {
  return new BenchmarkWantsArg<T>(name, fn);
}

}  // namespace testing

template <typename T>
static inline void BenchmarkAddArg(::testing::Benchmark* b, const char* name, T arg) {
  ::testing::BenchmarkWantsArg<T>* ba;
  ba = static_cast< ::testing::BenchmarkWantsArg<T>* >(b);
  ba->Arg(name, arg);
}

void SetBenchmarkBytesProcessed(uint64_t);
void ResetBenchmarkTiming(void);
void StopBenchmarkTiming(void);
void StartBenchmarkTiming(void);
void StartBenchmarkTiming(uint64_t);
void StopBenchmarkTiming(uint64_t);

#define BENCHMARK(f) \
    static ::testing::Benchmark* _benchmark_##f __attribute__((unused)) = \
        (::testing::Benchmark*)::testing::BenchmarkFactory(#f, f)

#endif // BIONIC_BENCHMARK_H_
