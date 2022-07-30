# Appendix: TSEC Performance
+ Data source: Both synthetic and real-world numerical data
+ Code: TrainData.py & train.ipynb
+ Experiment:
    + Step I: generate training data
        + mix all synthetic data and real world data together, then divided into 4 folders according to datatype
        + set TrainData.py ready:
            ```
            RESULT = "./train.csv" # to direction where result is expected to save in 
            STORAGE_PATH = "<IoTDB_PATH>/apache-iotdb-0.12.4-all-bin/apache-iotdb-0.12.4-all-bin/data/data/sequence/root.test/0/0"
            encodings = [TSEncoding.RAKE, TSEncoding.RLE, TSEncoding.TS_2DIFF, TSEncoding.GORILLA, TSEncoding.PLAIN, TSEncoding.SPRINTZ, TSEncoding.RLBE]
            compressors = [Compressor.UNCOMPRESSED]
            ```
            + set file operations to fit the data storage
            + set datatypes to fit the storage of data with different datatype
        + run python script TrainData.py
        + replace INT32 with 1, INT64 with 2, FLOAT with 3, DOUBLE with 4 in result file
    + Step II: generate test data
        + mix all synthetic data together then divided into 4 folders according to datatype
        + set script ready and run as same as Step I
        + replace INT32 with 1, INT64 with 2, FLOAT with 3, DOUBLE with 4 in result file
        + mix all real-world data together then divided into 4 folders according to datatype
        + set script ready and run as same as Step I
        + replace INT32 with 1, INT64 with 2, FLOAT with 3, DOUBLE with 4 in result file
    + Step III: train and test
        + set train.ipynb ready: set train dataset to the result generated in step I, test dataset to the result generated in step II
        + run train.ipynb, check the result of different classifiers
+ Note: You can use the dataset separatly to get the result of each dataset. This is how Figure 32 is done.