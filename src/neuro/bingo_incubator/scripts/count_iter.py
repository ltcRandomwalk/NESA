#!/usr/bin/env python3

from re import compile
from sys import stderr, stdin, stdout

start_re = compile('test (.*) \\(start')
stop_re = compile('test (.*) stop\\)')
iter_re = compile('runAnalysis')

def main():
  state = 0 # 0 = in,  1 = out
  count = 0
  query = '<OOPS>'
  for line in stdin:
    if not line.startswith('MAIN'):
      continue
    if state == 0:
      m = start_re.search(line)
      if m:
        query = m.group(1)
        count = 0
        state = 1
    else: # state == 1
      if iter_re.search(line):
        count += 1
      m = stop_re.search(line)
      if m:
        if m.group(1) != query:
          stderr.write('E: wat? started testing {} and finished testing {}?\n'.format(query, m.group(1)))
        else:
          stdout.write('{:3} {}\n'.format(count, query))
        state = 0

if __name__ == '__main__':
  main()
