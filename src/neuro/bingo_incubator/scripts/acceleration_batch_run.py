#!/usr/bin/env python3

from argparse import ArgumentParser, RawDescriptionHelpFormatter
from glob import glob
from random import shuffle
from shutil import rmtree
from sys import stderr, stdout
from subprocess import call, TimeoutExpired
from time import time

import os
import os.path
import re

argparser = ArgumentParser(description='''\
  Runs several clients on several projects, and saves logs.
''', formatter_class=RawDescriptionHelpFormatter)

def posint(s):
  r = int(s)
  if not (0 < r):
    raise ValueError
  return r

argparser.add_argument('-make-script', action='store_true',
  help='make script mode')
argparser.add_argument('-old', action='store_true',
  help='run analysis in the old mode')
argparser.add_argument('-clients',
  default='clients.txt',
  help='clients to run (used only if -make-script)')
argparser.add_argument('-benchmarks',
  default='benchmarks.txt',
  help='benchmarks to run (used only if -make-script)')
argparser.add_argument('-maxq', type=int,
  default=10,
  help='queries per (client, benchmark) pair (used only if -make-script)')
argparser.add_argument('-script',
  default='script.txt',
  help='execution script (created if -make-script; used otherwise)')
argparser.add_argument('-model',
  default='model.txt',
  help='probability model (not used if -old or -make-script)')
argparser.add_argument('-timeout', type=posint,
  default=900,
  help='timeout in seconds (not strict, though)')
argparser.add_argument('-outdir',
  default='logs_{:x}'.format(int(time()) % (1 << 32)),
  help='directory where output is saved (not used when -make-script)')
argparser.add_argument('-no-provenance', action='store_true',
  help='do not save provenances')

incubator_dir = os.environ['CHORD_INCUBATOR']
bench_dir = os.environ['PJBENCH']
temp_outdir = os.path.join(os.getcwd(), 'chord_out_tmp')

chord_settings = \
  { 'chord.experiment.boolDomain' : 'true'
  , 'chord.experiment.model.ruleProbability.scale' : 85
  , 'chord.experiment.saveGlobalProvenance' : 'true'
  , 'chord.experiment.solver.debug' : 'false'
  , 'chord.experiment.solvers' : 'chord.analyses.experiment.solver.Mifumax'
  , 'chord.out.dir' : temp_outdir }

chord_settings_delta = \
  [ { 'chord.experiment.likelyPossible' : 'true'
    , 'chord.experiment.model.class' : 'chord.analyses.experiment.classifier.DefaultModel' }
  , { 'chord.experiment.likelyPossible' : 'false'
    , 'chord.experiment.model.class' : 'chord.analyses.experiment.classifier.RuleProbabilityModel' } ]

provenance_count = 0

def run_chord(benchmark, settings, timeout):
  stdout.write('RUN_CHORD {} {}\n'.format(benchmark, settings))
  cmd = \
    [ os.path.join(incubator_dir, 'runner.pl')
    , '-foreground'
    , '-program={}'.format(benchmark)
    , '-analysis=experiment'
    , '-mode=serial' ]
  for k, v in settings.items():
    cmd += ['-D', '{}={}'.format(k, v)]
  stdout.flush()
  try:
    call(cmd, timeout=timeout+400)
  except TimeoutExpired:
    stdout.write('TIMEOUT {}\n'.format(timeout + 400))

def get_queries(client, benchmark, timeout):
  stdout.write('GET_QUERIES {} {}\n'.format(client, benchmark))
  settings = chord_settings.copy()
  settings.update(
    { 'chord.experiment.onlyReportQueries' : 'true'
    , 'chord.experiment.client' : client })
  run_chord(benchmark, settings, timeout)
  qs = []
  with open(os.path.join(temp_outdir, 'log.txt'), 'r') as f:
    for line in f:
      if line.startswith('MAIN: QUERIES'):
        qs = line.split()[2:]
        break
  stdout.write('GOT_QUERIES {} {}\n'.format(len(qs), ' '.join(qs)))
  rmtree(temp_outdir, ignore_errors=True)
  return qs

def tidy(s):
  return re.sub('[^a-zA-Z0-9]','_',s)

def save_logs(benchmark, client, query, outdir):
  global provenance_count
  stdout.write('SAVE_LOGS {} {} {} {}\n'.format(benchmark, client, query, outdir))
  log_src = os.path.join(temp_outdir, 'log.txt')
  log_tgt = os.path.join(outdir, tidy('log-{}-{}-{}'.format(benchmark, client, query)))
  try:
    os.rename(log_src, log_tgt)
  except Exception:
    stdout.write('SAVE_LOGS FAILED log.txt\n')
  for p in glob(os.path.join(temp_outdir, 'provenance*')):
    try:
      os.rename(p, os.path.join(outdir, 'provenance_{}'.format(provenance_count)))
      provenance_count += 1
    except Exception as e:
      stdout.write('SAVE_LOGS FAILED {} {}\n'.format(p, e))


def process(client, benchmark, query, old, outdir, model, timeout):
  stdout.write('PROCESS {} {} {} {} {} {} {}\n'.format(
    client, benchmark, query, old, outdir, model, timeout))
  s = chord_settings.copy()
  s.update(chord_settings_delta[0 if old else 1])
  s['chord.experiment.client'] = client
  if not old:
    s['chord.experiment.model.loadFile'] = model
  s['chord.experiment.query'] = query
  run_chord(benchmark, s, timeout)
  save_logs(benchmark, client, query, outdir)
  rmtree(temp_outdir, ignore_errors=True)


def make_script(clients_file, benchmarks_file, q_count, script_file, timeout):
  stdout.write('MAKE_SCRIPT {} {} {} {} {}\n'.format(
    clients_file, benchmarks_file, q_count, script_file, timeout))
  with open(clients_file, 'r') as f:
    clients = [x.strip() for x in f.readlines()]
  stdout.write('CLIENTS {}\n'.format(' '.join(clients)))
  with open(benchmarks_file, 'r') as f:
    benchmarks = [x.strip() for x in f.readlines()]
  stdout.write('BENCHMARKS {}\n'.format(' '.join(benchmarks)))
  with open(script_file, 'w') as out:
    for c in clients:
      for b in benchmarks:
        queries = get_queries(c, b, timeout)
        shuffle(queries)
        queries = queries[:q_count]
        stdout.write('KEPT_QUERIES {}\n'.format(' '.join(queries)))
        out.write('{} {} {}\n'.format(c, b, ' '.join(queries)))


def run_script(script, timeout, model, old, outdir):
  chord_settings['chord.experiment.timeout'] = \
      chord_settings['chord.experiment.solver.timeout'] = \
      str(timeout)
  if not old:
    model = os.path.join(os.getcwd(), model)
    if not os.path.exists(model):
      stderr.write('E: cannot find {}\n'.format(model))
      return
  os.mkdir(outdir)
  with open(script, 'r') as f:
    for line in f:
      ws = line.split()
      client = ws[0]
      benchmark = ws[1]
      for query in ws[2:]:
        try:
          process(client, benchmark, query, old, outdir, model, timeout)
        except Exception as e:
          stdout.write('EXCEPTION {} {} {} {}\n'.format(
            client, benchmark, query, e))

def main():
  args = argparser.parse_args()
  if args.no_provenance:
    chord_settings['chord.experiment.saveGlobalProvenance'] = 'false'
  if args.make_script:
    make_script(args.clients, args.benchmarks, args.maxq, args.script, args.timeout)
  else:
    run_script(args.script, args.timeout, args.model, args.old, args.outdir)

if __name__ == '__main__':
  main()
