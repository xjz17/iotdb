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
        return exp,typ,leng,rep
    if feature == "Exponent":
        exp = fold + 2
        leng = 100
        rep = 0.5
        typ = 750
        return exp,typ,leng,rep
    if feature == "Length":
        exp = 0
        Len = round(100*(fold+1))
        rep = 0.5
        typ = 2
        return exp,typ,leng,rep
    if feature == "Repeat":
        exp = 0
        leng = 1000
        rep = (fold/10)*(0.1)+0.9
        typ = 2
        return exp,typ,leng,rep

print("Start.")
STORAGE_PATH = "/home/srt_2022/apache-iotdb-0.13.0-SNAPSHOT-all-bin/data/data/sequence/root.test/0/0" ###
REPEAT_TIME = 20
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


dt = [[TSDataType.FLOAT,TSDataType.DOUBLE],[TSDataType.INT32,TSDataType.INT64]]

new_dirs=["Class","Exponent","Length","Repeat"]
RESULT1_PATH = "./data/learn/new_result_time_text_4_8.csv"  ###
logger = open(RESULT1_PATH, "w")
logger.write("DataFile,Compression,Encoding,Exponent,Types,Length,Repeat,Increase,Select Time,Insert Time\n")
for dir in new_dirs:
    dataset = "/home/srt_2022/client-py/data/TEXT_synthetic_data/{}".format(dir) ###
    fileList = os.listdir(dataset)
    for dataFile in fileList:
        fold = int(dataFile)
        path_fold = dataset + '/' + dataFile
        run_path = os.listdir(path_fold)
        # tsdt = 

        # for data_type in tsdt:              
            # if data_type == TSDataType.INT32 or data_type == TSDataType.FLOAT:
            #     point_size = 4
            # else:
            #     point_size = 8
        data_types = [TSDataType.TEXT]
        encodings = [TSEncoding.DICTIONARY,TSEncoding.HUFFMAN,TSEncoding.RLE,TSEncoding.PLAIN]
        compressors = [Compressor.UNCOMPRESSED,Compressor.GZIP,Compressor.LZ4,Compressor.SNAPPY]
        for compressor in compressors:
            for encoding in encodings:        
                insert_time = 0
                select_time = 0  
                count = 0      
                for path_per in run_path:
                    count += 1
                    if count >= REPEAT_TIME:
                        break
                    path = str(path_fold) + '/' + path_per
                    # print(path)
                    data = pd.read_csv(str(path))
                    device = "root.test.t1"
                    time_list = [x for x in data["Sensor"]]
                    value_list = [x for x in data["s_0"]]
                    # orginal_data_size = len(value_list)*(8+point_size)
                    measurements = ["s_0"]
                    # min_ratio = 1
                    Sata = data["s_0"].to_numpy()

                    tablet = Tablet(device, measurements, data_types,value_list, time_list)

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

                    time_start = time.time()
                    session.insert_tablet(tablet)
                    time_end = time.time()
                    insert_time += time_end-time_start

                    time_start = time.time()
                    session_data_set = session.execute_query_statement(
                        "select s_0 from root.test.t1")
                    time_end = time.time()
                    session_data_set.close_operation_handle()
                    select_time += time_end-time_start
                insert_time /= REPEAT_TIME
                select_time /= REPEAT_TIME
                res = statistic(dir, fold)
                if compressor.name == "UNCOMPRESSED":
                    logger.write("{},{},{},{},{},{}\n".format(dataFile, "NONE", encoding.name, res, select_time,insert_time))
                else:
                    logger.write("{},{},{},{},{},{}\n".format(dataFile, compressor.name, encoding.name, res, select_time,insert_time))
                print("{},{},{},{},{},{}\n".format(dataFile, compressor.name, encoding.name, res, select_time,insert_time))
                # counter+=1
logger.close()
session.close()
