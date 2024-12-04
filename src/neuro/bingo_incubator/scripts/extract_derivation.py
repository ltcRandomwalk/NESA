#!/usr/bin/env python3

from argparse import ArgumentParser, RawDescriptionHelpFormatter
from sys import argv, stderr, stdout

import re

opt_parser = ArgumentParser(
  formatter_class=RawDescriptionHelpFormatter,
  description='''\
  The derivation_file should have lines of the form
    x <- y&z
  meaning that x can be derived from y and z. All rules *must* have a head.

  If the in input_regexp is mentioned, then a rule "x <-" is introduced for
  all x that satisfy the regular expression.

  NOTE: As the other scripts in this directory, this one doesn't do input
  validation. Beware in particular that head-less rules like "<- x" are
  interpreted as "x <-".
''')
opt_parser.add_argument('derivation_file')
opt_parser.add_argument('query')
opt_parser.add_argument('input_regexp', nargs='?')

# Implementation:
# 1.  Prune rules using (stupid) unit propagation. After this phase, each atom
#     is justified in terms of other atoms that are closer to the leaves.
# 2.  Do backward reachability from the query.

def add_implicit_leaves(G, pat):
  if pat == None:
    pat = ' '
  pat = re.compile(pat)
  zs = set()
  for y, xss in G.items():
    zs.add(y)
    for xs in xss:
      for x in xs:
        zs.add(x)
  stderr.write('IMPLICIT LEAVES:')
  H = G.copy()
  for z in zs:
    ys = H.setdefault(z, [])
    if pat.match(z):
      stderr.write(' {}'.format(z))
      ys.append([])
  stderr.write('\n')
  return H

def prune_by_leaves_distance(G):
  implications = [(y,xs) for y, xss in G.items() for xs in xss]
  watch = { y : [] for y in G.keys() }
  nxt = []
  for i in range(len(implications)):
    y, xs = implications[i]
    if xs == []:
      nxt.append(i)
    else:
      watch[xs[0]].append(i)
  c1 = c2 = c3 = c4 = c5 = 0
  H = {}
  try:
    while nxt != []:
      c1 += 1
      now, nxt = nxt, []
      cs = [implications[i] for i in now]
      ys = set(y for y, _ in cs)
      for y in ys:
        H[y] = []
      for y, xs in cs:
        H[y].append(xs)
      for y in ys:
        c2 += 1
        for i in watch[y]:
          c3 += 1
          v, us = implications[i]
          if v in H:
            continue
          c4 += 1
          ws = [u for u in us if u not in H]
          c5 += len(ws)
          if ws == []:
            nxt.append(i)
          else:
            watch[ws[0]].append(i)
  except KeyboardInterrupt:
    pass
  #PROF print('c1={} c2={} c3={} c4={} c5={}'.format(c1,c2,c3,c4,c5))
  return H

def prune_by_reachability(G, query):
  if query not in G:
    stderr.write('query {} not derived\n'.format(query))
    exit(1)
  H = {}
  todo = set([query])
  while len(todo) != 0:
    y = todo.pop()
    H[y] = G[y]
    for xs in G[y]:
      for x in xs:
        if x not in H:
          todo.add(x)
  return H

def main():
  args = opt_parser.parse_args()
  G = {}
  with open(args.derivation_file, 'r') as df:
    for line in df:
      line = re.sub(' *(<-|&) *', ' ', line)
      ws = line.split()
      y = ws[0]
      xs = ws[1:]
      xss = G.setdefault(y, [])
      xss.append(xs)
  G = add_implicit_leaves(G, args.input_regexp)
  H = prune_by_leaves_distance(G)
  H = prune_by_reachability(H, args.query)
  for y, xss in H.items():
    stdout.write('{} <- {}\n'.format(y, '&'.join(xss[0])))

if __name__ == '__main__':
  main()
