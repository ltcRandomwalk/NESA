from collections import defaultdict

def readFromFile(filename):
  fp = open(filename)
  lines = fp.readlines()
  relationNames = map(lambda line: line.partition('(')[0], lines)
  data = defaultdict(int)
  for name in relationNames:
    data[name] += 1
  fp.close()
  return data

trainData = readFromFile('antlrFile.txt')
trainOracleData = readFromFile('antlrOracleFile.txt')
estimator = defaultdict(float)
for name in trainData:
  estimator[name] = (trainOracleData[name] * 1.0 / trainData[name])
for name in trainData:
  print name, trainData[name], trainOracleData[name], estimator[name]
print "\n"

testData = readFromFile('hedcFile.txt')
testOracleData = readFromFile('hedcOracleFile.txt')
error = defaultdict(float)
for name in testData:
  error[name] = ((testOracleData[name] * 1.0 / testData[name]) - estimator[name])
for name in testData:
  print name, testData[name], testOracleData[name], error[name]
print "\n"
