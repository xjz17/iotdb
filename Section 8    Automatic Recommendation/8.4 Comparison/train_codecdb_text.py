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
from get_train_result_compress import get_ratio as gr 

from sklearn.metrics import confusion_matrix
from sklearn.metrics import classification_report
from sklearn.metrics import precision_score, recall_score, f1_score

from sklearn.neural_network import MLPClassifier

from sklearn.tree import DecisionTreeClassifier
from sklearn import tree


from sklearn.ensemble import RandomForestClassifier

""" label = ['GZIP+GORILLA','GZIP+PLAIN','GZIP+RAKE','GZIP+RLBE','GZIP+RLE','GZIP+SPRINTZ',
 'GZIP+TS_2DIFF','LZ4+TS_2DIFF','SNAPPY+SPRINTZ','SNAPPY+TS_2DIFF'] """
# label = ['GORILLA','RLBE','RLE','SPRINTZ','TS_2DIFF','RAKE','PLAIN']
# label = ['GORILLA','RLE','SPRINTZ','TS_2DIFF','PLAIN']
# vnames = [
# "DataType","Mean","Standard_variance","Spread","Delta_mean","Delta_variance","Delta_spread","Repeat","Increase"
# ]
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


def main():
    vnames = [
    "Exponent","Types",'Length','Repeat'
    ]
    label = ['PLAIN','HUFFMAN','RLE','DICTIONARY','MTF','BW','AC']
    path = "/home/srt_2022/exp_script/Section 8    Automatic Recommendation/Compare/train_all.csv"
    testdata = pd.read_csv(path)
    testdata.dropna(axis = 0,how="any",inplace=True)
    # path = dataset + '/' + dataFile
    del_list = []
    for index,row in testdata.iterrows():
        if random.random()>0.2 :
            del_list.append(index)
        # if random.random()>0.8 or row['Encoding']=="RLBE" or row['Encoding']=="RAKE":
        #     # print(index,row["DataSet"])
        #     del_list.append(index)

    # print(label)
    testdata.drop(index = del_list,inplace = True)

    # print(testdata.shape[0])

    x_test_path = testdata["DataSet"].to_numpy()
    encoding_t = testdata["Encoding"].to_numpy()
    y_test = encoding_t
    X_test = testdata[vnames].to_numpy()

    # print('#!',len(x_test_path),len(y_test))

    data = pd.read_csv(path)
    data.dropna(axis = 0,how="any",inplace=True)
    del_list = []
    for index,row in data.iterrows():
        if random.random()<=0.2 :
            del_list.append(index)

    data.drop(index = del_list,inplace = True)
    # print(data.shape[0])


    compressor = data["Compressor"].to_numpy()
    encoding = data["Encoding"].to_numpy()
    y = encoding
    # print(np.unique(y))
    X = data[vnames].to_numpy()
    X_train, y_train = X, y

    # X_train, X_test, y_train, y_test = train_test_split(X,y)



    # ----------------RF---------------------------

    compare_file = "/home/srt_2022/exp_script/Section 8    Automatic Recommendation/Compare/compare_TSEC415.csv"
    logger = open(compare_file, "w")
    # # logger.write("Dataset,Size,Data Extraction Time,Insert Time\n")
    # # logger.write("Recommender,Precision,Recall,F1,Average Ratio,Time Cost\n")
    logger.write("Recommender,Indicator,Value\n")
    time_start = time.time()

    pipeline = Pipeline([
        ('rf', RandomForestClassifier(n_estimators=300,criterion="entropy"))
    ])

    #param_dist = {'rf__n_estimators':range(250,350,10)}
    #param_dist = {  'rf__criterion': ['entropy'], 'rf__n_estimators':[300]}
    param_dist ={}
    grid = GridSearchCV(pipeline, param_dist, verbose=2,
                        refit=True, cv=2, n_jobs=-1,scoring="f1_weighted")
    grid.fit(X_train, y_train)
    print(grid.best_params_, grid.best_score_)
    time_end = time.time()
    time_cost = (time_end-time_start)
    # print('The accuracy of best model in RandomForest set is', grid.score(X_test, y_test))
    # print(X_test)
    pred = grid.predict(X_test)

    print("time cost of TSEC:",time_cost)
    # print(pred)
    pre, recall , f1 = print_metrices(pred, y_test,label)
    print(len(x_test_path),len(y_test),len(pred))
    avg_ratio,ratio_list = gr(x_test_path,y_test,pred)
    print("avg_ratio:",avg_ratio)
    # logger.write("{},{},{},{},{},{}\n".format("TSEC",pre, recall , f1, avg_ratio,time_cost))
    # print("{},{},{},{},{},{}\n".format("TSEC",pre, recall , f1, avg_ratio,time_cost))

    logger.write("{},{},{}\n".format("TSEC","Precision",pre))
    logger.write("{},{},{}\n".format("TSEC","Recall", recall ))
    logger.write("{},{},{}\n".format("TSEC","F1",f1))
    logger.write("{},{},{}\n".format("TSEC","Average Ratio",avg_ratio))
    logger.write("{},{},{}\n".format("TSEC","Time Cost",time_cost))

    plot_confusion_matrix(confusion_matrix(y_test, pred), target_names=label, normalize=False,
                        title='Confusion matrix of Random Forest on real-world data',save="rf_real")
    joblib.dump(grid, 'rf.model')

    # # ---------------------------DT---------------------------------
    time_start = time.time()

    pipeline = Pipeline([
        ('dt', DecisionTreeClassifier())
    ])

    param_dist = {  'dt__criterion': ['gini','entropy'],
                    'dt__max_depth':[1,2,3,4,5,6,7,8,9,None]}
    grid = GridSearchCV(pipeline, param_dist, verbose=2,
                        refit=True, cv=5, n_jobs=-1,scoring="f1_weighted")
    grid.fit(X_train, y_train)
    print(grid.best_params_, grid.best_score_)
    print('The accuracy of best model in DT set is', grid.score(X_test, y_test))

    pred = grid.predict(X_test)
    time_end = time.time()
    time_cost = (time_end-time_start)
    print("time cost of C-Store:",time_cost)
    pre, recall , f1 = print_metrices(pred, y_test,label)
    avg_ratio,ratio_list = gr(x_test_path,y_test,pred)
    print("avg_ratio:",avg_ratio)
    # logger.write("{},{},{},{},{},{}\n".format("TSEC",pre, recall , f1, avg_ratio,time_cost))
    print("{},{},{},{},{},{}\n".format("C-Store",pre, recall , f1, avg_ratio,time_cost))

    logger.write("{},{},{}\n".format("C-Store","Precision",pre))
    logger.write("{},{},{}\n".format("C-Store","Recall", recall ))
    logger.write("{},{},{}\n".format("C-Store","F1",f1))
    logger.write("{},{},{}\n".format("C-Store","Average Ratio",avg_ratio))
    logger.write("{},{},{}\n".format("C-Store","Time Cost",time_cost))


    print_metrices(pred, y_test,label)
    plot_confusion_matrix(confusion_matrix(y_test, pred), target_names=label, normalize=False,
                        title='Confusion matix of Decision Tree on test data',save="dt")
    time_end = time.time()
    print("time cost of DT:", (time_end-time_start)/10)

    # ---------------------------------------------------------------------


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

    # # -----------------------GDBT------------------------

    # pipeline = Pipeline([
    #     ('gbc', GradientBoostingClassifier(n_estimators=100,max_depth=7))
    # ])

    # #param_dist = {  'gbc__n_estimators': range(10,100,10),'gbc__max_depth': [2,3,4,5,6,7,8,None]}
    # param_dist = {}

    # grid = GridSearchCV(pipeline, param_dist, verbose=2,
    #                     refit=True, cv=2,n_jobs=-1,scoring="f1_weighted")
    # grid.fit(X_train, y_train)
    # print(grid.best_params_, grid.best_score_)
    # print('The accuracy of best model in BalancedBagging set is',
    #       grid.score(X_test, y_test))

    # pred = grid.predict(X_test)
    # print_metrices(pred, y_test)
    # plot_confusion_matrix(confusion_matrix(y_test, pred), target_names=label, normalize=False,
    #                       title='Confusion matix of Gradient Boosting on test data',save="gdbt")
    # joblib.dump(grid, 'gdbt.model')

    # time_end = time.time()
    # print("time cost of GDBT:", (time_end-time_start)/10)



    # # ---------------------------------nouse--------------------------

    # pipeline =pipeline = Pipeline([

    # ('lr', LogisticRegression(class_weight='balanced'))

    # ])



    # param_dist = { 'lr__penalty': ['l2', 'elasticnet'],

    # 'lr__C':np.logspace(-3,3,5)}

    # #param_dist = {}

    # grid = GridSearchCV(pipeline, param_dist, verbose=2,

    # refit=True, cv=3, n_jobs=-1,scoring="f1_weighted")

    # grid.fit(X_train, y_train)

    # print(grid.best_params_, grid.best_score_)

    # print('The accuracy of best model in LogisticRegression set is', grid.score(X_test, y_test))



    # pred = grid.predict(X_test)

    # print_metrices(pred, y_test)

    # plot_confusion_matrix(confusion_matrix(y_test, pred), target_names=label, normalize=False,

    # title='Confusion matix of LogisticRegression on test data')



    # pipeline =pipeline = Pipeline([

    #  ('lr', SVC())

    # ])



    # param_dist = {'lr__C':np.logspace(-3,3,7)}

    # #param_dist = {}
    # grid = GridSearchCV(pipeline, param_dist, verbose=2,

    # refit=True, cv=3, n_jobs=-1,scoring="f1_weighted")

    # grid.fit(X_train, y_train)

    # print(grid.best_params_, grid.best_score_)

    # print('The accuracy of best model in LogisticRegression set is', grid.score(X_test, y_test))



    # pred = grid.predict(X_test)

    # print_metrices(pred, y_test)

    # plot_confusion_matrix(confusion_matrix(y_test, pred), target_names=label, normalize=False,

    # title='Confusion matix of LogisticRegression on test data')



    # from sklearn.neural_network import MLPClassifier



    # pipeline = Pipeline([

    #   ('gbc', MLPClassifier(hidden_layer_sizes=80,activation='logistic',alpha=0.1))

    # ])



    # param_dist = {  'gbc__hidden_layer_sizes': range(10,100,5),

    # 'gbc__activation' : ['identity', 'logistic', 'tanh', 'relu']}

    # param_dist = {'gbc__alpha' : np.logspace(-4,4,9)}

    # param_dist = {}





    # grid = GridSearchCV(pipeline, param_dist, verbose=2,
    # refit=True, cv=3, n_jobs=-1,scoring="f1_weighted")
    # grid.fit(X_train, y_train)
    # print(grid.best_params_, grid.best_score_)
    # print('The accuracy of best model in BalancedBagging set is',
    # grid.score(X_test, y_test))
    # pred = grid.predict(X_test)
    # print_metrices(pred, y_test)
    # plot_confusion_matrix(confusion_matrix(y_test, pred), target_names=label, normalize=False,
    # title='Confusion matix of MLP on val data',save="mlp")





    # from sklearn.naive_bayes import GaussianNB

    # pipeline = Pipeline([
    # ('GNB', GaussianNB())
    # ])
    # param_dist={}
    # grid = GridSearchCV(pipeline, param_dist, verbose=2,
    # refit=True, cv=5, n_jobs=-1,scoring="f1_weighted")
    # grid.fit(X_train, y_train)
    # print(grid.best_params_, grid.best_score_)
    # print('The accuracy of best model in SVC set is', grid.score(X_test, y_test))
    # pred = grid.predict(X_test)
    # print_metrices(pred, y_test)
    # plot_confusion_matrix(confusion_matrix(y_test, pred), target_names=label, normalize=False,
    # title='Confusion matix of Decision Tree on test data',save="GNB")
    # time_end = time.time()
    # print("time cost of GNB:", (time_end-time_start)/10)