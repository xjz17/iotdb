# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

# encoding:utf-8
import time
from pathlib import Path
import os
import re

import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from sklearn import datasets
import scipy.stats as ss

from iotdb.Session import Session
from iotdb.utils.IoTDBConstants import TSDataType, TSEncoding, Compressor
from iotdb.utils.Tablet import Tablet

def entropy(data):
    prob = []
    length = len(data)
    for i in np.unique(data):
        prob.append(np.sum(data == i)/float(length))
    return ss.entropy(prob)

def repeat_words(data,limit=8):
    lenth = len(data)
    index = 0
    key = data[0]
    count = 0
    for val in data:
        if val == key:
            count+=1
        else:
            if count >= limit:
                index += count
            key=val
            count=0
    return float(index)/lenth

'''
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
'''
def sortedness(data):
    length = len(data)
    count = 0
    for i in range(length-1):
        if data[i]<=data[i+1]:
            count += 1
    return count/(length-1)


# def merge(lst1, lst2):
#     """to Merge two list together"""
#     list = []
#     num = 0
#     while len(lst1) > 0 and len(lst2) > 0:
#         data1 = lst1[0]
#         data2 = lst2[0]
#         if data1 <= data2:
#             list.append(lst1.pop(0))
#         else:
#             num += len(lst1)
#             list.append(lst2.pop(0))
#     if len(lst1) > 0:
#         list.extend(lst1)
#     else:
#         list.extend(lst2)
#     return list,num

def statistic(data):
    ave = np.nanmean(data, axis=0)
    
    std = np.nanstd(data, axis=0)
    Min = np.nanmin(data, axis=0)
    Max = np.nanmax(data, axis=0)
    std_spread = Max-Min
    diff_data = np.diff(data, axis=0)
    diff_ave = np.nanmean(diff_data, axis=0)
    diff_min = np.nanmin(diff_data, axis=0)
    diff_max = np.nanmax(diff_data, axis=0)
    diff_spread = diff_max-diff_min
    diff_std = np.nanstd(diff_data, axis=0)
    repeat = repeat_words(data)
    sort = sortedness(data.tolist())
    return "{},{},{},{},{},{},{},{}".format(
        ave,std,std_spread,
        diff_ave,diff_std,diff_spread,
        repeat,sort
    )
print("Start.")
STORAGE_PATH = "/home/srt_2022/apache-iotdb-0.13.0-SNAPSHOT-all-bin/apache-iotdb-0.13.0-SNAPSHOT-all-bin/data/data/sequence/root.test/0/0"

ip = "127.0.0.1"
port_ = "6667"
username_ = 'root'
password_ = 'root'
session = Session(ip, port_, username_, password_)
session.open(False)
session.execute_non_query_statement(
    "delete storage group root.*"
)
session.set_storage_group("root.test")

RESULT1_PATH = "/home/srt_2022/client-py/import_time/time_real_world_insert_extraction.csv"  ###
logger = open(RESULT1_PATH, "w")
# logger.write("Dataset,Size,Data Extraction Time,Insert Time\n")
logger.write("Dataset,Size,Time type,Time Cost\n")

dataset = "/home/srt_2022/client-py/import_time/Real-world_data/INT32" ###
fileList = os.listdir(dataset)
count = 0

for dataFile in fileList:
    count += 1
    print(count)
    path = dataset + '/' + dataFile  + '/' + dataFile + ".csv"
    data = pd.read_csv(str(path),encoding='utf-8')
    data.dropna(inplace=True)
    device = "root.test.t1"
    time_list = [x for x in data["Sensor"]]
    value_list = [[x] for x in data["s_0"]]
    
    Sata = data["s_0"].to_numpy()
    orginal_data_size = len(value_list)*(8+4)
    print(orginal_data_size)

    for s in range(1,11):
        size = s*100000
        time_list_tmp = time_list[:size]
        value_list_tmp = value_list[:size]
        Sata = data["s_0"].to_numpy()[:size]
        print(size)

        time_start = time.time()
        for i in range(10):
            res = statistic(Sata)
        time_end = time.time()
        extraction_time = (time_end-time_start)/10
        print("{},{},Data Feature Collection Time,{}\n".format(dataFile,size,extraction_time))
        logger.write("{},{},Data Feature Collection Time,{}\n".format(dataFile,size,extraction_time))        


        tsdt = [TSDataType.INT32]
        encodings = [TSEncoding.RAKE,TSEncoding.RLE,TSEncoding.TS_2DIFF,TSEncoding.GORILLA,
                TSEncoding.PLAIN,TSEncoding.SPRINTZ,TSEncoding.RLBE]
        compressors = [Compressor.UNCOMPRESSED]#,Compressor.GZIP,Compressor.LZ4,Compressor.SNAPPY]
        insert_time = 100
        for compressor in compressors:
            for encoding in encodings:
                device = "root.test.t1"
                measurements = ["s_0"]
                tablet = Tablet(device, measurements, tsdt,value_list_tmp, time_list_tmp)

                session.execute_non_query_statement(
                    "set system to writable"
                )
                session.execute_non_query_statement(
                    "delete timeseries root.test.t1.s_0"
                )
                session.execute_non_query_statement(
                    "create timeseries root.test.t1.s_0 with datatype=INT32,encoding={},compressor={}".format(
                        encoding.name, compressor.name)
                )
                time_start = time.time()
                session.insert_tablet(tablet)
                time_end = time.time()
                if insert_time > time_end-time_start :
                    insert_time = time_end-time_start
        print("{},{},Insert Time,{}\n".format(dataFile,size,insert_time))
        logger.write("{},{},Insert Time,{}\n".format(dataFile,size,insert_time))  


        # print("{},{},{},{}\n".format(dataFile,size,extraction_time,insert_time))
        # logger.write("{},{},{},{}\n".format(dataFile,size,extraction_time,insert_time))

    # 
    # per_time = 1000000 * 16 * cal_time/orginal_data_size 
    # print("{},INT32,{},{},{}\n".format(path,res,cal_time,per_time))
    # logger.write("{},INT32,{},{},{}\n".format(path,res,cal_time,per_time))