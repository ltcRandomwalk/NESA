#!/usr/bin/env python3
from os import listdir, mkdir
from re import compile, match
from sys import stdout

fn_re = compile('refine_[0-9]+_(.*)\\.explicit')
invalid_re = compile('[^0-9a-zA-Z]')

def main():
  h = {}
  for fn in listdir():
    m = fn_re.match(fn)
    if not m:
      continue
    c = invalid_re.sub('', m.group(1))
    if c not in h:
      h[c] = []
    h[c].append(fn)
  for xs in h.values():
    xs.sort()
  stdout.write('rm -rf pv\nmkdir pv\n')
  for c, xs in h.items():
    stdout.write('rm -rf pv/{}\nmkdir pv/{}\n'.format(c,c))
    i = 0
    for x in xs:
      stdout.write("to_pv.py < '{}' > pv/{}/{:03}.pv\n".format(x, c, i))
      i += 1

if __name__ == '__main__':
  main()
