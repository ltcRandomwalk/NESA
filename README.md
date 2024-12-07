# NESA - Neural-Symbolic Static Analysis

---

## This repository is built for Combining Formal and Informal Information in Bayesian Program Analysis via Soft Evidences.

## Part I: Installation and Setup

### Step 1. Setting Python ENV
```bash
curl -LO https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh 

bash Miniconda3-latest-Linux-x86_64.sh -b -u

source ~/miniconda3/bin/activate conda init bash

conda create -n nesa python=3.8 -y

conda activate nesa

pip install -r requirements.txt
```

### Step 2. Installation of Java
To run the experiment, JDK version 1.6 and 1.11 are required. 

#### (1) Installing JDK 1.6

You can download and install Java JDK 1.6 on <https://www.oracle.com/hk/java/technologies/javase-java-archive-javase6-downloads.html>. After the installation, set the environment variables:

```bash
export JAVA_HOME={Path to your Java 1.6 main folder}
export PATH=${JAVA_HOME}/bin:${PATH}
```

#### (2) Installing JDK 1.11

You can download and install Java JDK 1.11 on <https://www.oracle.com/hk/java/technologies/javase/jdk11-archive-downloads.html>. After the installation, open `src/neuro/neuroanalysis/Driver/config.py`, and set `java11_path` to the Java 1.11 executable file. (e.g. /usr/lib/jvm/java-11-openjdk-amd64/bin/java)

### Step 3. Installation of Apache Ant

Download and install Apache Ant version 1.9.16 from <https://archive.apache.org/dist/ant/binaries/>. After the installatio, set the environment variables:

```bash
export ANT_HOME={Path to your Apache Ant 1.9.16 main folder}
export PATH=${PATH}:${ANT_HOME}/bin
```

### Step 4. Building Bingo

First, install the following libraries:

```bash
sudo apt install libboost-dev libboost-program-options-dev libboost-test-dev libgmp-dev
```

Next, build Bingo in the source code:

```bash
cd src/dynamic/bingo
./scripts/build.sh
cd ../../neuro/bingo  # src/neuro/bingo
./scripts/build.sh
```

### Step 5. Getting Benchmarks and Data

Due to the storage limit of Anonymous Github, we set our benchmarks and data used in the evaluation on <http://surl.li/xqwfve>.

You should copy the `benchmarks/` and `data/` folder to the NESA main folder.

### Step 6. Setting Environment Variables

Set the environment variables as follows. Note that the environment variable `ARTIFACT_ROOT_DIR` should be set to the absolute path to NESA's main folder.

For the environment variable `GRAPHCODEBERT_DIR`, you can also download graphcodebert-base from <https://huggingface.co/microsoft/graphcodebert-base>, and set it to the local path of the model. 

We recommend running Graphcodebert by GPU. However, if you want to run it by CPU, please change the environment variable `DEFAULT_DEVICE` to `cpu`.

```bash
export ARTIFACT_ROOT_DIR={Path to the repository main folder}
export CHORD_MAIN=$ARTIFACT_ROOT_DIR/src/neuro/jchord/main
export CHORD_INCUBATOR=$ARTIFACT_ROOT_DIR/src/neuro/bingo_incubator
export CHORD_INCUBATOR_ABS=$ARTIFACT_ROOT_DIR/src/neuro/bingo_incubator_abs
export PJBENCH=$ARTIFACT_ROOT_DIR/benchmarks/pjbench
export PAG_BENCH=$ARTIFACT_ROOT_DIR/benchmarks/pjbench

export GRAPHCODEBERT_DIR=microsoft/graphcodebert-base
export DEFAULT_DEVICE=cuda:1
export MLN=1
```

## Part II: Running All Experiments

### RQ1-2. 

To get the alarm rankings of a pointer analysis benchmark (e.g. `moldyn`), run:

```bash
cd src/neuro/neuroanalysis/Driver

python driver.py -lspbr moldyn small
```

For a taint analysis benchmark (e.g. `app-324`), run:

```bash
cd src/neuro/neuroanalysis/Driver

python taintdriver.py -blr app-324 ICCmodel
```

