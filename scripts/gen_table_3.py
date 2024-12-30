import os

artifact_root_dir = os.getenv("ARTIFACT_ROOT_DIR")
reproduced_dir = os.path.join(artifact_root_dir, "reproduced_results")
original_dir = os.path.join(artifact_root_dir, "original_results")

benchmarks = ["bc", "cflow", "grep", "gzip", "libtasn1", "patch", "readelf", "sed", "sort", "tar", "optipng", "latex2rtf", "shntool"]
benchmark_statistics = {
    "bc": {"full_name": "bc-1.06", "bug_num": 2},
    "cflow": {"full_name": "cflow-1.5", "bug_num": 1},
    "grep": {"full_name": "grep-2.19", "bug_num": 1},
    "gzip": {"full_name": "gzip-1.2.4a", "bug_num": 8},
    "libtasn1": {"full_name": "libtasn1-4.3", "bug_num": 1},
    "patch": {"full_name": "patch-2.7.1", "bug_num": 1},
    "readelf": {"full_name": "readelf-2.24", "bug_num": 1},
    "sed": {"full_name": "sed-4.3", "bug_num": 1},
    "sort": {"full_name": "sort-7.2", "bug_num": 1},
    "tar": {"full_name": "tar-1.28", "bug_num": 1},
    "optipng": {"full_name": "optipng-0.5.3", "bug_num": 1},
    "latex2rtf": {"full_name": "latex2rtf-2.1.1", "bug_num": 2},
    "shntool": {"full_name": "shntool-3.0.5", "bug_num": 6},
}

result_file = open(os.path.join(reproduced_dir, "dynamic/table3.csv"), "w")
result_file.write(f"Benchmark,Our Method,,DynaBoost,,Baseline,\n")
result_file.write(f",Init,Iters,Init,Iters,Init,Iters\n")

def get_average_rank(ranking_file):
    num = 0
    tot_rank = 0
    with open(ranking_file, "r") as f:
        
        for line in f.readlines()[1:]:
            rank, _, ground, _, _, _ = line.strip().split()
            if "True" in ground:
                tot_rank += int(rank)
                num += 1
    return tot_rank / num

def get_detected_bug_num(stats_file):
    num = 0
    with open(stats_file, "r") as f:
        for line in f.readlines()[1:]:
            ground = line.strip().split()[2]
            if "True" in ground:
                num += 1
    return num

def get_iters(stats_file):
    iters = 0
    with open(stats_file, "r") as f:
        iters = len(f.readlines()) - 1
    return iters

for benchmark in benchmarks:
    results = []
    for method in ["NESA", "Dynaboost", "baseline"]:
        init_file_name = f"dynamic/{benchmark_statistics[benchmark]['full_name']}/{benchmark}{method}0.out"
        stats_file_name = f"dynamic/{benchmark_statistics[benchmark]['full_name']}/{benchmark}{method}-stats.txt"
        init_ranking = os.path.join(reproduced_dir, init_file_name)
        if not os.path.isfile(init_ranking):
            print(f"WARNING: The init ranking file for benchmark {benchmark} and method {method} does not exist! Using original data.")
            init_ranking = os.path.join(original_dir, init_file_name)
        results.append(get_average_rank(init_ranking))
        
        stats_file = os.path.join(reproduced_dir, stats_file_name)
        if not os.path.isfile(stats_file):
            print(f"WARNING: The Bingo stats file for benchmark {benchmark} and method {method} does not exist! Using original data.")
            stats_file = os.path.join(original_dir, stats_file_name)
        detected_bug = get_detected_bug_num(stats_file)
        if detected_bug < benchmark_statistics[benchmark]["bug_num"]:
            print(f"WARNING: Bingo has not finished for benchmark {benchmark} and method {method}! The result stat is not complete. Using original data.")
            stats_file = os.path.join(original_dir, stats_file_name)
        results.append(get_iters(stats_file))
        
    result_file.write(f"{benchmark},{results[0]:.1f},{results[1]:.1f},{results[2]:.1f},{results[3]:.1f},{results[4]:.1f},{results[5]:.1f}\n")
        
        
result_file.close()