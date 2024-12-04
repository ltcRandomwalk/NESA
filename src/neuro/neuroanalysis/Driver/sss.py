import sys
import os


benchs = ['andors-trail', 'app-018', 'app-324', 'app-ca7', 'app-kQm', 'ginger-master', 'noisy-sounds', 'tilt-mazes']
global_t = 0
global_f = 0
trueProbs = [line.strip() for line in open('./trueprob.txt', 'r').readlines() ]
falseProbs = [line.strip() for line in open('./falseprob.txt', 'r').readlines() ]
for bench in benchs:
    bench_dir = os.path.join('.', bench+'/chord_output_mln-taint-problem')
    ICCNames = [ line.strip() for line in open(os.path.join(bench_dir, 'ICCNames.txt')).readlines()]
    orc_ICCNames = [ line.strip() for line in open(os.path.join(bench_dir, 'oracle_ICCNames.txt')).readlines()]
    #fls_ICCNames = ICCNames - orc_ICCNames
    outputFile = open(os.path.join(bench_dir, 'soft_evi.txt'), 'w')
    for icc in ICCNames:
        if icc in orc_ICCNames:
            print(f"{icc} {float(trueProbs[global_t])}", file=outputFile)
            global_t += 1
        else:
            print(f"{icc} {float(falseProbs[global_f])}", file=outputFile)
            global_f += 1
    outputFile.close()
    



