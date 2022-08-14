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

from iotdb.Session import Session
from iotdb.utils.IoTDBConstants import TSDataType, TSEncoding, Compressor
from iotdb.utils.Tablet import Tablet

TEST_SIZE = 100000
REPEAT_TIME = 10
RESULT_PATH = "./result_ingestion.csv"

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

logger = open(RESULT_PATH, "w")
logger.write("DataSet,DataType,Compressor,Encoding,Insert Time,Select Time\n")

p = Path("./ingestion")
for child in p.iterdir():
    dataset = str(child)
    print(dataset)
    fileList = os.listdir(dataset)
    for dataFile in fileList:
        DataType: list
        path = child.joinpath(dataFile)
        if re.match(".+INT32", dataset):
            DataType = [TSDataType.INT32]
        if re.match(".+INT64", dataset):
            DataType = [TSDataType.INT64]
        if re.match(".+FLOAT", dataset):
            DataType = [TSDataType.FLOAT]
        if re.match(".+DOUBLE", dataset):
            DataType = [TSDataType.DOUBLE]

        data = pd.read_csv(str(path))
        device = "root.test.t1"
        time_list = [x for x in data["Senser"]]
        value_list = [[x] for x in data["s_0"]]
        measurements = ["s_0"]

        for data_type in DataType:
            data_types = [data_type]
            encodings = [TSEncoding.RAKE,TSEncoding.RLE,TSEncoding.TS_2DIFF,TSEncoding.GORILLA,
            TSEncoding.PLAIN,TSEncoding.SPRINTZ,TSEncoding.RLBE,]
            compressors = [Compressor.SNAPPY, Compressor.GZIP,
                           Compressor.UNCOMPRESSED, Compressor.LZ4]

            for compressor in compressors:
                for encoding in encodings:
                    tablet = Tablet(device, measurements, data_types,
                                    value_list[:TEST_SIZE], time_list[:TEST_SIZE])
                    insert_time = 0
                    select_time = 0
                    for _ in range(REPEAT_TIME):
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
                    logger.write("{},{},{},{},{},{}\n".format(dataset, data_type.name,
                                                              compressor.name, encoding.name, insert_time, select_time))

logger.close()
session.close()
