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
    return "{},{},{},{},{},{},{},{}".format(
        ave,std,std_spread,
        diff_ave,diff_std,diff_spread,
        repeat,sort
    )

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
RESULT1_PATH = "result_delta_variance_ratio.csv"  ###
logger = open(RESULT1_PATH, "w")
logger.write("Datatype,Compression,Encoding,Compression_Ratio\n")
    
for dir in dirs:
    dataset = "../../Section 6    Encoding Benchmark/Datasets/Synthetic Datasets/Numerical Data/Delta variance/{}".format(dir) ###
    fileList = os.listdir(dataset)
    for dataFile in fileList:
        path = dataset + '/' + dataFile
        data = pd.read_csv(str(path))
        device = "root.test.t1"
        time_list = [x for x in data["Sensor"]]
        value_list = [[x] for x in data["s_0"]]
        measurements = ["s_0"]
        tsdt = [TSDataType.INT32]

        for data_type in tsdt:              ###
            # calculate the uncompressed data size
            if data_type == TSDataType.INT32 or data_type == TSDataType.FLOAT:
                point_size = 4
            else:
                point_size = 8
            orginal_data_size = len(value_list)*(8+point_size)
            # different compression combinations
            data_types = [data_type]
            encodings = [TSEncoding.RAKE,TSEncoding.RLE,TSEncoding.TS_2DIFF,TSEncoding.GORILLA,
            TSEncoding.PLAIN,TSEncoding.SPRINTZ,TSEncoding.RLBE]
            compressors = [Compressor.UNCOMPRESSED,Compressor.GZIP,Compressor.LZ4,Compressor.SNAPPY]
            min_ratio = 1
            Sata = data["s_0"].to_numpy()
            for compressor in compressors:
                for encoding in encodings:
                    tablet = Tablet(device, measurements, data_types,
                                    value_list, time_list)
                    session.execute_non_query_statement(
                        "set system to writable"
                    )
                    session.execute_non_query_statement(
                        "delete timeseries root.test.t1.s_0"
                    )
                    session.execute_non_query_statement(
                        "create timeseries root.test.t1.s_0 with datatype={},encoding={},compressor={}".format(
                            data_type.name, encoding.name, compressor.name)
                    )
                    session.insert_tablet(tablet)
                    session.execute_non_query_statement(
                        "flush"
                    )
                    data_path = os.listdir(STORAGE_PATH)
                    compressed_size = 0
                    for filename in data_path:
                        if re.match(".+\\.tsfile",filename):
                            f = open(STORAGE_PATH + "/" + filename,'rb')
                            compressed_size += len(f.read())
                    ratio = float(compressed_size)/orginal_data_size
                    res = statistic(Sata)
                    logger.write("{},{},{},{},{},{}\n".format(dataFile, data_type.name, res, compressor.name,
                                                            encoding.name,ratio))
                    print("{},{},{},{},{},{}\n".format(dataFile, data_type.name, res, compressor.name,
                                                            encoding.name,ratio))
                    counter+=1

logger.close()
session.close()
