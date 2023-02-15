import itertools

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import time
import random


from sklearn.svm import SVC
from sklearn.neural_network import MLPClassifier
from sklearn.linear_model import SGDClassifier, LogisticRegression
from sklearn.ensemble import GradientBoostingClassifier,RandomForestClassifier
from sklearn.model_selection import GridSearchCV,train_test_split
from sklearn.pipeline import Pipeline
import joblib
# from get_train_result_compress import get_ratio as gr 
from cmath import log
from sklearn.metrics import confusion_matrix
from sklearn.metrics import classification_report
from sklearn.metrics import precision_score, recall_score, f1_score
from get_train_result_compress import get_ratio as gr 

def sortedness(data):
    if len(data) <= 1:
        return data,0
    index = len(data) // 2
    lst1 = data[:index]
    lst2 = data[index:]
    left,n1 = sortedness(lst1)
    right,n2 = sortedness(lst2)
    sorted,num = merge(left,right)
    return sorted,n1+n2+num

def merge(lst1, lst2):
    """to Merge two list together"""
    list = []
    num = 0
    while len(lst1) > 0 and len(lst2) > 0:
        data1 = lst1[0]
        data2 = lst2[0]
        if data1 <= data2:
            list.append(lst1.pop(0))
        else:
            num += len(lst1)
            list.append(lst2.pop(0))
    if len(lst1) > 0:
        list.extend(lst1)
    else:
        list.extend(lst2)
    return list,num

def cal_sortedness(data):
    _,inverted_pair = sortedness(data)
    total_pair = len(data) * (len(data) - 1) / 2
    ratio = (total_pair - inverted_pair) / total_pair
    ivpair = 1 - abs(2*ratio - 1)
    ktau = (total_pair - 2 * inverted_pair) / total_pair
    df = pd.Series(data)
    df = df.dropna()
    index = [x for x in range(1, len(data)+1)]
    index = pd.Series(index)
    srho = df.corr(index,method='spearman')
    print(ivpair)
    print(ktau)
    print(srho)
    return ivpair, ktau, srho

def cal_feature(data):
    N = 0
    repeat = 0
    length = len(data)
    tot = 0
    dic = []
    dic_time = []
    dic_pro = []
    last = float(np.inf)
    for ln in data:
        flag = 0
        if ln in dic:
            flag = 1
            j = dic.index(ln)
            dic_time[j] += 1
        if flag == 0:
            N += 1
            dic.append(ln)
            dic_time.append(1)
    tot = len(dic)
    entropy = 0
    for v in dic_time:
        p = v / length
        # print(p)
        entropy += -p * log(p,2).real
    ivpair, ktau, srho = cal_sortedness(data)
    return "{},{},{},{},{}\n".format(tot/length,entropy,ivpair,ktau,srho)



# path = "/home/srt_2022/client-py/train/Real-world_data/DOUBLE/Chemistry/d0.csv"
# data = pd.read_csv(str(path))
# time_list = [x for x in data["Sensor"]]
# value_list = [[x] for x in data["s_0"]]
# print(cal_feature(value_list))



vnames = [
"Cardinality Ratio","Entropy","Ivpair","Ktau","Srho"
]
def plot_confusion_matrix(cm,
                          target_names,
                          title='Confusion matrix',
                          cmap=None,
                          normalize=True,
                          save = "result.eps"):

    accuracy = np.trace(cm) / float(np.sum(cm))
    misclass = 1 - accuracy

    if cmap is None:
        cmap = plt.get_cmap('Blues')

    plt.figure(figsize=(5, 4))
    plt.imshow(cm, interpolation='nearest', cmap=cmap)
    plt.title(title)
    plt.colorbar()

    if target_names is not None:
        tick_marks = np.arange(len(target_names))
        plt.xticks(tick_marks, target_names, rotation=45)
        plt.yticks(tick_marks, target_names)

    if normalize:
        cm = cm.astype('float') / cm.sum(axis=1)[:, np.newaxis]


    thresh = cm.max() / 1.5 if normalize else cm.max() / 2
    for i, j in itertools.product(range(cm.shape[0]), range(cm.shape[1])):
        if normalize:
            plt.text(j, i, "{:0.4f}".format(cm[i, j]),
                     horizontalalignment="center",
                     color="white" if cm[i, j] > thresh else "black")
        else:
            plt.text(j, i, "{:,}".format(cm[i, j]),
                     horizontalalignment="center",
                     color="white" if cm[i, j] > thresh else "black")


    plt.tight_layout()
    plt.ylabel('True label')
    #plt.xlabel('Predicted label\naccuracy={:0.4f}; misclass={:0.4f}'.format(accuracy, misclass))
    plt.xlabel('Predicted label')
    # plt.savefig(save,format='eps',dpi = 40,bbox_inches='tight')
    plt.savefig(save+".eps",format='eps',dpi = 400,bbox_inches='tight')
    plt.savefig(save+".png", dpi = 400,bbox_inches='tight')
    plt.show()

