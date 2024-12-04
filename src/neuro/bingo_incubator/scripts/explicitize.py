#!/usr/bin/env python3

from sys import argv, exit, stdout

def usage():
  stdout.write('''\
usage: explicitize.py cnf_of_wcnf tuple_map

  Example: Suppose a cnf is unsat when it shouldn't. Then you could use a tool
  like muser to extract a minimum unsat core. Then, you'd want to read the
  result in terms of tuple names, not numbers. This script lets you translate
  back to names.

  The tuple_map file is supposed to have lines in the format
    10 CVC(10,20) bla-bla
  The bla-bla is ignored; 10 is replaced by CVC(10,20).

''')
  exit(1)

def main():
  if len(argv) != 3:
    usage()
  input_file_name = argv[1]
  tuple_map_file_name = argv[2]
  name = {}
  def get_name(x):
    if x not in name:
      return 'Aux{}'.format(x)
    else:
      return name[x]
  with open(tuple_map_file_name, 'r') as f:
    for line in f:
      ws = line.split()
      name[int(ws[0])] = ws[1]
  wcnf, top_weight = None, None
  with open(input_file_name, 'r') as f:
    for line in f:
      ws = line.split()
      if ws[:2] == ['p', 'cnf']:
        wcnf = False
        top_weight = None
      elif ws[:2] == ['p', 'wcnf']:
        wcnf = True
        top_weight = int(ws[4])
      else:
        assert (wcnf != None)
        assert (wcnf or top_weight == None)
        if wcnf:
          is_hard = int(ws[0]) == top_weight
          ws = ws[1:]
        xs = [int(w) for w in ws]
        ps = [get_name(x) for x in xs if x > 0]
        ns = [get_name(-x) for x in xs if x < 0]
        if wcnf:
          stdout.write('hard ' if is_hard else 'soft ')
        stdout.write('{} <- {}\n'.format('|'.join(ps), '&'.join(ns)))

if __name__ == '__main__':
  main()
