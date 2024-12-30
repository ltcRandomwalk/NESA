import os
import subprocess

statistic_name = "mean rank"
output_file_name = "mean"
benchmarks = ["moldyn", "montecarlo", "weblech", "toba-s","hedc","jspider","javasrc-p","ftp"]
models = ["small", "ftp+moldyn", "javasrc-p+montecarlo", "weblech+jspider", "toba-s+hedc"]

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

for benchmark in benchmarks:
    os.makedirs(os.path.join(reproduced_dir, f"neuro/pts/{benchmark}"), mode=0o777, exist_ok=True)
    csv_output = os.path.join(reproduced_dir, f"neuro/pts/{benchmark}/{output_file_name}.CSV")
    output_file = open(csv_output, "w")
    output_file.write(f"{statistic_name},count\n")
    for model in models:
        if benchmark in model:
            continue
        reproduced_ranking = os.path.join(reproduced_dir, f"neuro/pts/{benchmark}/rank-our-approach-{model}.txt")
        if not os.path.isfile(reproduced_ranking):
            print(f"WARNING: Missing data {benchmark}/rank-our-approach-{model}.txt! Using original data.")
            reproduced_ranking = os.path.join(original_dir, f"neuro/pts/{benchmark}/rank-our-approach-{model}.txt")
        statistics = parse_ranking(reproduced_ranking)
        output_file.write(f"{model},{statistics[statistic_name]}\n")
    output_file.close()
        
        
        