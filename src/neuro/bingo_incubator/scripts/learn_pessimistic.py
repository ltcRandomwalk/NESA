#!/usr/bin/env python3
# imports {{{

from argparse import ArgumentParser, RawDescriptionHelpFormatter
from collections import defaultdict, deque
from functools import partial
from itertools import chain
from math import ceil, exp, log, log1p
from random import choice, randrange, sample, seed, shuffle, uniform
from re import compile, escape
from scipy.optimize import basinhopping, minimize

import json
import numpy as np
import sys

# }}}
# command line parsing {{{
def unit(s):
  r = float(s)
  if not (0 < r and r <= 1.0):
    raise ValueError
  return r

def posint(s):
  r = int(s)
  if not (0 < r):
    raise ValueError
  return r

argparser = ArgumentParser(description='''\
  This script inputs a global provenance, and outputs probabilities for rules.
''', formatter_class=RawDescriptionHelpFormatter)
argparser.add_argument('-i', type=compile,
  default=compile(r'(H|O)K$'),
  help='how input tuples look (regexp)')
argparser.add_argument('-o', type=compile,
  default=compile(r'unsafeDowncast|polySite'),
  help='how output tuples look (regexp for prefix)')
argparser.add_argument('-initial_step', type=unit,
  default=0.1,
  help='initial step size (for optimizer hill)')
argparser.add_argument('-a', type=unit,
  default=0.99,
  help='step decrease factor (for optimizer hill)')
argparser.add_argument('-iterations', type=int,
  default=10,
  help='number of iterations (for optimizers hill and coord)')
argparser.add_argument('-samples', type=posint,
  default=10,
  help='how many samples to take from each provenance (default 10)')
argparser.add_argument('-big', type=posint,
  default=10,
  help='which value of the parameters is considered big (default 10)')
argparser.add_argument('-optimizer',
 choices=['hill', 'coord', 'slsqp', 'hopping'],
 default='hill',
 help='which optimizer to use (default: hill)')
argparser.add_argument('-load-samples',
  help='from where to read samples')
argparser.add_argument('-save-samples',
  help='where to save samples')
argparser.add_argument('-dont-optimize', action='store_true',
  help='skip optimization')
argparser.add_argument('-load-parameters',
  help='from where to load parameters')
argparser.add_argument('-dont-evaluate', action='store_true',
  help='skip evaluation')
argparser.add_argument('-seed', type=posint,
  default=101,
  help='random seed')
argparser.add_argument('out',
  help='where to (over!)write the output')
argparser.add_argument('provenances', nargs='*',
  help='from where to read global provenances')

# }}}
# constants {{{
SAFE = True   # DBG
infinity = float('inf')

# }}}
# helpers for handling provenances {{{
def check_provenance(provenance):
  if not SAFE:
    return
  ok = True
  types = { n : args for n, args in provenance['types'] }
  def_vs = set(provenance['vertices'])
  def_rs = set(provenance['rules'])
  def_cs = set(x[0] for x in provenance['contexts'])
  if len(def_vs) != len(provenance['vertices']):
    sys.stderr.write('E: multiply defined vertices\n')
    ok = False
  if len(def_rs) != len(provenance['rules']):
    sys.stderr.write('E: multiply defined rules\n')
    ok = False
  if len(types) != len(provenance['types']):
    sys.stderr.write('E: multiply defined types\n')
    ok = False
  used_vs = set(x for _, x, _ in provenance['arcs'])
  used_vs |= set(y for _, _, ys in provenance['arcs'] for y in ys)
  used_rs = set(r for r, _, _ in provenance['arcs'])
  used_cs = set(x[-1] for x in provenance['contexts'])
  used_cs |= set(x[1][i] for x in def_vs for i in range(len(x[1]))
      if x[0] in types and types[x[0]][i] == 'DomC')
  undef_vs = used_vs - def_vs
  undef_rs = used_rs - def_rs
  undef_cs = used_cs - def_cs
  undef_types = set(x[0] for x in used_vs
      if x[0] not in types)
  bad_arity = set(x for x in used_vs
      if x[0] in types and len(x[1]) != len(types[x[0]]))
  if undef_vs:
    sys.stderr.write('E: undefined vertices: {}\n'.format(undef_vs))
    ok = False
  if undef_rs:
    sys.stderr.write('E: undefined rules: {}\n'.format(undef_rs))
    ok = False
  if undef_cs:
    sys.stderr.write('E: undefined contexts: {}\n'.format(undef_cs))
    ok = False
  if undef_types:
    sys.stderr.write('E: undefined types: {}\n'.format(sorted(list(undef_types))))
    ok = False
  if bad_arity:
    sys.stderr.write('E: mismatched arity: {}\n'.format(sorted(list(bad_arity))))
    ok = False
  for _, _, ys in provenance['arcs']:
    assert type(ys) == tuple
  assert ok # I want to see the stack trace when this fails


