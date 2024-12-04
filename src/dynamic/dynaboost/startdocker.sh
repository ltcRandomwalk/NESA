. init.sh
docker run --network=host --cap-add=SYS_PTRACE -it \
--mount type=bind,source=/tmp,target=/tmp \
--mount type=bind,source=$PWD,target=/home/ubuntu/dynamicbingo \
--mount type=bind,source=$PWD/../bingo-ci-experiment,target=/home/ubuntu/bingo-ci-experiment \
--mount type=bind,source=$LLVM_ROOT,target=/tmp/llvm-project,readonly \
--mount type=bind,source=$PWD/../vanilla-experiment,target=/home/ubuntu/vanilla-experiment \
-w /home/ubuntu/dynamicbingo \
dynamicbingo:latest
