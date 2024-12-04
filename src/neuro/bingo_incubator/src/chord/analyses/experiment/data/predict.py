from collections import defaultdict
from scipy.stats import bernoulli
from sklearn import svm
from sklearn import linear_model
import numpy as np

def getRelNameOnly(t):
	return t.partition("(")[0]

def hasHead(name):
	return (name.partition(":=")[0] != ":=")

def getHead(name):
	return name.partition(":=")[0] 

def handleBody(body):
	return ",".join(map(getRelNameOnly, body))

def generateName(constraint):
	if not hasHead(constraint):
		headString = "False"
		body1 = constraint.partition(":=")[1].partition("*")
		body2 = filter(lambda s: s!="*", body1)
		bodyString = handleBody(body2)
	else:
		headString = getRelNameOnly(constraint.partition(":=")[0])
		body1 = constraint.partition(":=")[2].partition("*")
		body2 = filter(lambda s: s!="*", body1)
		bodyString = handleBody(body2)
	return headString + ":=" + bodyString

def generateEmptyFeatures(names):
	features = defaultdict(int)
	for name in names:
		features[name] = 0
	return features

def updateTupleData(tupleData, constraint, constraintNames):
	if hasHead(constraint):
		head = getHead(constraint)
		name = generateName(constraint)
		if (tupleData[head] == {}):
			tupleData[head] = generateEmptyFeatures(constraintNames)
		tupleData[head][name] += 1

def getConstraintNamesFromFiles(filenames):
	constraintNameSet = set()
	for filename in filenames:
		fp = open(filename)
		lines = fp.readlines()
		targetRule = map(lambda line: line.split(": ")[0], lines)
		for constraint in targetRule:
			constraintNameSet.add(generateName(constraint))	
		fp.close()
	constraintNameList = list(constraintNameSet)
	constraintNameList.sort()
	print "[getConstraintNamesFromFiles]", constraintNameList
	print
	return constraintNameList

def readFromFile(filename, constraintNameSet):
	fp = open(filename)
	lines = fp.readlines()
	targetRule = map(lambda line: (line.split(": ")[0], int(line.split(": ")[1].strip())), lines)
	data = defaultdict(int)
	tupleData = defaultdict(dict)
	for constraint, numOcc in targetRule:
		name = generateName(constraint)
		data[name] += 1
		updateTupleData(tupleData, constraint, constraintNameSet)
	fp.close()
	return (data, tupleData)

def analyzeRule(trainRuleData, trainOracleRuleData):
	estimator = defaultdict(float)
	for name in trainRuleData:
		estimator[name] = (trainOracleRuleData[name] * 1.0 / trainRuleData[name])
	ruleNamesSorted = sorted(trainRuleData)
	for name in ruleNamesSorted:
		print "[analyzeRule]", name, "(", trainRuleData[name], ",", trainOracleRuleData[name], ",", estimator[name], ")"
	print 

def analyzeTuple(tupleData, oracleTupleData):
	relnameData1 = defaultdict(int)
	relnameData2 = defaultdict(int)
	relnameSet = set()
	for t in tupleData:
		relname = getRelNameOnly(t)
		features = tupleData[t]
		oracleFeatures = oracleTupleData[t]
		if (features != oracleFeatures):
			relnameData1[relname] += 1
		else:
			relnameData2[relname] += 1
		relnameSet.add(relname)
	for relname in relnameSet:
		print "[analyzeTuple]", relname, ":", "total -", relnameData1[relname] + relnameData2[relname], ",", \
			"diff -", relnameData1[relname], ",", "same -", relnameData2[relname]	
	print 
	return (relnameData1, relnameData2)	

def hasEnoughSignal(tupleData, oracleTupleData):
	featuresPos = set()
	negTuples = set()
	for t in tupleData:
		if (oracleTupleData[t] != {}): featuresPos.add(str(tupleData[t]))
		else: negTuples.add(t)
	neg = 0; overlap = 0
	for t in negTuples:
		neg += 1
		if (str(tupleData[t]) in featuresPos): overlap += 1
	print "[hasEnoughSignal]", "num of neg. examples:", neg, ",",  "num of over. examples: ", overlap
	

