# Figure 8a: Inversion counts for pointer analysis
import os
import subprocess

statistic_name = "median rank"
output_file_name = "median-pts"
benchmarks = ["moldyn", "montecarlo", "weblech", "toba-s","hedc","jspider","javasrc-p","ftp"]

artifact_root_dir = os.getenv("ARTIFACT_ROOT_DIR")
reproduced_dir = os.path.join(artifact_root_dir, "reproduced_results")
original_dir = os.path.join(artifact_root_dir, "original_results")

def parse_ranking(file_path: str):
    statistics = dict()
    with open(file_path, 'r') as f:
        lines = f.readlines()
    statistics["inversion count"] = int(lines[-5].strip().split()[-1])
    statistics["mean rank"] = float(lines[-4].strip().split()[-1])
    statistics["median rank"] = float(lines[-2].strip().split()[-1])
    statistics["inference time"] = float(lines[-1].strip().split()[-1])
    
    return statistics

def format_number(num):
    if abs(num) >= 1000000:
        formatted = f"{num/1000000:.3g}m"
    elif abs(num) >= 1000:
        formatted = f"{num/1000:.3g}k"
    else:
        formatted = f"{num:.3g}"
    return formatted

csv_output = open(os.path.join(reproduced_dir, f"RQ1/{output_file_name}.CSV"), "w")
top_output = open(os.path.join(reproduced_dir, f"RQ1/{output_file_name}.top"), "w")
csv_output.write(f"{statistic_name},baseline,our approach,oracle\n")

for benchmark in benchmarks:
    csv_output.write(f"{benchmark},")
    results = []
    for rank_file in ["rank-baseline.txt", "rank-our-approach-small.txt", "rank-oracle.txt"]:
        reproduced_ranking = os.path.join(reproduced_dir, f"neuro/pts/{benchmark}/{rank_file}")
        if not os.path.isfile(reproduced_ranking):
            print(f"WARNING: Missing data {benchmark}/{rank_file}! Using original data.")
            reproduced_ranking = os.path.join(original_dir, f"neuro/pts/{benchmark}/{rank_file}")
        statistics = parse_ranking(reproduced_ranking)
        results.append(statistics[statistic_name])
    
    csv_output.write(f"1,{results[1]/results[0]},{results[2]/results[0]}\n")
    top_output.write(f"{format_number(results[0])} ")
    
csv_output.close()
top_output.close()
    
        
        