def parse_global_provenance(file):
  def tup(t):
    return (t[0], tuple(t[1]))
  provenance = json.load(file)
  provenance['arcs'] = [(r, tup(x), tuple(tup(y) for y in ys))
      for r, x, ys in provenance['arcs']]
  provenance['vertices'] = [tup(x) for x in provenance['vertices']]
  check_provenance(provenance)
  return provenance


def debug_provenance_size(provenance):
  r = {}
  for k, v in provenance.items():
    r[k] = len(v)
  return r


# Returns a list of lists. The inner lists look like
#   [(0, ('H', (100, 0))), (10, ('H', (100, 10)))]
# (This one corresponds to the parameter H100.)
def find_inputs(provenance, in_re):
  inputs = defaultdict(set)
  for _, x, ys in provenance['arcs']:
    if in_re.match(x[0]):
      sys.stderr.write('W: parameter {} appears in the head of a rule\n'.format(x))
    for y in ys:
      m = in_re.match(y[0])
      if m:
        p = (y[0], y[1][0])
        inputs[p].add((y[1][1], y))
  return [sorted(xs) for xs in inputs.values()]


# Returns a list of tuples that match out_re and also appear in the head of
# some arc of provenance.
def find_outputs(provenance, out_re):
  outputs = set()
  for _, x, _ in provenance['arcs']:
    m = out_re.match(x[0])
    if m:
      outputs.add(x)
  return sorted(outputs)
# }}}
# reachability in provenances {{{

def re_match(re):
  return (lambda x : re.match(x[0]))


# Keeps those arcs that are on input--output walks.
# If only_forward, then keeps only arcs (x <- ys) such that d(x) > max(d(y)),
# where d denotes distance from inputs.
# If sample, then for each vertex x it keeps at most one arc (x <- ...),
# chosen at random.
def keep_relevant(
    provenance, inputs, only_forward=False, sample=False, backward_prune=None):

  # To identify forward arcs, we must porcess vertices in increasing order of
  # distance from inputs, which is incompatible with sampling a random order.
  assert not only_forward or not sample

  # forward reachability
  watch = { y : [] for y in provenance['vertices'] }
  todo = list(inputs)
  justified = set(todo)
  new_arcs = {}
  for arc in provenance['arcs']:
    r, x, ys = arc
    if len(ys) == 0:
      if x not in justified:
        justified.add(x)
        todo.append(x)
      new_arcs.setdefault(x, []).append((r, tuple()))
    else:
      watch[ys[0]].append(arc)
  while todo:
    # DBG print('len(todo)',len(todo))
    if sample:
      to_pick = randrange(len(todo))
    else:
      to_pick = 0
    y = todo[to_pick]
    todo = todo[:to_pick] + todo[to_pick+1:]
    if y not in watch:
      continue
    for arc in watch[y]:
      r, x, ys = arc
      if x in justified and only_forward:
        continue # not a forward arc
      zs = [z for z in ys if z not in justified]
      if zs == []:
        if x not in justified or not sample:
          new_arcs.setdefault(x, []).append((r, ys))
        if x not in justified:
          justified.add(x)
          todo.append(x)
      else:
        watch[zs[0]].append(arc)

  # backward reachability
  if backward_prune is not None:
    leaves = set(inputs)
    todo = deque(x for x in justified if backward_prune(x))
    new_vertices = set(todo)
    while todo:
      x = todo.popleft()
      if x in leaves:
        continue
      for _, ys in new_arcs[x]:
        for y in ys:
          if y not in new_vertices:
            todo.append(y)
            new_vertices.add(y)
  else:
    new_vertices = set(new_arcs.keys()) | set(inputs)

  # make new provenance
  result = {}
  result['arcs'] = []
  for x, yss in new_arcs.items():
    if x not in new_vertices:
      continue
    for r, ys in yss:
      if any(y not in new_vertices for y in ys):
        continue
      result['arcs'].append((r, x, ys))
  result['rules'] = list(set(r for r, _, _ in result['arcs']))
  new_vertices = set()
  for _, x, ys in result['arcs']:
    new_vertices.add(x)
    for y in ys:
      new_vertices.add(y)
  result['vertices'] = list(new_vertices)
  used_names = set(x[0] for x in new_vertices)
  result['types'] = [t for t in provenance['types'] if t[0] in used_names]
  result['contexts'] = provenance['contexts']

  check_provenance(result)
  return result

