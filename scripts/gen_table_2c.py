import os
import subprocess

statistic_name = "inversion count"
output_file_name = "inversion-pts"
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

def getTimeFromBingo(filepath, maxAlarm=200):
    f = open(filepath, 'r')
    #fw = open(outpath, 'w')
    lines = f.readlines()
    trueRank = []
    times = 0
    numTrue = 0
    cur_line = 0
    for _ in range(1, len(lines)):
        cur_line += 1
        line = lines[cur_line]
        _, _, Ground, _, _, _, _, _, Time = line.split()
        Time = float(Time)
        times += Time
        #if times > timeout:
            #break
        if cur_line > maxAlarm:
            break
        if Ground.startswith('True'):
            trueRank.append(cur_line)
            numTrue += 1
        #print(numTrue, file=fw)
    f.close()
    if cur_line <= maxAlarm:
        return 0
   # fw.close()
    return times  

csv_output = open(os.path.join(reproduced_dir, f"RQ3/table_2c.csv"), "w")
csv_output.write(",Bingo,Our Approach\n")

for benchmark in benchmarks:
    csv_output.write(f"{benchmark},")
    results = []
    
    # get the time of Bingo
    reproduced_ranking = os.path.join(reproduced_dir, f"neuro/pts/{benchmark}/naivebingo.txt")
    if not os.path.isfile(reproduced_ranking):
        print(f"WARNING: Missing data {benchmark}/naivebingo.txt! Using original data.")
        reproduced_ranking = os.path.join(original_dir, f"neuro/pts/{benchmark}/naivebingo.txt")
    results.append(getTimeFromBingo(reproduced_ranking))
    if results[0] == 0:
        csv_output.write("timeout,")
    else:
        csv_output.write(f"{results[0]:.2f},")
    
    
    for rank_file in ["rank-our-approach-small.txt"]:   # get the time of our approach
        reproduced_ranking = os.path.join(reproduced_dir, f"neuro/pts/{benchmark}/{rank_file}")
        if not os.path.isfile(reproduced_ranking):
            print(f"WARNING: Missing data {benchmark}/{rank_file}! Using original data.")
            reproduced_ranking = os.path.join(original_dir, f"neuro/pts/{benchmark}/{rank_file}")
        statistics = parse_ranking(reproduced_ranking)
        results.append(statistics["inference time"])
    
    csv_output.write(f"{results[1]:.2f}\n")
    
    
csv_output.close()
