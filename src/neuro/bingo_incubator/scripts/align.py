#!/usr/bin/env python3

from sys import stdin, stdout

def p(s, w):
  stdout.write(s)
  stdout.write(' ' * (w - len(s)))

def main():
  data = [x.split() for x in stdin]
  ws = [0] * max(len(x) for x in data)
  for xs in data:
    for i in range(len(xs)):
      ws[i] = max(ws[i], len(xs[i]))
  for xs in data:
    if xs != []:
      p(xs[0], ws[0])
      for i in range(1, len(xs)-1):
        stdout.write(' ')
        p(xs[i], ws[i])
      if len(xs) > 1:
        stdout.write(' ')
        stdout.write(xs[-1])
    stdout.write('\n')

if __name__ == '__main__':
  main()
