#!/usr/bin/env python
#===- lib/asan/scripts/asan_symbolize.py -----------------------------------===#
#
#                     The LLVM Compiler Infrastructure
#
# This file is distributed under the University of Illinois Open Source
# License. See LICENSE.TXT for details.
#
#===------------------------------------------------------------------------===#
import glob
import os
import re
import sys
import string
import subprocess

pipes = {}
load_addresses = {}
next_inline_frameno = 0

def patch_address(frameno, addr_s):
  ''' Subtracts 1 or 2 from the top frame's address.
  Top frame is normally the return address from asan_report*
  call, which is not expected to return at all. Because of that, this
  address often belongs to the next source code line, or even to a different
  function. '''
  if frameno == '0':
    addr = int(addr_s, 16)
    if os.uname()[4].startswith('arm'):
      # Cancel the Thumb bit
      addr = addr & (~1)
    addr -= 1
    return hex(addr)
  return addr_s

def android_get_load_address(path):
  if load_addresses.has_key(path):
    return load_addresses[path]
  readelf_glob = os.path.join(os.environ['ANDROID_TOOLCHAIN'], '*-readelf')
  readelf = glob.glob(readelf_glob)[0]
  readelf_pipe = subprocess.Popen([readelf, "-l", path], stdin=subprocess.PIPE, stdout=subprocess.PIPE)
  for line in readelf_pipe.stdout:
      if ('LOAD' in line) and (' E ' in line):
          match = re.match(r'\s*LOAD\s+0x[01-9a-zA-Z]+\s+(0x[01-9a-zA-Z]+)', line, re.UNICODE)
          if match:
              load_addr = int(match.group(1), 16)
              load_addresses[path] = load_addr
              return load_addr
          else: break
  print 'Could not make sense of readelf output!'
  sys.exit(1)

def postprocess_file_name(file_name, paths_to_cut):
  for path_to_cut in paths_to_cut:
    file_name = re.sub(".*" + path_to_cut, "", file_name)
  file_name = re.sub(".*asan_[a-z_]*.(cc|h):[0-9]*", "[asan_rtl]", file_name)
  file_name = re.sub(".*crtstuff.c:0", "???:0", file_name)
  return file_name

# TODO(glider): need some refactoring here
def symbolize_addr2line(line, binary_prefix, paths_to_cut):
  global next_inline_frameno
  # Strip the log prefix ("I/asanwrapper( 1196): ").
  line = re.sub(r'^.*?: ', '', line)
  #0 0x7f6e35cf2e45  (/blah/foo.so+0x11fe45)
  match = re.match(r'^(\s*#)([0-9]+) *(0x[0-9a-f]+) *\((.*)\+(0x[0-9a-f]+)\)', line, re.UNICODE)
  if match:
    frameno = match.group(2)
    binary = match.group(4)
    addr = match.group(5)
    addr = patch_address(frameno, addr)

    if binary.startswith('/'):
      binary = binary[1:]
    binary = os.path.join(binary_prefix, binary)

    if not os.path.exists(binary):
      print line.rstrip().encode('utf-8')
      return

    load_addr = android_get_load_address(binary)
    addr = hex(int(addr, 16) + load_addr)

    if not pipes.has_key(binary):
      pipes[binary] = subprocess.Popen(["addr2line", "-i", "-f", "-e", binary],
                         stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    p = pipes[binary]
    frames = []
    try:
      print >>p.stdin, addr
      # This will trigger a "??" response from addr2line so we know when to stop
      print >>p.stdin
      while True:
        function_name = p.stdout.readline().rstrip()
        file_name     = p.stdout.readline().rstrip()
        if function_name in ['??', '']:
          break
        file_name = postprocess_file_name(file_name, paths_to_cut)
        frames.append((function_name, file_name))
    except:
      pass
    if not frames:
      frames.append(('', ''))
      # Consume another pair of "??" lines
      try:
        p.stdout.readline()
        p.stdout.readline()
      except:
        pass
    for frame in frames:
      inline_frameno = next_inline_frameno
      next_inline_frameno += 1
      print "%s%d" % (match.group(1).encode('utf-8'), inline_frameno), \
          match.group(3).encode('utf-8'), "in", frame[0], frame[1]
  else:
    print line.rstrip().encode('utf-8')


binary_prefix = os.path.join(os.environ['ANDROID_PRODUCT_OUT'], 'symbols')
paths_to_cut = [os.getcwd() + '/', os.environ['ANDROID_BUILD_TOP'] + '/'] + sys.argv[1:]

for line in sys.stdin:
  line = line.decode('utf-8')
  symbolize_addr2line(line, binary_prefix, paths_to_cut)
