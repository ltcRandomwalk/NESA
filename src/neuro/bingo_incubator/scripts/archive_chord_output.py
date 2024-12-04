#!/usr/bin/env python3

from datetime import datetime
from os import curdir, listdir, mkdir, pardir, rename
from os.path import basename, dirname, isdir, join
from re import compile
from sys import stdout

output_regex = compile('chord_output')
archive_prefix = 'chord_archive'

archive_regex = compile(archive_prefix)

def backup_dir():
  d = '{}_{}'.format(archive_prefix, datetime.utcnow().strftime('%Y%m%d'))
  n = 1
  while True:
    r = '{}_{}'.format(d, n)
    if not isdir(r):
      return r
    n += 1

def is_output(f):
  return output_regex.match(f) != None

def is_archive(f):
  return archive_regex.match(f) != None

def find_outputs(base = '.'):
  rs = []
  for f in listdir(base):
    bf = join(base, f)
    if is_output(f):
      rs.append(bf)
    elif isdir(bf) and not is_archive(f):
      rs += find_outputs(bf)
  return rs

def move(s, t):
  test = basename(dirname(s))
  if test in ['', curdir, pardir]:
    test = 'anonymous'
  if isdir(join(t, test)):
    n = 1
    while isdir(join(t, '{}-{}'.format(test, n))):
      n += 1
    test = '{}-{}'.format(test, n)
  t = join(t, test)
  mkdir(t)
  t = join(t, basename(s))
  rename(s, t)

def main():
  t = backup_dir()
  stdout.write("finding chord's outputs\n")
  ss = find_outputs()
  if ss == []:
    stdout.write('none found\n')
  else:
    stdout.write('moving them to {} '.format(t))
    stdout.flush()
    mkdir(t)
    for s in ss:
      move(s, t)
      stdout.write('.')
      stdout.flush()
    stdout.write('\n')

if __name__ == '__main__':
  main()
