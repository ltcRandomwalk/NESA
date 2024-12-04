#!/usr/bin/ruby

q = false
ARGV.delete_if { |x|
  if x =~ /^%q(:?)(.*)$/
    q = $2.split(/,/)
    true
  else
    false
  end
}
require 'execrunner'

$hostname = `hostname -f`.chomp

$version = 8
excludeSun = 'sun.,com.sun.,com.ibm.,org.apache.harmony.'
def D(a, b); lambda {|e| "-D#{a}=#{envEval(e,b)}"} end
def P(a, b); D("chord.sliver.#{a}", b) end
def selD(i, a, *bs); general_sel(i, a, bs, false, lambda{|*z| D(*z)}) end
def selP(i, a, *bs); general_sel(i, a, bs, false, lambda{|*z| P(*z)}) end
$basePath = Dir.pwd

############################################################

def B(name)
  path = [$basePath+'/pjbench/'+name, $basePath+'/pjbench/dacapo/benchmarks/'+name].find { |x| File.exists?(x) }
  raise "Unknown benchmark: #{name}" unless path
  l(D('chord.work.dir', "//"+path), tag(name), let(:bench, name))
end

def classicTask(queryType, name, cspaName)
  l(P('queryType', queryType),
    P('initTaskNames', "#{name}-init-dlog"), 
    P('taskNames', "cspa-#{cspaName}-dlog,#{name}-classic-dlog"),
    P('relevantTaskName', "#{name}-classic-#{cspaName}-relevant-dlog"),
    P('transTaskName', "#{name}-classic-#{cspaName}-trans-dlog"))
end

def sliverTask(queryType, name)
  l(P('queryType', queryType),
    P('initTaskNames', "#{name}-init-dlog"), 
    P('taskNames', "cspa-sliver-dlog,#{name}-sliver-dlog"),
    P('relevantTaskName', "#{name}-sliver-relevant-dlog"),
    P('transTaskName', "#{name}-sliver-trans-dlog"))
end

# For master/slave for POPL
$hosts = {
  ['tsp', 'H'] => '',
  ['tsp', 'I'] => '',
  ['elevator', 'H'] => '',
  ['elevator', 'I'] => '',
  ['hedc', 'H'] => '',
  ['hedc', 'I'] => '',
  ['weblech-0.0.3', 'H'] => '',
  ['weblech-0.0.3', 'I'] => '',
  ['lusearch', 'H'] => '',
  ['lusearch', 'I'] => '',
}
def parSetup(mode); lambda { |e|
  if mode == 'worker'
    bench = e[:bench] or raise "No benchmark"
    site = e[:site] or raise "No site"
    host = $hosts[[bench,site]]
    $stderr.puts "No host for benchmark #{bench} and site type #{site}, using #{$hostname}" unless host
  end
  l(P('mode', mode),
    host && P('masterHost', host),
    P('masterPort', 4000))
} end