# }}}
# sample provenances {{{
# From the global provenance it produces
#   (cheap_provenance, precise_provenance, projection)
# The inputs and outputs of global_provenance are identified using in_re and
# out_re. Only those inputs that have been tried with values both <big and
# >=big are kept in the local provenances: the cheap_provenance keeps the
# smallest value; the precise_provenance keeps the largest value. Local
# provenances, unlike global provenaces, have two extra fields: 'inputs' and
# 'outputs'.
#
# The projection is defined in terms of a relation P between vertices x in the
# cheap provenance and vertices y in the precise provenance. We have xPy when
#   - both x and y are on some path (in their respective provenances) from
#     inputs to outputs; the inputs are defined here as abstraction tuples;
#     the outputs are defined here as queries OR "Deny" tuples
#   - the names of x and y come from a given relation
#     (this relation is the identity most of the time; see name_map below)
#   - x and y have the same types, which implies the same number of arguments
#   - x and y have the same name and the same number of arguments
#   - for each pair (x[i], y[i]) of corresponding arguments,
#     - if the domain is DomK, then x[i] <= y[i]
#     - if the domain is DomC, then y[i] projects on x[i] according to the
#       (explicit) projection relation on contexts, in one or more steps
#     - otherwise, x[i] == y[i]
# The projection is P, represented as a mapping from vertices in the precise
# provenance to lists of vertices in the cheap provenance. The projection
# computed like as described above is often a function, but this isn't
# guaranteed.
def locals_of_global(global_provenance, big, in_re, out_re):
  is_out_or_deny = re_match(compile('({})|Deny'.format(out_re.pattern)))
  types = dict(global_provenance['types'])
  context_projection = { c[0] : c[1] for c in global_provenance['contexts']
      if len(c) == 2 }
  def get_local_inputs():
    inputs = [ts for ts in find_inputs(global_provenance, in_re)
        if ts[0][0] < big and ts[-1][0] >= big]
    return \
        { 'cheap' : [ts[0][1] for ts in inputs]
        , 'precise' : [ts[-1][1] for ts in inputs] }
  def get_slice(inputs):
    local = keep_relevant(global_provenance, inputs,
        backward_prune=is_out_or_deny)
    local['inputs'] = [x[0][1] for x in find_inputs(local, in_re)]
    local['outputs'] = list(find_outputs(local, out_re))
    assert not SAFE or set(local['inputs']) <= set(inputs)
    return local
  def get_projection(cheap_vertices, precise_vertices):
    def bucket_vertices(xs):
      def b(x):
        def pa(a, t):
          if t == 'DomC':
            return 'C'
          elif t == 'DomK':
            return 'K'
          else:
            return a
        return (x[0], tuple(pa(x[1][i], types[x[0]][i]) for i in range(len(x[1]))))
      buckets = defaultdict(list)
      for x in xs:
        buckets[b(x)].append(x)
      return buckets
    name_map = { x[0] : [x[0]] for x in precise_vertices }
    name_map['COC'] = ['COC', 'COC_1', 'COC_2']
    name_map['COC_1'] = ['COC_1']
    name_map['COC_2'] = []
    def projects(precise, cheap):
      assert cheap[0] in name_map[precise[0]]
      assert types[precise[0]] == types[cheap[0]]
      n = len(cheap[1])
      assert n == len(precise[1]) and n == len(types[cheap[0]])
      for i in range(n):
        if types[cheap[0]][i] == 'DomC':
          context = precise[1][i]
          while context != cheap[1][i] and context in context_projection:
            context = context_projection[context]
          if context != cheap[1][i]:
            return False
        elif types[cheap[0]][i] == 'DomK':
          if cheap[1][i] > precise[1][i]:
            return False
        else:
          if cheap[1][i] != precise[1][i]:
            return False
      return True
    cheap_buckets = bucket_vertices(cheap_vertices)
    precise_buckets = bucket_vertices(precise_vertices)
    projection = defaultdict(list)
    for pb, xs in precise_buckets.items():
      for cheap_name in name_map[pb[0]]:
        cb = tuple((pb[i] if i != 0 else cheap_name) for i in range(len(pb)))
        for y in cheap_buckets[cb]:
          for x in xs:
            if projects(x, y):
              projection[x].append(y)
    return projection
  local_inputs = get_local_inputs()
  cheap_provenance = get_slice(local_inputs['cheap'])
  precise_provenance = get_slice(local_inputs['precise'])
  check_provenance(cheap_provenance)
  check_provenance(precise_provenance)
  projection = get_projection(
      cheap_provenance['vertices'], precise_provenance['vertices'])
  return \
      { 'cheap_provenance' : cheap_provenance
      , 'precise_provenance' : precise_provenance
      , 'projection' : projection }



# TODO: avoid repetitions
# TODO: experiment with different biases
def sample_inputs(count, all_inputs):
  all_inputs = sorted(all_inputs)
  n = len(all_inputs)
  for _ in range(count):
    m = randrange(n) + 1
    yield list(sample(all_inputs, m))


def remove_cycling_arcs(provenance):
  provenance['arcs'] = [(r, x, ys) for r, x, ys in provenance['arcs']
      if x not in ys]
  check_provenance(provenance)


