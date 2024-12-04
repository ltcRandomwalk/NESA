# NESA - Neural-Symbolic Static Analysis

---

## This repository is built for Combining Formal and Informal Information in Bayesian Program Analysis via Soft Evidences.

## Part I: Installation and Setup

### Step 1. Setting Python ENV
```
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

You can download and install Java JDK 1.6 on https://www.oracle.com/hk/java/technologies/javase-java-archive-javase6-downloads.html. After the installation, set the environment variables:

```
export JAVA_HOME={Path to your Java 1.6 main folder}
export PATH=${JAVA_HOME}/bin:${PATH}
```

#### (2) Installing JDK 1.11

You can download and install Java JDK 1.11 on https://www.oracle.com/hk/java/technologies/javase/jdk11-archive-downloads.html. After the installation, open `src/neuro/neuroanalysis/Driver/config.py`, and set `java11_path` to the Java 1.11 executable file. (e.g. /usr/lib/jvm/java-11-openjdk-amd64/bin/java)

### Step 3. Installation of Apache Ant

Download and install Apache Ant version 1.9.16 from https://archive.apache.org/dist/ant/binaries/. After the installatio, set the environment variables:

```
export ANT_HOME={Path to your Apache Ant 1.9.16 main folder}
export PATH=${PATH}:${ANT_HOME}/bin
```

### Step 4. Building Bingo

First, install the following libraries:

```
sudo apt install libboost-dev libboost-program-options-dev libboost-test-dev libgmp-dev
```

Next, build Bingo in the source code:

```
cd src/dynamic/bingo
./scripts/build.sh
cd ../../neuro/bingo  # src/neuro/bingo
./scripts/build.sh
```

### Step 5. Getting Benchmarks and Data

Due to the storage limit of Anonymous Github, we set our benchmarks and data used in the evaluation on ***URL***.

You should copy the `benchmarks` and `data` folder to the NESA main folder.

### Step 6. Setting Environment Variables

Set the environment variables as follows. Note that the environment variable `ARTIFACT_ROOT_DIR` should be set to the absolute path to NESA's main folder.

For the environment variable `GRAPHCODEBERT_DIR`, you can also download graphcodebert-base from https://huggingface.co/microsoft/graphcodebert-base, and set it to the local path of the model. 

We recommend running Graphcodebert by GPU. However, if you want to run it by CPU, please change the environment variable `DEFAULT_DEVICE` to `cpu`.

```
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
