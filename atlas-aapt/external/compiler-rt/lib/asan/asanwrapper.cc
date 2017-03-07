#include <elf.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <string>

void usage(const char* me) {
  static const char* usage_s = "Usage:\n"
    "  %s /system/bin/app_process <args>\n"
    "or, better:\n"
    "  setprop wrap.<nicename> %s\n";
  fprintf(stderr, usage_s, me, me);
  exit(1);
}

void env_prepend(const char* name, const char* value, const char* delim) {
  const char* value_old = getenv(name);
  std::string value_new = value;
  if (value_old) {
    value_new += delim;
    value_new += value_old;
  }
  setenv(name, value_new.c_str(), 1);
}

bool elf_is_64bit(const char* path) {
  int fd = open(path, O_RDONLY);
  if (fd == -1) return false;

  const size_t kBufSize = EI_CLASS + 1;
  char buf[kBufSize];
  ssize_t sz = read(fd, buf, kBufSize);
  if (sz != kBufSize) {
    close(fd);
    return false;
  }

  if (buf[EI_MAG0] != ELFMAG0 || buf[EI_MAG1] != ELFMAG1 ||
      buf[EI_MAG2] != ELFMAG2 || buf[EI_MAG3] != ELFMAG3)
    return false;

  bool is_64bit = buf[EI_CLASS] == ELFCLASS64;
  close(fd);
  return is_64bit;
}

int main(int argc, char** argv) {
  if (argc < 2) {
    usage(argv[0]);
  }
  char** args = new char*[argc];
  // If we are wrapping app_process, replace it with app_process_asan.
  // TODO(eugenis): rewrite to <dirname>/asan/<basename>, if exists?
  if (strcmp(argv[1], "/system/bin/app_process") == 0) {
    args[0] = (char*)"/system/bin/asan/app_process";
  } else if (strcmp(argv[1], "/system/bin/app_process32") == 0) {
    args[0] = (char*)"/system/bin/asan/app_process32";
  } else if (strcmp(argv[1], "/system/bin/app_process64") == 0) {
    args[0] = (char*)"/system/bin/asan/app_process64";
  } else {
    args[0] = argv[1];
  }

  for (int i = 1; i < argc - 1; ++i)
    args[i] = argv[i + 1];
  args[argc - 1] = 0;

  if (elf_is_64bit(args[0])) {
    env_prepend("LD_LIBRARY_PATH", "/system/lib64/asan:/system/lib64", ":");
  } else {
    env_prepend("LD_LIBRARY_PATH", "/system/lib/asan:/system/lib", ":");
  }
  env_prepend("ASAN_OPTIONS", "allow_user_segv_handler=1", ",");

  printf("ASAN_OPTIONS: %s\n", getenv("ASAN_OPTIONS"));
  printf("LD_LIBRARY_PATH: %s\n", getenv("LD_LIBRARY_PATH"));
  for (int i = 0; i < argc - 1; ++i)
    printf("argv[%d] = %s\n", i, args[i]);

  execv(args[0], args);
}