def reach_and_project(precise_provenance, projection, precise_inputs):
  precise_slice = keep_relevant(precise_provenance, precise_inputs,
      only_forward=True)
  if False: # DBG
    for theta, x, ys in sorted(precise_slice['arcs']):
      print('PRECARC',theta,x,sorted(ys))
  result = set()
  if False: # DBG
    for x in sorted(precise_slice['vertices']):
      print('PROJECT',x,'on',sorted(projection[x]))
  for x in precise_slice['vertices']:
    for y in projection[x]:
      result.add(y)
  return result


# A sample is a list of independent observations. Here is an example observation:
#   (['a', 'b', 'c'], [[...], [(['d', 'e'], ['f']), (['e'], ['f', 'd'])],  ...])
# Here ['a', 'b', 'c'] is the negative observation: We noticed that these arcs
# must be missing from the predictive provenance. Next comes the positive
# observation, which is a list of justification for each vertex. For example,
# one of the vertices in this example has the following list of justifications
#   [(['d', 'e'], ['f']), (['e'], ['f', 'd'])]
# The two justifications are for two different abstractions. For example,
#   (['d', 'e'], ['f'])
# means that for one abstraction the current vertex needed to be justified, and
# was justified by the forward arcs ['d', 'e'] and the nonforward arcs ['f'].
#
# The 'a', 'b', 'c', ... from above are arcs.
#
# Samples are computed as follows:
#   1. Obtain pairs (R1, T1), ..., (Rn, Tn)
#   2. Compute missing arcs, and remove them from the cheap provenance.
#   3. Compute forward and nonforwards arcs.
# For details of each step, see inline comments.
def sample_provenance(big, in_re, out_re, samples_count, provenance):

  # Step 0. Compute cheap and precise provenances, and normalize them.
  L = locals_of_global(provenance, big, in_re, out_re)
  remove_cycling_arcs(L['cheap_provenance'])
  remove_cycling_arcs(L['precise_provenance'])
  if False: # DBG
    for x in sorted(L['cheap_provenance']['arcs']):
      print('CHEAP_ARC',x)
    for x in sorted(L['precise_provenance']['arcs']):
      print('PRECISE_ARC',x)

  # Step 1. Compute (R1, T1), ..., (Rn, Tn). (n is samples_count)
  # Let P be the set of inputs of the precise provenance; that is, the tuples
  # encoding precise values of parameters. We take a random subset Pk of P.
  # We obtain Tk by projecting Pk. We obtain Rk by doing reachability from Pk
  # (in the precise provenance) and then projecting on the cheap provenance.

  sampled_abstractions = [] # contains T1, ..., Tn
  sampled_reachable = [] # contains R1, ..., Rn (NOTE: if too big, try recomputing)
  reach_and_project_ = partial(reach_and_project,
      L['precise_provenance'], L['projection'])
  all_precise_inputs = L['precise_provenance']['inputs']
  for precise_inputs in sample_inputs(samples_count, all_precise_inputs):
    if False: # DBG
      print('NEWSAMPLE')
    cheap_inputs = \
        frozenset(x for y in precise_inputs for x in L['projection'][y])
    sampled_abstractions.append(cheap_inputs)
    sampled_reachable.append(frozenset(reach_and_project_(precise_inputs)))
  samples_count = len(sampled_abstractions)
  assert samples_count == len(sampled_reachable)
  if SAFE:
    for i in range(samples_count):
      inputs = sampled_abstractions[i]
      reachable = sampled_reachable[i]
      assert inputs <= reachable

  if False: # DBG
    for i in range(len(sampled_abstractions)):
      print('ABSSIZE',len(sampled_abstractions[i]), 'REACHABLESIZE',len(sampled_reachable[i]))

  # Step 2. Compute missing arcs, and remove them from the cheap provenance.
  # An arc is missing if there exists a sample in which the body of the arc
  # is satisfied, but the head is not derived.
  missing_arcs = []
  for e in L['cheap_provenance']['arcs']:
    _, x, ys = e
    for i in range(samples_count):
      inputs = sampled_abstractions[i]
      reachable = sampled_reachable[i]
      assert type(inputs) == frozenset
      assert type(reachable) == frozenset
      if x not in reachable and all(y in reachable for y in ys):
        missing_arcs.append(e)
  missing_arcs = frozenset(missing_arcs)
  cheap_arcs = []
  cheap_vertices = set()
  for e in L['cheap_provenance']['arcs']:
    if e not in missing_arcs:
      _, x, ys = e
      cheap_arcs.append(e)
      cheap_vertices.add(x)
      cheap_vertices.update(ys)
  L['cheap_provenance']['arcs'] = cheap_arcs
  L['cheap_provenance']['vertices'] = cheap_vertices
  cheap_arcs, cheap_vertices = None, None # don't use later
  check_provenance(L['cheap_provenance'])
  if False: # DBG
    print('MISSING_ARCS',len(missing_arcs),'KEPT_ARCS',len(L['cheap_provenance']['arcs']))
  if False: # DBG
    for e in sorted(missing_arcs):
      print('MISSING',e)
    for e in sorted(L['cheap_provenance']['arcs']):
      print('NONMISSING', e)

  # Step 3. Compute forward and nonforward arcs.
  vertices = sorted(L['cheap_provenance']['vertices'])
  justification = defaultdict(list)
  for i in range(samples_count):
    inputs = sampled_abstractions[i]
    reachable = sampled_reachable[i]
    assert type(reachable) == frozenset
    assert type(inputs) == frozenset
    forward = { v : [] for v in reachable - inputs }
    nonforward = { v : [] for v in reachable - inputs }
    cheap_slice = keep_relevant(L['cheap_provenance'], inputs)
    forward_slice = keep_relevant(cheap_slice, inputs, only_forward=True)
    all_forward = frozenset(forward_slice['arcs'])
    for e in cheap_slice['arcs']:
      _, x, ys = e
      assert not all(y in reachable for y in ys) or x in reachable
      if x in reachable and all(y in reachable for y in ys):
        assert x not in inputs
        if e in all_forward:
          forward[x].append(e)
        else:
          nonforward[x].append(e)
    if SAFE:
      if True: # XXX
        for x in sorted(reachable - inputs):
          if len(forward[x]) == 0:
            print('OOPS unreachable',x,'(missing arcs)')
      assert all(len(forward[x]) > 0 for x in reachable - inputs)
    for x in reachable - inputs:
      justification[x].append((forward[x], nonforward[x]))

  return [(sorted(missing_arcs), sorted(justification.values()))]


