#!/usr/bin/env python3

from math import sqrt
from sys import argv, exit, stdout

def usage():
  stdout.write(
'''\
usage: avgiter.py <file> [columns]
  where <file> contains a matrix of integers
  the first column contains the number of the refinement iteration
  the other columns contain the number of queries solved *so far*
    e.g., column 2 might have proved queries, and column 3 impossible ones
''')
  exit(1)

def main():
  fn = None
  cs = None
  try:
    fn = argv[1]
    cs = [int(x) for x in argv[2:]]
  except (IndexError, ValueError):
    usage()
  xs = []
  ys = {c : [0] for c in cs}
  with open(fn, 'r') as f:
    for line in f:
      vs = [int(x) for x in line.split()]
      xs.append(vs[0])
      for c in cs:
        ys[c].append(vs[c])
  stdout.write(fn)
  n = len(xs)
  for c in cs:
    vs = [ys[c][i+1] - ys[c][i] for i in range(n)]
    cnt = sum(vs)
    if cnt == 0:
      stdout.write(' none_solved')
      continue
    avg = sum(vs[i] * xs[i] for i in range(n)) / cnt
    stdout.write(' {:.1f}'.format(avg))
    if cnt > 1:
      dev = sum(vs[i] * (xs[i] - avg) * (xs[i] - avg) for i in range(n))
      dev = sqrt(dev/(cnt-1))
      stdout.write('±{:.1f}'.format(dev))
  stdout.write('\n')

if __name__ == '__main__':
  main()
