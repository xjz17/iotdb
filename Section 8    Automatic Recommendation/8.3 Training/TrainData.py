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
from textwrap import indent
from matplotlib.pyplot import axes
import pandas as pd
import numpy as np
import scipy.stats as ss

import re
import os
import time
from pathlib import Path

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



RESULT = "./train_real.csv"
STORAGE_PATH = "/home/srt_2022/apache-iotdb-1.0.0-all-bin/apache-iotdb-1.0.0-all-bin/data/data/sequence/root.test/0/0"

ip = "127.0.0.1"
port_ = "6667"
username_ = 'root'
password_ = 'root'
session = Session(ip, port_, username_, password_)
session.open(False)
session.execute_non_query_statement(
    "delete storage group root.test"
)
session.set_storage_group("root.test")
print("Set storage finished")
logger = open(RESULT, "a+")
logger.write(
    "DataType,Mean,Standard_variance,Spread,Delta_mean,Delta_variance,Delta_spread,Repeat,Increase,Compressor,Encoding\n") 

dir = ["int","long","float","double"]
for whichdir in range(4):
    SOURCE_DIR = "/home/srt_2022/client-py/data/learn/{}".format(dir[whichdir])
    '''
    p = Path(SOURCE_DIR)
    for child in p.iterdir():
        if "result" in str(child):
            continue
    '''
    fileList = os.listdir(str(SOURCE_DIR))
    count = 0
    for dataFile in fileList:
        print(dataFile)
        path = str(SOURCE_DIR) + '/' + dataFile
        if whichdir == 0:
            data_type = TSDataType.INT32
        elif whichdir == 1:
            data_type = TSDataType.INT64
        elif whichdir == 2:
            data_type = TSDataType.FLOAT
        elif whichdir == 3:
            data_type = TSDataType.DOUBLE
        data = pd.read_csv(str(path))
        device = "root.test.t2"
        time_list = [x for x in data["Sensor"]]
        value_list = [[x] for x in data["s_0"]]
        measurements = ["s_0"]
        if whichdir == 0 or whichdir == 2:
            spacelen = 12
        else:
            spacelen = 16
        orginal_data_size = spacelen*len(time_list)
        data_types = [data_type]
        encodings = [TSEncoding.RAKE, TSEncoding.RLE, TSEncoding.TS_2DIFF, TSEncoding.GORILLA,
                    TSEncoding.PLAIN, TSEncoding.SPRINTZ, TSEncoding.RLBE, ]
        compressors = [Compressor.UNCOMPRESSED]
        min_ratio = 1
    
        for compressor in compressors:
            for encoding in encodings:
                tablet = Tablet(device, measurements, data_types,
                                value_list, time_list)
                session.execute_non_query_statement(
                    "set system to writable"
                )
                session.execute_non_query_statement(
                    "delete timeseries root.test.t2.s_0"
                )
                session.execute_non_query_statement(
                    "create timeseries root.test.t2.s_0 with datatype={},encoding={},compressor={}".format(
                        data_type.name, encoding.name, compressor.name)
                )
                session.insert_tablet(tablet)
                session.execute_non_query_statement(
                    "flush"
                )
                data_path = os.listdir(STORAGE_PATH)
                compressed_size = 0
                for filename in data_path:
                    if re.match(".+\\.tsfile", filename):
                        f = open(STORAGE_PATH + "/" + filename, 'rb')
                        compressed_size += len(f.read())
                ratio = float(compressed_size)/orginal_data_size
                if min_ratio > ratio:
                    min_ratio = ratio
                    best_encoding = encoding
                    best_compressor = compressor
        count += 1
        print("{},{}\n".format(100*count/len(fileList),dir[whichdir]))
        Sata = data["s_0"].to_numpy()
        res = statistic(Sata)
        logger.write("{},{},{},{}\n".format(data_type.name, res, best_compressor.name,best_encoding.name))