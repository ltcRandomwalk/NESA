import argparse
#from config import *
import os
import subprocess

base_bench_path = os.path.join(os.getenv("ARTIFACT_ROOT_DIR"), "data/stamp-benches")
bench_path_map = { 'andors-trail': 'DarpaApps/1A_AndorsTrail/AndorsTrail.apk', 'app-018': 'SymcDir/SymcApks/018ee7f7fffd7323d7b771935382a1a6.apk',
					'app-324': 'SymcDir/SymcApks/324357f628d534eeae1674e6c3af9f3d4fad3e0dda5bc3fb782f1ed3b9a37dd8.apk',
     				'app-ca7': 'SymcDir/SymcApks/ca7b267e7898662ca5b74e55660413af.apk',
         			'app-kQm': 'SymcDir/SymcApks/kQm-LOfpwpJ.apk',
            		'ginger-master': 'SymcDir/SymcApks/gingermaster.apk',
              		'noisy-sounds': 'SymcDir/SymcApks/com-noisysounds.apk', 'tilt-mazes': 'SymcDir/SymcApks/com-gp-tiltmazes.apk',
                    'AC4': 'ActivityCommunication4.apk',
                    'VideoActivity': 'DarpaApps/2A_VideoGame/VideoActivity.apk'}
bench_path_map = { p: os.path.join(base_bench_path, bench_path_map[p]) for p in bench_path_map }

parser = argparse.ArgumentParser(description="run a Bayesian analysis with soft evidence")

parser.add_argument("bench", help="benchmark name")
parser.add_argument("-f", "--frontend", help="run front-end analysis through jchord", action="store_true")
parser.add_argument("-c", "--bancicm", help="run javaparser and CodeGraphBERT to calculate similarity scoares of var names", action="store_true")
parser.add_argument("-i", "--genICC", help="run the backend bayesian analysis", action="store_true")
parser.add_argument("-l", "--labelAlias", help="produce labels for alias tuples", action="store_true")
parser.add_argument("-r", "--runlabels", action="store_true")
parser.add_argument("-a", "--allhard", action="store_true")
parser.add_argument("-t", "--traindata", action="store_true")
parser.add_argument("-p", "--predictprob", action="store_true")
parser.add_argument("-n", "--naivebingo", action="store_true")
parser.add_argument("-q", "--softbingo", action="store_true")

args = parser.parse_args()

bench_path = bench_path_map[args.bench]
list_stamp_path = os.path.join(os.getenv("ARTIFACT_ROOT_DIR"), "src/neuro/scripts/stamp/list_stamp.sh")  #artifact/Error-Ranking/chord-fork/scripts/stamp/list_stamp.sh'
stamp_output_base_path = os.path.join(os.getenv("ARTIFACT_ROOT_DIR"), 'data/android_bench')
output_dir = os.path.join(stamp_output_base_path, args.bench)
oracle_path = os.path.join(output_dir, 'chord_output_mln-taint-oracle')
problem_path = os.path.join(output_dir, 'chord_output_mln-taint-problem')
oracle_cons_all_path = os.path.join(oracle_path, 'named_cons_all.txt')
oracle_ban_cicm_path = os.path.join(oracle_path, 'named_cons_ban.txt')
oracle_ban_query_path = os.path.join(oracle_path, 'banned_queries.txt')
ICC_name_path = os.path.join(oracle_path, "ICCNames.txt")
oracle_ICC_file_path = os.path.join(oracle_path, "oracle_ICCNames.txt")
ICC_name_problem_path = os.path.join(problem_path, "ICCNames.txt")
oracle_ICC_name_problem_path = os.path.join(problem_path, "oracle_ICCNames.txt")
oracle_CICM_path = os.path.join(oracle_path, "cicmNames.txt")
ban_query_path = os.path.join(problem_path, 'oracle_queries.txt')





def run_frontend():
    if subprocess.call(f"{list_stamp_path} {args.bench} {bench_path}", shell=True):
        exit(1)
    if subprocess.call(f"cp {os.path.join(oracle_path, 'oracle_queries.txt')} {os.path.join(problem_path, 'oracle_queries.txt')}", shell=True):
        exit(1)

def ban_cicm():
    scriptFile = './scripts/cicm-ban.py'
    if subprocess.call(f"python {scriptFile} {oracle_cons_all_path} {oracle_ban_cicm_path} {oracle_ban_query_path}", shell=True):
        exit(1)
        
def gen_ICC():
    scriptFile = './scripts/gen-ICC.py'
    if subprocess.call(f"python {scriptFile} {oracle_CICM_path} {ICC_name_path} {oracle_ICC_file_path}", shell=True):
        exit(1)
    subprocess.call(f"cp {ICC_name_path} {ICC_name_problem_path}", shell=True)
    subprocess.call(f"cp {oracle_ICC_file_path} {oracle_ICC_name_problem_path}", shell=True)
    scriptFile = './scripts/cicm-ban.py'
    if subprocess.call(f"python {scriptFile} {oracle_cons_all_path} {oracle_ban_cicm_path} {oracle_ban_query_path} {ICC_name_path} {oracle_ICC_file_path}", shell=True):
        exit(1)
    subprocess.call(f"cp {oracle_ban_query_path} {ban_query_path}", shell=True)


def main():
    if args.frontend:
        run_frontend()
    if args.genICC:
        gen_ICC()
    



if __name__ == "__main__":
    main()
