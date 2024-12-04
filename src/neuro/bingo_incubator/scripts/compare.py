#!/usr/bin/env python3

from subprocess import call
from sys import argv, stdout

import os
import os.path
import re

runner_cmd = ['./runner.pl', '-foreground']

runner_settings = \
  { 'program' : 'hedc'
  , 'analysis' : 'experiment'
  , 'mode' : 'serial' }

chord_settings = \
  { 'chord.experiment.boolDomain' : 'true'
  , 'chord.experiment.client' : 'downcast'
  , 'chord.experiment.mono' : 'true'
  , 'chord.experiment.solver.debug' : 'true'
  , 'chord.experiment.solvers' : 'chord.analyses.experiment.solver.Mifumax' }

delta_settings = \
  [ { 'chord.experiment.likelyPossible' : 'false' }
  , { 'chord.experiment.likelyPossible' : 'true' } ]

result_dir = '../pjbench-read-only/hedc/chord_output_experiment/'
archive_dir = 'tmp'

def run(settings):
  cmd = [c for c in runner_cmd]
  for k, v in runner_settings.items():
    cmd += ['-{}={}'.format(k, v)]
  for k, v in settings.items():
    cmd += ['-D', '{}={}'.format(k, v)]
  call(cmd)

def save(d):
  os.rename(result_dir, os.path.join(archive_dir, d))

def tidy(s):
  return re.sub('[^a-zA-Z0-9]','_',s)

def main():
  with open(argv[1], 'r') as qs_file:
    for q in qs_file:
      q = q.strip()
      stdout.write('processing {}\n'.format(q))
      for i in range(len(delta_settings)):
        s = chord_settings.copy()
        s.update(delta_settings[i])
        s['chord.experiment.query'] = q
        run(s)
        save(tidy('{}_{}'.format(q, i)))

if __name__ == '__main__':
  main()