# }}}
# helpers for handling likelihoods and polynomials {{{

# Likelihoods are products of polynomials:
#   P1^a1 ... Pn^an
# The representation is a tuple of pairs:
#   REP(P1^a1 ... Pn^an) = ((REP(P1), a1), ..., (REP(Pn), an))
# Each Pk is a multivariate polynomial over hyperparameters, of the form
#   a1 T1 + ... + an Tn
# The representation is again a tuple of pairs:
#   REP(a1 T1 + ... + an Tn) = ((REP(T1), a1), ..., (REP(Tn), an))
# Finally, each Tk is a term of the form
#   θ1^a1 ... θn^an
# By now, you can probably guess the representation. It's the same, almost:
#   REP(θ1^a1 ... θn^an) = ((θ1,a1), ..., (θn,an))


def simplify_likelihood(x):
  def collect(ps, depth=1):
    if depth == 0:
      return ps
    r = defaultdict(int)
    for y, k in ps:
      r[collect(y, depth-1)] += k
    return tuple(sorted((a, b) for a, b in r.items() if b != 0))
  return collect(x, depth=3)


def derive_term(term_a, variable):
  term, a = term_a
  new_term = []
  variable_power = 0
  for theta, b in term:
    if theta == variable:
      variable_power += b
    else:
      new_term.append((theta, b))
  if variable_power == 0: # not necessary, i think
    return ((),0)
  a *= variable_power
  if variable_power - 1 != 0:
    new_term.append((variable, variable_power - 1))
  return (tuple(new_term), a)


def derive_polynomial(polynomial, variable):
  new_polynomial = []
  for term in polynomial:
    new_polynomial.append(derive_term(term, variable))
  return tuple(new_polynomial)


# For representation of polynomials, see comment above likelihood_one().
# Reliability polynomials are a special case.
def check_relpoly(x):
  if not SAFE:
    return
  for term, c in x:
    assert type(c) == int
    assert c != 0
    vs = set(v for v, _ in term)
    assert len(vs) == len(term)
    assert all(p == 1 for _, p in term)


def relpoly_one():
  return (((),1),)


def relpoly_multiply(x, y):
  check_relpoly(x)
  check_relpoly(y)
  def vs(t):
    return tuple(v for v, _ in t)
  zd = defaultdict(int)
  for xt, xc in x:
    vxt = vs(xt)
    for yt, yc in y:
      zd[tuple(sorted(set(vxt + vs(yt))))] += xc * yc
  z = tuple((tuple((v, 1) for v in t), c) for t, c in zd.items() if c != 0)
  check_relpoly(z)
  return z


def bitcount(x):
  assert type(x) == int
  assert x >= 0
  count = 0
  while x > 0:
    count += x % 2
    x //= 2
  return count


def relpoly_of_clause(clause):
  clause = tuple(set(clause))
  n = len(clause)
  if n > 10:
    sys.stderr.write('W: running algo with ~2^{} steps\n'.format(n))
    sys.stderr.flush()
    sys.stdout.flush()
  terms = []
  for mask in range(1, 1 << n):
    coefficient = 1 if bitcount(mask) % 2 == 1 else -1
    variables = tuple((clause[i], 1) for i in range(n) if ((mask >> i) & 1) == 1)
    terms.append((variables, coefficient))
  p = tuple(terms)
  check_relpoly(p)
  return p


def guarded_log(x):
  assert 0 <= x and x <= 1
  if x == 0:
    return float('-inf')
  return log(x)


