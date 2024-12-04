#!/usr/bin/env python3
from sys import argv, exit, stderr, stdin, stdout

def read_tuplemap(fn):
  h = {}
  with open(fn,'r') as f:
    for line in f:
      ws = line.split()
      h[ws[1]] = ws[0]
  return h

def main():
  if len(argv) != 3:
    stderr.write('usage: {} <fileA> <fileB>\n'.format(argv[0]))
    exit(1)
  a = read_tuplemap(argv[1])
  b = read_tuplemap(argv[2])
  for ta, ia in a.items():
    if ta not in b:
      stdout.write('- {} old index {}\n'.format(ta, ia))
  for tb, ib in b.items():
    if tb not in a:
      stdout.write('+ {} new index {}\n'.format(tb, ib))


if __name__ == '__main__':
  main()
