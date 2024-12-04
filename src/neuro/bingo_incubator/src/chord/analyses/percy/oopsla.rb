#!/usr/bin/ruby

require 'execrunner'

# Dynamic analysis for OOPSLA 2010

def D(a, b); "-D#{a}=#{b}" end
def P(a, b); "-Dchord.partition.#{a}=#{b}" end
def selD(i, a, *bs); sel(i, *bs.compact.map{|b| D(a, b)}) end
def selP(i, a, *bs); sel(i, *bs.compact.map{|b| P(a, b)}) end

extraOpts = []
# These things will be taken care of by runInEnv
javaBasePath = nil
$basePath = '/scratch/pliang/chord'
extraOpts << D('chord.java.analysis.path', $basePath+'/dist/chord.jar')
extraOpts << D('chord.dlog.analysis.path', $basePath+'/dlog')
extraOpts << P('finalPoolPath', '/work/pliang/chord/execs')

def SEL(*args); sel(*args) end

version = '12'
env!(requireTags,
  'ant', extraOpts,

  D('chord.scope.kind', 'dynamic'),
  D('chord.out.pooldir', $basePath+'/state/execs'),
  selP(0, 'verbose', 0, 1, 5, 7),

  D('chord.scope.exclude', ''), # Instrument everything

  selP(0, 'includeAllQueries', false, true),

  def bench(path)
    l(
      D('chord.work.dir', path),
    nil)
  end,

  def dacapobench(name); bench($basePath+'/pjbench/dacapo/benchmarks/'+name) end,

  def coreAbstractions
    SEL(nil,
      P('abstraction', 'none'),
      l(P('abstraction', 'recency'),
        sel(0,
          l(P('recencyOrder', 0), P('kCFA', 0)),
          l(P('recencyOrder', 0), P('kCFA', 5)),
          l(P('recencyOrder', 1), P('kCFA', 0)),
        nil),
      nil),
      stop,
      P('abstraction', 'reach'),
    nil)
  end,

  #############################################################
  # Debug
  run(tag('debug'),
    D('chord.run.analyses', 'dynamic-thracc-java'),
    dacapobench('batik'),
    sel(0,
      P('abstraction', 'none'),
      l(P('abstraction', 'recency'), P('kCFA', 0), P('recencyOrder', 0)),
    nil),
    'run',
  nil),

  #############################################################
  # Check statistics
  run(tag('stat'),
    D('chord.run.analyses', 'dynamic-visitcnt-java'),
    P('addToView', 'stats'),
    sel(0,
      dacapobench('luindex'),
      dacapobench('lusearch'),
      dacapobench('pmd'),
      dacapobench('fop'),
      dacapobench('batik'),
      dacapobench('antlr'),
      dacapobench('hsqldb'),
      dacapobench('avrora'),
      dacapobench('xalan'),
    nil),
    'run',
  nil),

  #############################################################
  # Annecdote annotation
  run(tag('ann'),
    l(P('maxFieldAccessesToPrint', 100), P('outputGraph', true)),
    D('chord.run.analyses', 'ss-thread-escape'),
    dacapobench('luindex'),
    sel(0,
      P('abstraction', 'none'),
      l(P('abstraction', 'recency'), P('kCFA', 0), P('recencyOrder', 0)),
    nil),
    'run',
  nil),

  #############################################################
  # Comprehensive study
  run(tag('all'),
    # Client
    SEL(2,
      #D('chord.run.analyses', 'ss-empty'),
      l(D('chord.run.analyses', 'ss-thread-escape'), P('addToView', 'thread-escape'+version)),
      l(D('chord.run.analyses', 'ss-stationary-fields'), P('addToView', 'stationary-fields'+version)),
      sel(0,
        l(D('chord.run.analyses', 'ss-may-alias'), P('addToView', 'may-alias'+version)),
        #l(D('chord.run.analyses', 'ss-may-alias'), P('addToView', 'may-alias'+version), D('chord.check.exclude', ''), P('exclude', 'none')),
      nil),
      l(D('chord.run.analyses', 'ss-thread-access'), P('addToView', 'thread-access'+version)),
      l(D('chord.run.analyses', 'ss-shared-lock-access'), P('addToView', 'shared-lock-access'+version), D('chord.check.exclude', ''), P('exclude', 'none')),
    nil),

    # Benchmark
    sel(2,
      sel(0,
        bench('examples/tiny'),
        bench('examples/datarace_test'),
      nil),
      l(
        sel(0,
          bench($basePath+'/cbench/elevator'), # works
          bench($basePath+'/cbench/hedc'), # works
          bench($basePath+'/cbench/java_grande/moldyn'), # useless
          bench($basePath+'/cbench/java_grande/montecarlo'), # useless
          bench($basePath+'/cbench/java_grande/raytracer'), # useless
          bench($basePath+'/cbench/philo'), # works
          bench($basePath+'/cbench/sor'), # works
          bench($basePath+'/cbench/tsp'), # works
          bench($basePath+'/cbench/weblech-0.0.3'), # works
        nil),
      nil),
      SEL(0,
        dacapobench('luindex'),
        dacapobench('lusearch'),
        dacapobench('pmd'),
        dacapobench('fop'),
        dacapobench('batik'),
        dacapobench('antlr'),
        dacapobench('hsqldb'),
        dacapobench('avrora'),
        dacapobench('xalan'),
        #dacapobench('bloat'), # crashes
        #dacapobench('sunflow'), # too big (373M events)
      nil),
    nil),

    coreAbstractions,

    'run',
  nil),

  #############################################################
  # More detailed study
  run(tag('int'),
    # Subset
    #selD(nil, 'chord.run.analyses', 'ss-thread-escape', 'ss-stationary-fields'),
    SEL(nil,
      l(D('chord.run.analyses', 'ss-thread-escape')),
      l(D('chord.run.analyses', 'ss-stationary-fields')),
      l(D('chord.run.analyses', 'ss-thread-access')),
      l(D('chord.run.analyses', 'ss-shared-lock-access'), D('chord.check.exclude', ''), P('exclude', 'none')),
    nil),

    sel(nil,
      dacapobench('pmd'),
      dacapobench('batik'),
      dacapobench('luindex'),
      dacapobench('lusearch'),
    nil),

    sel(1,
      # Precision/complexity (0)
      l(P('addToView', 'random'+version),
        l(P('abstraction', 'random'), selP(nil, 'randSize', 1000, 10000, 100000)),
      nil),

      # Vary r
      l(P('addToView', 'varyr'+version),
        P('abstraction', 'recency'),
        selP(nil, 'recencyOrder', 0, 1, 2, 3, 4, 5, 6),
        #selP(nil, 'kCFA', 9, 11, 12, 13, 14, 15, 16, 17, 18, 19),
        #selP(nil, 'kCFA', 0, 1, 2, 3, 4, 5, 6, 7, 8, 10000),
        selP(nil, 'kCFA', 0, 10000),
      nil),

      # Vary reach
      l(P('addToView', 'varyreach'+version),
        selP(nil, 'kCFA', 0, 1, 2, 3, 4, 5, 6, 7, 8, 10000),
        selP(nil, 'abstraction', 'reach', 'point'),
      nil),

      stop,

      # Strong versus weak updates (improves reachability)
      l(P('addToView', 'update'+version),
        coreAbstractions,
        selP(nil, 'useStrongUpdates', true, false),
      nil),

      # Array collapse (shouldn't change anything); but actually screws up reachability
      l(P('addToView', 'array'+version),
        coreAbstractions,
        selP(nil, 'collapseArrayIndices', true, false),
      nil),
    nil),

    'run',
  nil),
nil)