def evaluate_log_term(term, assignment):
  result = 0
  for theta, a in term:
    result += a * guarded_log(assignment(theta))
  return result


def evaluate_polynomial(polynomial, assignment):
  result = 0
  for term, a in polynomial:
    result += a * exp(evaluate_log_term(term, assignment))
  return result


def evaluate_log_likelihood(likelihood, assignment):
  result = 0
  for polynomial, a in likelihood:
    result += a * guarded_log(evaluate_polynomial(polynomial, assignment))
  return result


def evaluate_log_likelihood_der(likelihood, assignment, variable):
  ts = []
  for polynomial, a in likelihood:
    if a != 0:
      ts.append((polynomial, derive_polynomial(polynomial, variable), a))
  result = 0
  for p, p_der, a in ts:
    p_value = evaluate_polynomial(p, assignment)
    p_der_value = evaluate_polynomial(p_der, assignment)
    if p_value == 0: # hack
      sign = a * p_der_value
      if sign > 0:
        return infinity
      if sign < 0:
        return -infinity
      assert False
    result += a * p_der_value / p_value
  return result


# upper bound
def evaluate_log_baseline(justification):
  result = 0
  for vertex_justification in justification:
    n = float('inf')
    for forward, nonforward in vertex_justification:
      n = min(n, len(forward) + len(nonforward))
    result += log1p((-2) ** (-n))
  return result


def likelihood_lowerbound_pos(justification):
  def thetapoly_of_relpoly(p):
    q = []
    for term, alpha in p:
      theta_term = []
      for (theta, _, _), n in term:
        theta_term.append((theta, n))
      q.append((tuple(theta_term), alpha))
    return tuple(q)
  likelihood = []
  for vertex_justification in justification:
    relpoly = relpoly_one()
    for forward, _ in vertex_justification:
      relpoly = relpoly_multiply(relpoly, relpoly_of_clause(forward))
    likelihood.append((thetapoly_of_relpoly(relpoly), 1))
  return simplify_likelihood(likelihood)


def likelihood_neg(missing):
  def one_minus_theta(theta):
    return ((((), 1), (((theta, 1),), -1)), 1)
  likelihood = []
  for theta, _, _ in missing:
    likelihood.append(one_minus_theta(theta))
  return simplify_likelihood(likelihood)

def likelihood_lowerbound(sample):
  missing, justification = sample
  pos = likelihood_lowerbound_pos(justification)
  neg = likelihood_neg(missing)
  return pos + neg

def likelihood_of_independent_samples(get_likelihood, samples):
  likelihood = []
  for s in samples:
    likelihood.extend(get_likelihood(s))
  return simplify_likelihood(likelihood)


def get_parameter_set(likelihood):
  def get(x, depth):
    if depth == 0:
      yield x
    else:
      for y, _ in x:
        for z in get(y, depth-1):
          yield z
  return set(x for x in get(likelihood, 3))

# }}}
# numerical optimization of likelihood {{{
def random_parameters(likelihood, parameters):
  result = dict(parameters)
  for p in get_parameter_set(likelihood):
    if p not in result:
      result[p] = uniform(0, 1)
  return result


# d/dx1 log L, ..., d/dxn log L
def compute_gradient(likelihood, parameters):
  gradient = { p : evaluate_log_likelihood_der(likelihood, parameters.get, p)
      for p in parameters.keys() }
  if False: # DBG
    print('gradient', sorted(gradient.items()))
  return gradient


def update_parameters(parameters, gradient, alpha):
  eps = 0.0001
  max_delta = 0.1
  min_delta = 0
    # with 0 gets stuck in local optima quite often, but converges
    # with eps it often finds a better local optimum, but sometimes doesn't converge
  def snap(x):
    return min(1-eps, max(eps, x))
  max_d = 0
  for p, v in parameters.items():
    d = alpha * gradient[p]
    if v < 2 * eps and v + d < 2 * eps:
      continue
    if v > 1 - 2 * eps and v + d > 1 - 2 * eps:
      continue
    max_d = max(max_d, abs(d))
  if False: # DBG
    print('max_d',max_d)
    print('alpha',alpha)
  if max_d != 0:
    if max_d < min_delta:
      alpha *= min_delta / max_d
    if max_d > max_delta:
      alpha *= max_delta / max_d
  if False: # DBG
    print('adjusted_alpha',alpha)
  return { p : snap(v + alpha * gradient[p]) for p, v in parameters.items() }


def scipy_likelihood(likelihood, parameters, x):
  n = len(parameters)
  if any(not (0 < t and t < 1) for t in x):
    return infinity
  ps = { parameters[i] : x[i] for i in range(n) }
  return -evaluate_log_likelihood(likelihood, ps.get)


