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
from math import log
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
    N = 0
    repeat = 0
    length = 0
    tot = 0
    dic = {}
    dic_time = []
    dic_time_fst = 0
    dic_time_sec = 0
    # cnt=0
    for ln in data:
        # cnt+=1
        # if(cnt%100==0):
        #     print(cnt)
        ln = "".join(ln)
        flag = 0
        # hln=hash(ln)
        j=dic.get(ln)
        if(j!=None):
            flag = 1
            dic_time[j] += 1
            if dic_time[j] > dic_time_fst:
                dic_time_sec = dic_time_fst
                dic_time_fst = dic_time[j]
            elif dic_time[j] <= dic_time_fst and dic_time[j] > dic_time_sec:
                dic_time_sec = dic_time[j]
        if flag == 0:
            N += 1
            dic[ln]=len(dic_time)
            dic_time.append(1)
            j = len(dic) - 1
            if dic_time[j] > dic_time_fst:
                dic_time_sec = dic_time_fst
                dic_time_fst = dic_time[j]
            elif dic_time[j] <= dic_time_fst and dic_time[j] > dic_time_sec:
                dic_time_sec = dic_time[j]
        tot += len(ln)
        for i in range(len(ln)):
            if i!=0 and ln[i] == ln[i-1]:
                repeat += 1
                # print(repeat)
    theta = log(dic_time_fst/dic_time_sec).real / log(2).real
    return "{},{},{},{}".format(theta, N, repeat/tot, tot/len(data))



RESULT = "./Section 8    Automatic Recommendation/8.3 Training/train.csv"
STORAGE_PATH = "/home/srt_2022/apache_iotdb_bin/apache-iotdb-1.0.0-all-bin/data/data/sequence/root.test/0/0"

ip = "127.0.0.1"
port_ = "6667"
username_ = 'root'
password_ = 'root'
logger = open(RESULT, "w")
logger.write(
    "DataType,Exponent,Types,Length,Repeat,Compressor,Encoding\n") 

SOURCE_DIR = "/home/srt_2022/exp_script/Section 8    Automatic Recommendation/8.3 Training/train_data"
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
    data_type = TSDataType.TEXT
    data = pd.read_csv(str(path),encoding='utf-8',dtype = {'Sensor':np.int64,'s_0':str},error_bad_lines=False, engine="python")
    device = "root.test.t1"
    time_list = [x for x in data["Sensor"]]
    value_list = [[x] for x in data["s_0"]]
    measurements = ["s_0"]
    orginal_data_size = len(time_list)*8
    for values in value_list:
        for value in values:
            orginal_data_size += len(value)
    data_types = [data_type]
    encodings = [TSEncoding.PLAIN,TSEncoding.HUFFMAN,TSEncoding.RLE,TSEncoding.DICTIONARY,TSEncoding.MTF,TSEncoding.BW,TSEncoding.AC]
    compressors = [Compressor.UNCOMPRESSED]
    min_ratio = 1

    for compressor in compressors:
        for encoding in encodings:
            session = Session(ip, port_, username_, password_)
            session.open(False)
            session.execute_non_query_statement(
                "delete storage group root.*"
            )
            session.set_storage_group("root.test")

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
                if re.match(".+\\.tsfile", filename):
                    f = open(STORAGE_PATH + "/" + filename, 'rb')
                    compressed_size += len(f.read())
            ratio = float(compressed_size)/orginal_data_size
            # print('{}:{}\n'.format(encoding.name,ratio))
            if min_ratio > ratio:
                min_ratio = ratio
                best_encoding = encoding
                best_compressor = compressor
    count += 1
    print("{},{}\n".format(100*count/len(fileList),'TEXT'))
    Sata = data["s_0"].to_numpy()
    res = statistic(Sata)
    logger.write("{},{},{},{}\n".format(data_type.name, res, best_compressor.name,best_encoding.name))