def learn(tupleData, oracleTupleData, select, ratio):
	X = list()
	y = list()
	for t in tupleData:
		if select(t):
			features = tupleData[t].values()
			r = bernoulli.rvs(ratio)
			if (oracleTupleData[t] == {}):
				X.append(features)
				y.append(-1)
			elif (r == 1):
				X.append(features)
				y.append(1)
	print "[learn] num of neg examples:", y.count(-1), ",", "num of pos examples:", y.count(1)
	print "[learn] fit start"
	clf = linear_model.Ridge(alpha=0.5)
	clf.fit(X,y)
	print "[learn] fit done"
	print "[learn] intercept:", clf.intercept_
	print "[learn] coeffs:", clf.coef_
	print 
	return clf

def test(trainTupleData, trainOracleTupleData, clf, select):
	X = list()
	y1 = list()
	for t in trainTupleData:
		if select(t):
			features = trainTupleData[t].values()
			X.append(features)
			if (trainOracleTupleData[t] == {}):
				y1.append(-1)
			else:
				y1.append(1)
	print "[test] num of neg examples:", y1.count(-1), ",", "num of pos examples: ", y1.count(1)
	print "[test] prediction start"
	y2 = clf.predict(X)
	print "[test] prediction done"
	print "[test] counting start"
	pos = 0; neg = 0; falsePos = 0; falseNeg = 0
	z = zip(y1,y2)
	for (v1,v2) in z:
		if (v1 > 0):
			pos += 1
		else:
			neg += 1
		if (v1 > 0 and v2 < 0):
			falsePos += 1
		elif (v1 <= 0 and v2 >= 0):
			falseNeg += 1
	print "[test] counting done"
	if (pos > 0.0):
		print "[test] falsePos/pos:", falsePos, "/", pos, "=", ((falsePos * 1.0) / pos)
	if (neg > 0.0):
		print "[test] falseNeg/neg:", falseNeg, "/", neg, "=", ((falseNeg * 1.0) / neg)
	if (pos + neg > 0.0):
		print "[test] falseTotal/total:", (falsePos + falseNeg), "/", (pos + neg), "=", (((falsePos + falseNeg) * 1.0) / (pos+neg))
	print 
	
def analyze(trainFile, oracleFile, constraintNames):
	(trainRuleData, trainTupleData) = readFromFile(trainFile, constraintNames)
	(oracleRuleData, oracleTupleData) = readFromFile(oracleFile, constraintNames)
	analyzeRule(trainRuleData, oracleRuleData)
	analyzeTuple(trainTupleData, oracleTupleData)
	return (trainTupleData, oracleTupleData)

def selectAll(t):
	return True

def selectRel(relname):
	return (lambda t: getRelNameOnly(t) == relname)

def analyzeAndLearn(trainFile, oracleFile):
	constraintNames = getConstraintNamesFromFiles([trainFile, oracleFile])
	(trainTupleData, oracleTupleData) = analyze(trainFile, oracleFile, constraintNames)
	hasEnoughSignal(trainTupleData, oracleTupleData)
	clf = learn(trainTupleData, oracleTupleData, selectAll, 1)
	test(trainTupleData, oracleTupleData, clf, selectAll)
	return (clf, constraintNames)

def analyzeAndTest(trainFile, oracleFile, clf, constraintNames):
	(trainTupleData, oracleTupleData) = analyze(trainFile, oracleFile, constraintNames)
	hasEnoughSignal(trainTupleData, oracleTupleData)
	test(trainTupleData, oracleTupleData, clf, selectAll)

print "================================"
print "[main] start of the analysis of hedc:"
print
(clf, constraintNames) = analyzeAndLearn("hedcConstraintItemsCounted.txt", "hedcOracleConstraintItemsCounted.txt")

print "================================"
print "[main] start of the analysis of antlr:"
print
analyzeAndTest("antlrConstraintItemsCounted.txt", "antlrOracleConstraintItemsCounted.txt", clf, constraintNames)