def print_metrices(pred, true,label):
    # print(confusion_matrix(true, pred))
    # print(classification_report(true, pred, target_names=label,digits=4))
    pre = precision_score(
        true, pred, average='weighted',labels=label)
    recall = recall_score(true, pred,  average='weighted',labels=label)
    f1 = f1_score(true, pred, average='weighted',labels=label)
    print("Weighted Precison : ", pre)
    print("Weighted Recall : ", recall)
    print("F1 : ", f1)
    return pre,recall,f1

# data = pd.read_csv("data/train.csv")
# data.dropna(axis = 0,how="any",inplace=True)
# data.info()
# compressor = data["Compressor"].to_numpy()
# encoding = data["Encoding"].to_numpy()
# y = encoding
# print(np.unique(y))
# X = data[vnames].to_numpy()
# X_train, y_train = X, y

# testdata = pd.read_csv("data/train_real.csv")
# testdata.dropna(axis = 0,how="any",inplace=True)
# encoding_t = testdata["Encoding"].to_numpy()
# y_test = encoding_t
# X_test = testdata[vnames].to_numpy()

label = []
testdata = pd.read_csv("/home/srt_2022/client-py/compare/train_data414.csv")

testdata.dropna(axis = 0,how="any",inplace=True)
# path = dataset + '/' + dataFile
print(testdata.shape[0])
del_list = []

for index,row in testdata.iterrows():
    if  row['Encoding']=="RLBE" or row['Encoding']=="RAKE":
        del_list.append(index)
    elif random.random()>0.2 :
        # print(index,row["DataSet"])
        del_list.append(index)
    elif row['Encoding'] not in label:
        label.append(row['Encoding'])

# print(label)
testdata.drop(index = del_list,inplace = True)

print(testdata.shape[0])

x_test_path = testdata["DataSet"].to_numpy()
encoding_t = testdata["Encoding"].to_numpy()
y_test = encoding_t
X_test = testdata[vnames].to_numpy()

data = pd.read_csv("/home/srt_2022/client-py/compare/train_data414.csv")
data.dropna(axis = 0,how="any",inplace=True)
del_list = []
for index,row in data.iterrows():
    if  row['Encoding']=="RLBE" or row['Encoding']=="RAKE":
        del_list.append(index)
    elif random.random()<=0.2 :
        # print(index,row["DataSet"])
        del_list.append(index)

data.drop(index = del_list,inplace = True)
print(data.shape[0])


compressor = data["Compression"].to_numpy()
encoding = data["Encoding"].to_numpy()
y = encoding
# print(np.unique(y))
X = data[vnames].to_numpy()
X_train, y_train = X, y







# # ---------------------------------nouse--------------------------

compare_file = "/home/srt_2022/client-py/compare/compare_CodecDB415.csv"
logger = open(compare_file, "w")
# logger.write("Dataset,Size,Data Extraction Time,Insert Time\n")
logger.write("Recommender,Indicator,Value\n")
# logger.write("Recommender,Precision,Recall,F1,Average Ratio,Time Cost\n")

from sklearn.neural_network import MLPClassifier

time_start = time.time()
pipeline = Pipeline([
  ('gbc', MLPClassifier(hidden_layer_sizes=(500,500),solver='adam',learning_rate_init=0.01,activation='tanh',alpha=0.9,beta_2=0.999))
])

# param_dist = {  'gbc__hidden_layer_sizes': range(10,100,5),
# 'gbc__activation' : ['identity', 'logistic', 'tanh', 'relu']}
# param_dist = {'gbc__alpha' : np.logspace(-4,4,9)}
param_dist = {}


grid = GridSearchCV(pipeline, param_dist, verbose=2,
refit=True, cv=3, n_jobs=-1,scoring="f1_weighted")
grid.fit(X_train, y_train)
time_end = time.time()
time_cost = (time_end-time_start)
# print(grid.best_params_, grid.best_score_)
# print('The accuracy of best model in BalancedBagging set is',
# grid.score(X_test, y_test))
pred = grid.predict(X_test)

print("time cost of CodecDB:",time_cost)
pre, recall , f1 = print_metrices(pred, y_test,label)
avg_ratio,ratio_list = gr(x_test_path,y_test,pred)
print("avg_ratio:",avg_ratio)

print("{},{},{},{},{},{}\n".format("CodecDB",pre, recall , f1, avg_ratio,time_cost))
# logger.write("{},{},{},{},{},{}\n".format("CodecDB",pre, recall , f1, avg_ratio,time_cost))
logger.write("{},{},{}\n".format("CodecDB","Precision",pre))
logger.write("{},{},{}\n".format("CodecDB","Recall", recall ))
logger.write("{},{},{}\n".format("CodecDB","F1",f1))
logger.write("{},{},{}\n".format("CodecDB","Average Ratio",avg_ratio))
logger.write("{},{},{}\n".format("CodecDB","Time Cost",time_cost))
# plot_confusion_matrix(confusion_matrix(y_test, pred), target_names=label, normalize=False,
# title='Confusion matix of MLP on val data',save="mlp")