# TODO: make it work again
def scipy_likelihood_jac(samples, parameters, x):
  def d(t):
    if not (0 < t):
      print('MIN')
      return -1
    elif not (t < 1):
      print('MAX')
      return 1
    else:
      return 0
  jac = [d(t) for t in x]
  if any(jac):
    return jac
  n = len(parameters)
  ps = { parameters[i] : x[i] for i in range(n) }
  jac = compute_gradient(samples, ps)
  return [-jac[parameters[i]] for i in range(n)]


# TODO: add derivative
def optimize_with_scipy(samples, iterations):
  parameters = list(get_parameter_set(samples))
  n = len(parameters)
  epsilon = 1e-9
  res = minimize(
      partial(scipy_likelihood, samples, parameters),
      jac=False, #partial(scipy_likelihood_jac, samples, parameters),
      x0=[uniform(0, 1) for _ in parameters],
      bounds=[(epsilon,1-epsilon)]*n,
      method='SLSQP',
      options={'disp':True, 'maxiter':iterations})
  return { parameters[i] : res.x[i] for i in range(n) }


# TODO: add derivative
def optimize_with_hopping(likelihood, iterations):
  parameters = list(get_parameter_set(likelihood))
  def mk_fa(xs): # scipy fails with weird error without this wrapping
    return np.array([float(x) for x in xs])
  def objective_f(x):
    return scipy_likelihood(likelihood, parameters, x)
  def accept(**kwargs):
    return all((0 < x and x < 1) for x in kwargs['x_new'])
  res = basinhopping(
      objective_f,
      x0=[uniform(0, 1) for _ in parameters],
      #minimizer_kwargs={'method':'L-BFGS-B', 'jac':False},
      minimizer_kwargs={'method':'SLSQP', 'jac':False},
      accept_test=accept,
      niter=iterations)
  if False: # DBG
    print('OPTIMIZATION RESULT', res)
  return { parameters[i] : res.x[i] for i in range(len(parameters)) }


def estimate_likelihood_quality(likelihood, parameters):
  logL = -evaluate_log_likelihood(likelihood, parameters.get)
  logL0 = -evaluate_log_likelihood(likelihood, (lambda _ : 0.5))
  sys.stderr.write('likelihood gain is {:5.3f}-{:5.3f}={:5.3f}\n'
      .format(logL0, logL, logL0-logL))
  sys.stderr.flush()


def optimize_with_gradient_ascent(
    likelihood,
    iterations,
    initial_step,
    alpha,
    initial_parameters):
  parameters = dict(initial_parameters)
  step = initial_step
  for _ in range(iterations):
    gradient = compute_gradient(likelihood, parameters)
    parameters = update_parameters(parameters, gradient, step)
    step *= alpha
    estimate_likelihood_quality(likelihood, parameters)
    if False: # DBG
      print('step', step)
      print('parameters',sorted(parameters.items()))
  return parameters


def optimize_one_probability(likelihood, parameter, old_value):
  derivatives = []
  for polynomial, _ in likelihood:
    derivatives.append(derive_polynomial(polynomial, parameter))
  def parameter_value(v, p):
    assert p == parameter
    return v
  def f(x):
    return -evaluate_log_likelihood(likelihood, partial(parameter_value, x[0]))
  def f_der(x):
    n = len(likelihood)
    assert n == len(derivatives)
    result = 0
    for i in range(n):
      p, alpha = likelihood[i]
      p_der = derivatives[i] # precomputed above
      if alpha == 0:
        continue
      p_value = evaluate_polynomial(p, partial(parameter_value, x[0]))
      p_der_value = evaluate_polynomial(p_der, partial(parameter_value, x[0]))
      if p_value == 0: # hack
        sign = alpha * p_der_value
        assert sign != 0
        if sign > 0:
          return np.array([infinity])
        if sign < 0:
          return np.array([-infinity])
      result += alpha * p_der_value / p_value
    return np.array([-result]) # without np.array, scipy fails with weird error
  epsilon = 1e-15
  def accept(**kwargs):
    return all((epsilon <= x and x <= 1-epsilon) for x in kwargs['x_new'])
  minimizer_kwargs =\
    { 'method' : 'L-BFGS-B'
    , 'jac' : f_der
    , 'bounds' : [(epsilon, 1-epsilon)] }
  res = basinhopping(f, [old_value],
      minimizer_kwargs=minimizer_kwargs,
      accept_test=accept,
      niter_success=5)
  if False: # DBG
    print('OPTIMIZATION_RESULT',res)
  return (res.x[0], res.fun)


