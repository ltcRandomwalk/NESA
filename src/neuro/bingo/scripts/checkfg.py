#!/usr/bin/env python3

testfile = '../bingo_test1/bnet/noaugment_base/factor-graph.fg'
inFile = open(testfile, 'r')
line = inFile.readline()
factorNum = int(line)
print(factorNum)

line = inFile.readline()
line = inFile.readline()
maxvarNum = 0
count = 0
while(line):
    count += 1
    varnum = int(line)
    if varnum > maxvarNum:
        maxvarNum = varnum
    varNames = inFile.readline()
    bitCount = inFile.readline()
    numValue = int(inFile.readline())
    for i in range(numValue):
        inFile.readline()
    inFile.readline()
    line = inFile.readline()
    if numValue == 0:
        print(varNames)


print(count)
print(maxvarNum)
    