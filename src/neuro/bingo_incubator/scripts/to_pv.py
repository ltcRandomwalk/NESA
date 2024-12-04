#!/usr/bin/env python3

from sys import exit, stderr, stdin, stdout

def main():
  for line in stdin:
    ws = line.split()
    if ws[0] == 'AbsCost':
      assert (len(ws) == 6) # e.g.: "AbsCost weight 1 : DenyO(953,1) <-"
      stdout.write('Input: {}\n'.format(ws[4]))
    elif ws[0] == 'Query':
      assert (len(ws) == 6) # e.g.: "Query weight 2587 :  <- polySite(815)"
      stdout.write('Output: {}\n'.format(ws[5]))
    elif len(ws) == 7: # e.g.: "Derivation weight 2587 : COC_2(0,3570,0) <- COC_1(0,3570,3630)&DenyO(3570,1)"
      stdout.write('{} <- {}\n'.format(ws[4], ws[6]))
    elif len(ws) == 6: # e.g.: "Derivation weight 2587 : CM(0,0) <- "
      stdout.write('{}\n'.format(ws[4]))
    else:
      stderr.write('E: {}\n'.format(line))
      exit(1)

if __name__ == '__main__':
  main()