# Cycles several times (iterations) through all coordinates. For each
# coordinate, it optimizes using a generic solver.
def optimize_with_coordinate_ascent(likelihood, iterations, initial_parameters):
  def evaluate_nonp(p, parameters):
    result = []
    for polynomial, a in likelihood:
      new_polynomial = []
      for term, b in polynomial:
        new_term = []
        for theta, c in term:
          if theta == p:
            new_term.append((p, c))
          else:
            b *= pow(parameters[theta], c)
        new_polynomial.append((tuple(new_term), b))
      result.append((tuple(new_polynomial), a))
    return tuple(result)
  parameters = dict(initial_parameters)
  old_value = None
  for _ in range(iterations):
    for p in parameters.keys():
      likelihood_p = evaluate_nonp(p, parameters)
      (new_p, value) = optimize_one_probability(likelihood_p, p, parameters[p])
      if False: # DBG
        print('OPTIMIZE_STEP',p,parameters[p],'->',new_p)
      parameters[p] = new_p
    estimate_likelihood_quality(likelihood, parameters)
    if old_value is not None and abs(old_value - value) < 1e-9:
      break
    old_value = value
  return parameters


# }}}
# main {{{
def save_parameters(out, parameters):
  ps = sorted((v, k) for k, v in parameters.items())
  for v, k in ps[::-1]:
    out.write('{:10.03f} {}\n'.format(v, k))


def load_parameters(infile):
  sys.stderr.write('I: loading parameters\n')
  sys.stderr.flush()
  parameters = {}
  for line in infile:
      ws = line.split()
      parameters[ws[1]] = float(ws[0])
  return parameters


def tuplify(xs):
  if type(xs) == str:
    return xs
  try:
    return tuple(tuplify(x) for x in xs)
  except TypeError:
    return xs


def main():
  args = argparser.parse_args()
  seed(args.seed)

  sys.stderr.write('I: getting samples\n')
  sys.stderr.flush()
  samples = []
  if args.load_samples:
    with open(args.load_samples, 'r') as f:
      samples.extend(json.load(f))
  if args.save_samples:
    with open(args.save_samples, 'w') as f:
      f.write('I am testing whether I can write to this file.\n')
  sample_provenance_ = partial(
      sample_provenance, args.big, args.i, args.o, args.samples)
  for provenance_filename in args.provenances:
    try:
      with open(provenance_filename, 'r') as provenance_file:
        sys.stderr.write('I: processing provenance {}\n'.format(provenance_filename))
        provenance = parse_global_provenance(provenance_file)
        new_samples = sample_provenance_(provenance)
        samples.extend(new_samples)
    except Exception as e:
      sys.stderr.write('E: {}\n'.format(e))
  if args.save_samples:
    with open(args.save_samples, 'w') as f:
      json.dump(samples, f, indent=1, sort_keys=True)

  if not args.dont_optimize or not args.dont_evaluate:
    sys.stderr.write('I: computing likelihood\n')
    sys.stderr.flush()
    samples = tuplify(samples)
    likelihood = likelihood_of_independent_samples(likelihood_lowerbound, samples)
    if not args.dont_evaluate\
        or (not args.dont_optimize and args.optimizer in ['hill','coord']):
      parameters = {}
      if args.load_parameters:
        try:
          with open(args.load_parameters, 'r') as f:
            parameters = load_parameters(f)
        except Exception:
          sys.stderr.write('E: cannot load parameters; will use random ones\n')
          sys.stderr.flush()
      parameters = random_parameters(likelihood, parameters)
    elif args.load_parameters:
      sys.stderr.write('W: parameters NOT loaded\n')
      sys.stderr.flush()

  if not args.dont_optimize:
    sys.stderr.write('I: optimizing\n')
    sys.stderr.flush()
    with open(args.out, 'w') as out:
      if args.optimizer == 'hopping':
        parameters = optimize_with_hopping(likelihood, args.iterations)
      elif args.optimizer == 'slsqp':
        parameters = optimize_with_scipy(likelihood, args.iterations)
      elif args.optimizer == 'hill':
        parameters = optimize_with_gradient_ascent(
            likelihood, args.iterations, args.initial_step, args.a, parameters)
      elif args.optimizer == 'coord':
        parameters = optimize_with_coordinate_ascent(
            likelihood, args.iterations, parameters)
      else:
        assert False
      estimate_likelihood_quality(likelihood, parameters)
      save_parameters(out, parameters)

  if not args.dont_evaluate:
    sys.stderr.write('I: evaluating likelihood quality\n')
    sys.stderr.flush()
    def get_param(p):
      if p not in parameters:
        return 0.5
      epsilon = 1e-9
      return max(min(parameters[p], 1-epsilon), epsilon)
    log_upperbound_baseline = 0
    for one_sample in samples:
      _, justification = one_sample
      log_upperbound_baseline += evaluate_log_baseline(justification)
    log_lowerbound_current = \
        evaluate_log_likelihood(likelihood, get_param)
    if True: # DBG
      sys.stdout.write('{:5.3f} lowerbound on current log-likelihood\n'
          .format(log_lowerbound_current))
      sys.stdout.write('{:5.3f} upperbound on uniform/baseline log-likelihood\n'
          .format(log_upperbound_baseline))
    sys.stderr.write('I: {:5.3f} gain in log-likelihood\n'
        .format(log_lowerbound_current - log_upperbound_baseline))
    sys.stderr.flush()


if __name__ == '__main__':
  main()

# }}}
