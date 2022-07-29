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

# Note: the reason of calculating the features directly:
#         According to the algorithm of generating text data,
#         the input arguements are exactly the features after
#         data generation. Thus we directly use calculated feature
#         values to reduce time cost of this experiment.
def statistic(feature, fold):
    if feature == "Class":
        exp = 0
        leng = 1000
        rep = 0.5
        typ = fold*150
        if typ == 0:
            typ = 1
        return "{},{},{},{}".format(exp,typ,leng,rep)
    if feature == "Exponent":
        exp = fold + 2
        leng = 100
        rep = 0.5
        typ = 750
        return "{},{},{},{}".format(exp,typ,leng,rep)
    if feature == "Length":
        exp = 0
        Len = round(100*(fold+1))
        rep = 0.5
        typ = 2
        return "{},{},{},{}".format(exp,typ,Len,rep)
    if feature == "Repeat":
        exp = 0
        leng = 1000
        rep = (fold/10)*(0.1)+0.9
        typ = 2
        return "{},{},{},{}".format(exp,typ,leng,rep)



STORAGE_PATH = "/home/srt_2022/apache-iotdb-0.13.0-SNAPSHOT-all-bin/apache-iotdb-0.13.0-SNAPSHOT-all-bin/data/data/sequence/root.test/0/0" ###

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
# dirs=["Length"]
# dirs=["Class","Exponent","Repeat"]
# dirs=["Exponent"]
dirs=["Class","Exponent","Length","Repeat"]
RESULT1_PATH = "/home/srt_2022/client-py/text_feature/text_ratio_result414.csv"  ###
logger = open(RESULT1_PATH, "w")
logger.write("Datatype,Compression,Encoding,Exponent,Types,Length,Repeat,Compression Ratio\n")
REPEAT_TIME = 1 
for dir in dirs:
    dataset = "/home/srt_2022/client-py/text_feature/TEXT_synthetic_data414/{}".format(dir) ###
    fileList = os.listdir(dataset)
    for dataFile in fileList:
        fold = int(dataFile)
        path_fold = dataset + '/' + dataFile
        run_path = os.listdir(path_fold)
        
        data_types = [TSDataType.TEXT]
        # encodings = [TSEncoding.DICTIONARY]
        encodings = [TSEncoding.RLE,TSEncoding.PLAIN,TSEncoding.HUFFMAN,TSEncoding.DICTIONARY]
        compressors = [Compressor.UNCOMPRESSED]
        for compressor in compressors:
            for encoding in encodings:
                ratio = 0
                count = 1 

                res = statistic(dir, fold)                
                for path_per in run_path:
                    path = str(path_fold) + '/' + path_per
                    data = pd.read_csv(str(path),encoding='utf-8',dtype = {'Sensor':np.int64,'s_0':str}) 
                    data.dropna(inplace=True)
                    device = "root.test.t1"
                    time_list = [x for x in data["Sensor"]]
                    value_list = [[x] for x in data["s_0"]]

                    orginal_data_size = len(time_list)*8
                    for values in value_list:
                        for value in values:
                            orginal_data_size += len(value)                    
                    measurements = ["s_0"]
                    # different compression combinations
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
                        "create timeseries root.test.t1.s_0 with datatype=TEXT,encoding={},compressor={}".format(
                            encoding.name, compressor.name)
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
                    print(compressed_size)
                    print(orginal_data_size)
                    ratio += float(compressed_size)/orginal_data_size
                    count += 1
                    if count > REPEAT_TIME:
                        ratio /= REPEAT_TIME
                        if compressor.name == "UNCOMPRESSED":
                            logger.write("{},{},{},{},{}\n".format(path, "NONE", encoding.name, res, ratio))
                        else:
                            logger.write("{},{},{},{},{}\n".format(path, compressor.name, encoding.name, res, ratio))
                        print("{},{},{},{},{}\n".format(path, compressor.name, encoding.name, res, ratio))
                        break
                    session.close()

logger.close()

