#!/usr/bin/env python3

from re import compile
from sys import stdin, stdout

res = [
  compile(r'MAIN.*iteration[^0-9]+([0-9]+)'),
  compile(r'MAIN.*unresolved[^0-9]+([0-9]+)'),
  compile(r'MAIN.*impossible[^0-9]+([0-9]+)'),
  compile(r'MAIN.*ruled out[^0-9]+([0-9]+)'),
  compile(r'MAIN.*difficult[^0-9]+([0-9]+)') ]


def main():
  vs = [None] * len(res)
  for line in stdin:
    for i in range(len(res)):
      m = res[i].match(line)
      if m:
        vs[i] = m.group(1)
        break
    if None not in vs:
      for v in vs:
        stdout.write('{} '.format(v))
      stdout.write('\n')
      vs = [None] * len(res)

if __name__ == '__main__':
  main()