The benchmarks for the pointer analysis lie in `benchmarks/pjbench`, while those for the taint analysis lie in `benchmarks/android_bench`. The rankings will be stored in `chord_output_mln-{pts/taint}-problem/` in the corresponding benchmark folder. `rank-baseline.txt`, `rank-our-approach-\*.txt`, and `rank-oracle.txt` represent the results of the baseline approach, our approach and the oracle, respectively. At the bottom of each ranking file, the inversion count, mean rank, and median rank are listed. The inference time is listed here as well.

To run all pointer analysis experiments in the background, run:

```bash
./scripts/exp-pts-all.sh
```

To run all taint analysis experiments in the background, run:

```bash
./scripts/exp-taint-all.sh
```


---

### RQ3.

To run Bingo on a pointer analysis benchmark (e.g. `moldyn`), run:

```bash
cd src/neuro/neuroanalysis/Driver

python driver.py -n moldyn 

```

The result ranking will be stored as `chord_output_mln-pts-problem/naivebingo.txt` in the corresponding benchmark folder. **The Bingo experiment can take extremely long time, it is possible to stop it during the process, and the result will be updated in time.** In our experiment, we set a time limit of one week.

To run all Bingo experiments, run:

```bash
./scripts/exp-bingo-pts.sh
```

---

### RQ4. 

**Note: Before running the experiments in this part, please make sure the experiment in RQ1-2 is finished, which can provide the analysis frontend results.**

To get the alarm ranking for a pointer analysis benchmark (e.g. `moldyn`) using a fine-tuned model (e.g. `toba-s+hedc`), run:

```bash
cd src/neuro/neuroanalysis/Driver

python driver.py -pb moldyn toba-s+hedc
```

There are four available models: `toba-s+hedc`, `ftp+moldyn`, `javasrc-p+montecarlo`, and `weblech+jspider`, which are all stored in the `data/alias_model/` folder.

The rankings will be stored as `chord_output_mln-pts-problem/rank-our-approach-{model_name}.txt` in the corresponding benchmark folder.

To run all sensitivity experiments in the background, run:

```bash
./scripts/sensitivity1.sh
./scripts/sensitivity2.sh
./scripts/sensitivity3.sh
./scripts/sensitivity4.sh
```

**Note: For the above four scripts, please make sure that the tasks in one script are all finished before running another one.**


### Dynamic Information Experiment

To get the result for a dynamic analysis benchmark (e.g. `cflow-1.5`), run:

```bash
cd src/dynamic/dynaboost

source ./init.sh

cd dfsan-plugin

./runbingo.sh cflow-1.5 ${ARTIFACT_ROOT_DIR}/data/dynamic/cflow-1.5/ interval cflow 

```

The results will be stored in `src/dynamic/bingo/`. `cflowbaseline-stats.txt`, `cflowNESA-stats.txt`, and `cflowDynaboost-stats.txt` represent the result of the baseline approach, our approach and Dynaboost, respectively. The total line of the rank stands for the number of iterations required by a user to identify all true alarms. The initial rankings are the files whose suffix has "combined0.out". The average rank of true alarms without user feedback can be calculated there.

To run all dynamic information experiment in the background, run:

```bash
./scripts/exp-dynamic-all.sh
```

## Part III: Evaluation Results

All of our evaluation results are stored in `/results` folder. 

- To check the overall results, please refer to `results/statistics`. The CSV files include statiscal results of all experiments, such as inversion count, mean rank and median rank.
- To check the exact results of a pointer or taint analysis benchmark, please refer to `results/neuro`. The results are in the specific benchmark folder, including the rankings in RQ1-4.
- To check the exact results of a dynamic analysis benchmark, please refer to `results/dynamic`. The result ranking is named with the benchmark name, 


In `/results` folder, the experiment result figures in our paper can be reproduced.

- To reproduce Figure 8, go to `./RQ1` and run `./plot-figure8.sh`. The figures will be stored in `./RQ1/figs`.
- To reproduce Figure 9-10, go to `./RQ1` and run `./plot-all-boxplot.sh`. The figures will be stored in `./RQ1/boxplot`.
- To reproduce Figure 11, go to `./RQ3` and run `./plot-all-RQ3.sh`. The figures will be stored in `./RQ3/figs`.
- To reproduce Figure 12-14, go to `./RQ4` and run  `./plot-all-RQ4.sh`. The figures will be stored in `./benchmarks/RQ4-figs`.