env!(
  requireTags,
  'q',
  '-mem', '4g', # BIG
  (q ? ['-tag']+q+['-add'] : '-direct'), '---',
  'scripts/runInEnv', 'ant', '-f', 'chord/extra/build.xml',
  D('chord.java.analysis.path', "//"+$basePath+'/chord.jar'),
  D('chord.out.dir', '//_OUTPATH_'),
  D('chord.bddbddb.max.heap', '2048m'),
  D('percy', true),
  D('execName', 'sliver'),
  appendArgs('run'),

  D('chord.scope.kind', 'rta'),
  D('chord.reflect.kind', 'dynamic'),
  D('chord.scope.exclude', excludeSun),
  sel(1, l(let(:query, 'app')), l(let(:query, 'all'), D('chord.check.exclude', excludeSun))), # 0: APP, 1: ALL

  # SETTINGS
  sellettag(1, :task, 'thresc', 'race', 'monosite', 'downcast'),
  sellettag(0, :k, 1, 2, 3),
  sel(1,
    B('tsp'),
    B('elevator'),
    B('hedc'),
    B('weblech-0.0.3'),
    B('lusearch'),
    B('hsqldb'),
    B('avrora'),
    B('sunflow'),
  nil),
  l(P('addToView', 'stats'+$version.to_s), tag('stats'), D('chord.run.analyses', 'cipa-0cfa-dlog,src-files-java'), run), stop,

  sellettag(0, :site, 'H', 'I'),

  selP(0, 'typeStrategy', 'identity', 'is', 'has', 'is,has'),
  sel(1, l(), P('disallowRepeats', true)),

  # Flow insensitive k-CFA/k-obj (just for benchmarking); (k+1,k) is k-CFA
  sel(nil,
    def K(obj, cfa); l(D('chord.kobj.k', obj), D('chord.kcfa.k', cfa)) end,
    l(P('addToView', lambda{|e| 'uniform-'+e[:query]+'-'+e[:task]+$version.to_s}), tag('u'),
      D('chord.analysis.exclude', 'race-classic-dlog,race-sliver-dlog'),
      sel(:task, 'thresc' => D('chord.run.analyses', 'thresc-flowins-java'),
                 'race' => l(D('chord.run.analyses', 'datarace-java'),
                             D('chord.exclude.nongrded', true),
                             D('chord.exclude.parallel', false),
                             D('chord.print.results', false))),
      sel(:site,
        'I' => l(D('chord.inst.ctxt.kind', 'cs'), D('chord.stat.ctxt.kind', 'cs')), # call-site-based
        'H' => l(D('chord.inst.ctxt.kind', 'co'), D('chord.stat.ctxt.kind', 'cc')), # object-based
        '0' => l(D('chord.inst.ctxt.kind', 'ci'), D('chord.stat.ctxt.kind', 'ci'))),
      sel(:k, 1 => K(1,0), 2 => K(2,1), 3 => K(3,2)),
    nil),

    # Slivers
    l(P('addToView', lambda {|e| 'sliver-'+e[:task]+$version.to_s}),
      D('chord.run.analyses', 'sliver-ctxts-java'),

      sel(1,
        l( # OLD CLASSIC
          #P('useCtxtsAnalysis', true),
          sel(:site,
            'I' => l(D('chord.inst.ctxt.kind', 'cs'), D('chord.stat.ctxt.kind', 'cs')), # call-site-based
            'H' => l(D('chord.inst.ctxt.kind', 'co'), D('chord.stat.ctxt.kind', 'cc')), # object-based
            nil => nil),
          sel(lambda{|e|[e[:task],e[:site]]},
            ['race', 'H'] => classicTask('EE', 'race', 'kobj'),
            ['race', 'I'] => classicTask('EE', 'race', 'kcfa'),
            ['monosite', 'H'] => classicTask('I', 'monosite', 'kobj'),
            ['monosite', 'I'] => classicTask('I', 'monosite', 'kcfa'),
            ['downcast', 'H'] => classicTask('P', 'downcast', 'kobj'),
            ['downcast', 'I'] => classicTask('P', 'downcast', 'kcfa'),
            nil => nil)),
        sel(:task, # NEW SLIVER
          'thresc' => sliverTask('E', 'thresc'),
          'race' => sliverTask('EE', 'race'),
          'monosite' => sliverTask('I', 'monosite'),
          'downcast' => sliverTask('P', 'downcast'),
          nil => nil),
      nil),

      sel(:site, 'I' => P('useObjectSensitivity', false),
                 'H' => P('useObjectSensitivity', true)),

      def K(obj, cfa, d=2); l(P('minH', obj), P('minI', cfa), P('maxH', obj+d), P('maxI', cfa+d)) end,
      def R(r); l(tag("R=#{r}"), P('maxIters', r)) end,

      sel(nil,
        l(tag('s'), sel(:k, 1 => K(1,0), 2 => K(2,1), 3 => K(3,2))), # Uniform K values
        l(tag('sr'), K(1,0), R(20),
          sel(3,
            l(P('pruneSlivers', false), P('refineSites', false)), # Full refinement
            l(P('pruneSlivers', false), P('refineSites', true)), # Site-based DatalogRefine
            l(P('pruneSlivers', true), P('refineSites', false)), # Sliver-based DatalogRefine with simple pruning
            l(P('pruneSlivers', true), P('refineSites', false), # Sliver-based DatalogRefine + type pruning
              selP(2, 'pruningTypeStrategy', 'identity', 'single', 'is', 'has', 'is,has')),
          nil),
        nil),
        l(tag('sm'), parSetup('master'), P('minimizeAbstraction', true), K(1,0,1)), # Coarsen
        l(tag('sw'), parSetup('worker')), # Coarsen
      nil),

      #P('focusQuery', '3,3'),
      #selP(nil, 'randQuery', *(5..10).to_a),

      #P('inspectTransRels', true),
      #P('verbose', 1),
      #P('verifyAfterPrune', true),
      #P('saveRelations', true),
    nil),
  nil),

  D('description', tagstr),

  run,
nil)
