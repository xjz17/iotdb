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
    return ave,std,std_spread,diff_ave,diff_std,diff_spread,repeat,sort



STORAGE_PATH = "../../iotdb/data/data/sequence/root.test/0/0"

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

#logger = open(RESULT1_PATH, "w")
#logger.write("DataSet,DataType,Average,Standard_Variance,MAD,Average_diff,Variance_diff,Spread_diff,Entropy,Repeat_words,Increase,Encoding,Compression_Ratio\n")

counter = 0
p = ["/home/srt_2022/client-py/data/Reverse_double",
"/home/srt_2022/client-py/data/generate_data/generate_data_int_long"]
dt = [[TSDataType.FLOAT,TSDataType.DOUBLE],[TSDataType.INT32,TSDataType.INT64]]

""" for i in range(2):
    dataset = p[i]
    DataType = dt[i] """
dirs = ["INT32"]
RESULT1_PATH = "result_compression_ratio_for_each_dataset.csv"  ###
logger = open(RESULT1_PATH, "w")
logger.write("Dataset,Encoding,Value mean,Standard variance,Value spread,Delta mean,Delta variance,Delta spread,Repeat,Increase,Compression Ratio\n")
encodings = [TSEncoding.RAKE, TSEncoding.RLE, TSEncoding.TS_2DIFF, TSEncoding.GORILLA,
             TSEncoding.PLAIN, TSEncoding.SPRINTZ, TSEncoding.RLBE]
for dir in dirs:
    type_fol = "../../Section 6    Encoding Benchmark/Datasets/Real-world Datasets/Numerical Data/{}".format(dir) ###
    fileList = os.listdir(type_fol)

    for dataset in fileList:
        inner_fileList = os.listdir(type_fol + '/' + str(dataset))
        for encoding in encodings:
            count = 0
            Comp_ratio = 0
            Ave = 0
            Std = 0
            Std_spread = 0
            Diff_ave = 0
            Diff_std = 0
            Diff_spread = 0
            Repeat = 0
            Sort = 0
            for dataFile in inner_fileList:
                count += 1
                path = type_fol + '/' + str(dataset) + '/' + dataFile
                data = pd.read_csv(str(path))
                device = "root.test.t1"
                time_list = [x for x in data["Sensor"]]
                value_list = [[x] for x in data["s_0"]]
                measurements = ["s_0"]
                data_type = TSDataType.INT32
                if data_type == TSDataType.INT32 or data_type == TSDataType.FLOAT:
                    point_size = 4
                else:
                    point_size = 8
                orginal_data_size = len(value_list) * (8 + point_size)
                # different compression combinations
                min_ratio = 1
                Sata = data["s_0"].to_numpy()
                tablet = Tablet(device, measurements, [TSDataType.INT32],
                                value_list, time_list)
                session.execute_non_query_statement(
                    "set system to writable"
                )
                session.execute_non_query_statement(
                    "delete timeseries root.test.t1.s_0"
                )
                session.execute_non_query_statement(
                    "create timeseries root.test.t1.s_0 with datatype={},encoding={},compressor={}".format(
                        data_type.name, encoding.name, Compressor.UNCOMPRESSED)
                )
                session.insert_tablet(tablet)
                session.execute_non_query_statement(
                    "flush"
                )
                data_path = os.listdir(STORAGE_PATH)
                compressed_size = 0
                time.sleep(1)
                for filename in data_path:
                    if re.match(".+\\.tsfile", filename):
                        f = open(STORAGE_PATH + "/" + filename, 'rb')
                        compressed_size += len(f.read())
                ratio = float(compressed_size) / orginal_data_size
                ave,std,std_spread,diff_ave,diff_std,diff_spread,repeat,sort = statistic(Sata)
                Comp_ratio += ratio
                Ave += ave
                Std += std
                Std_spread += std_spread
                Diff_ave += diff_ave
                Diff_std += diff_std
                Diff_spread += diff_spread
                Repeat += repeat
                Sort += sort
            Comp_ratio /= count
            Ave /= count
            Std /= count
            Std_spread /= count
            Diff_ave /= count
            Diff_std /= count
            Diff_spread /= count
            Repeat /= count
            Sort /= count
            logger.write("{},{},{},{},{},{},{},{},{},{},{}\n".format(dataset,encoding.name,Ave,Std,Std_spread,Diff_ave,Diff_std,Diff_spread,Repeat,Sort,Comp_ratio))
            print("{},{},{},{},{},{},{},{},{},{},{}".format(dataset,encoding.name,Ave,Std,Std_spread,Diff_ave,Diff_std,Diff_spread,Repeat,Sort,Comp_ratio))


logger.close()
session.close()
