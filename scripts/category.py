import os

benchmarks = ['ftp', 'hedc', 'javasrc-p', 'jspider', 'moldyn', 'montecarlo', 'toba-s', 'weblech']

ranking_files = [ '/home/tcli/NESA/original_results/neuro/pts/' + benchmark + '/rank-our-approach-small.txt' for benchmark in benchmarks ]



for p in range(1, 100):
    acc_rate = []
    for rankfile in ranking_files:
        with open(rankfile, 'r') as f:
            acc = 0
            tot = 0
            for line in f.readlines()[1:]:
                if line[0] == "#":
                    break
                _, prob, ground, _, _, _ = line.strip().split()
                if float(prob) * 100 >= p and "True" in ground:
                    acc += 1
                elif float(prob) * 100 < p and "False" in ground:
                    acc += 1
                tot += 1
            acc_rate.append(acc/tot)
    print(f"{p}: {acc_rate}. Average accuracy: {sum(acc_rate) / len(acc_rate)}")