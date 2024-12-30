FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

RUN sed -i 's|http://archive.ubuntu.com/ubuntu|http://mirrors.aliyun.com/ubuntu|g' /etc/apt/sources.list && \
    sed -i 's|http://security.ubuntu.com/ubuntu|http://mirrors.aliyun.com/ubuntu|g' /etc/apt/sources.list

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN apt-get update && apt-get install -y \
    wget \
    g++ \
    make \
    build-essential \
    bc

RUN apt-get update && apt-get install -y \
    libboost-dev \
    libboost-program-options-dev \
    libboost-test-dev \
    libgmp-dev

#RUN useradd -ms /usr/bin/bash -l -u $USER_ID user
RUN useradd -m user

RUN mkdir -p /home/user
RUN chown -R user:user /home/user

# Install Conda
ENV CONDA_DIR=/home/user/miniconda3
RUN mkdir -p $CONDA_DIR
RUN wget https://repo.anaconda.com/miniconda/Miniconda3-py38_4.12.0-Linux-x86_64.sh -O $CONDA_DIR/miniconda.sh
RUN bash $CONDA_DIR/miniconda.sh -b -u -p $CONDA_DIR
RUN rm -f $CONDA_DIR/miniconda.sh
ENV PATH=$CONDA_DIR/bin:$PATH

# Install Conda packages
RUN conda install -c conda-forge python=3.8

USER root
RUN mkdir -p /usr/lib/jvm

COPY java-packages/jdk-6u45-linux-x64.bin /usr/lib/jvm

# Install JDK 1.6.0_45
RUN cd /usr/lib/jvm && \
    chmod +x jdk-6u45-linux-x64.bin && \
    ./jdk-6u45-linux-x64.bin && \
    rm jdk-6u45-linux-x64.bin
ENV JAVA_HOME=/usr/lib/jvm/jdk1.6.0_45
ENV PATH=$JAVA_HOME/bin:$PATH

# Install openjdk-11
RUN apt-get install -y openjdk-11-jdk && \
    update-alternatives --install /usr/bin/java java /usr/lib/jvm/java-11-openjdk-amd64/bin/java 2
ENV JAVA11_PATH=/usr/lib/jvm/java-11-openjdk-amd64/bin/java

# Install Apache Ant 1.9.16
RUN wget https://archive.apache.org/dist/ant/binaries/apache-ant-1.9.16-bin.tar.gz -O /tmp/apache-ant.tar.gz
RUN mkdir -p /usr/lib/ant
RUN tar -xzf /tmp/apache-ant.tar.gz -C /usr/lib/ant --strip-components=1
ENV ANT_HOME=/usr/lib/ant
ENV PATH=$ANT_HOME/bin:$PATH
RUN ant -version
RUN rm /tmp/apache-ant.tar.gz

# Set Environment Variables
COPY --chown=user:user . /home/user/NESA
WORKDIR /home/user/NESA

USER user

RUN echo 'export CHORD_MAIN=/home/user/NESA/src/neuro/jchord/main' >> ~/.bashrc
RUN echo 'export CHORD_INCUBATOR=/home/user/NESA/src/neuro/bingo_incubator' >> ~/.bashrc
RUN echo 'export CHORD_INCUBATOR_ABS=/home/user/NESA/src/neuro/bingo_incubator_abs' >> ~/.bashrc
RUN echo 'export PJBENCH=/home/user/NESA/benchmarks/pjbench' >> ~/.bashrc
RUN echo 'export PAG_BENCH=/home/user/NESA/benchmarks/pjbench' >> ~/.bashrc
RUN echo 'export ARTIFACT_ROOT_DIR=/home/user/NESA' >> ~/.bashrc
RUN echo 'export GRAPHCODEBERT_DIR=microsoft/graphcodebert-base' >> ~/.bashrc
RUN echo 'export DEFAULT_DEVICE=cuda:1' >> ~/.bashrc
RUN echo 'export MLN=1' >> ~/.bashrc
RUN echo 'export PATH="$PATH:/home/user/.local/bin"' >> ~/.bashrc

RUN cd ~/NESA
RUN pip install -r requirements.txt