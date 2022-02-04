# Guideline: Experimental reproduction

In our paper *Time Series Data Encoding for Efficient Storage A Comparative Analysis in Apache IoTDB*, we introduce an algorithm of generating data to certain features in section 6.2, and carry out 3 experience: real-world data evaluation, varying data features and recommendation performance in section 7.1, 7.2 and 7.3.

To enable reproductivity, we open source all datasets, algorithms and codes introduced in the paper, and this document produces a guideline of reproduction. 

## Section 6.2: Synthetic Data

The algorithm 1: Data generator is implemented by python in direction Code, named data_generator.py. In this python script, function *generator(mean, delta_mean, delta_variance, repeat, increase, length)* has the same function descripted in our paper. By inputing parameters of value means, delta mean, delta variance, repeat and increase, data with certain features can be generated. During our experiment, we generated 5 groups of data by controling 4 parameters and ranging 1 specific parameter. The code of generating 5 groups synthetic data are at the end of the script file. By annotating others and de-annotating a chosen one, synthetic data with certain feature can be generated, and they are as same as the synthetic data in direction Data/Synthetic_data, also same as the data we use in experiment.

## Section 7.1 Real-world Data Evaluation

### Fig 12: Compression ratio over all datasets
+ Data sources: Data/Real-world_data
+ Data configuration: merge 10 datasets' data into 4 folders according to datatype
+ Code: Code/Ratio_expr.py

+ Code configuration:
    + STORAGE_PATH on line 119:
        ```
        STORAGE_PATH = <IoTDB_PATH>/apache-iotdb-0.12.4-all-bin/apache-iotdb-0.12.4-all-bin/data/data/sequence/root.test/0/0
        ```
    + set dataset on line 149 and dirs on line 143 to the direction where data are stored
    + set RESULT1_PATH to the direction where the result is expected to save in
    + set encodings on line 176 and compressors on line 178:
        ```
        encodings = [TSEncoding.RAKE,TSEncoding.RLE, TSEncoding.TS_2DIFF,TSEncoding.GORILLA, TSEncoding.PLAIN,TSEncoding.SPRINTZ,TSEncoding.RLBE]
        compressors = [Compressor.UNCOMPRESSED,Compressor.GZIP,Compressor.LZ4,Compressor.SNAPPY]
        ```
+ Experiment:
    + set data and code successfully
    + run python script Ratio_expr.py
    + check result and visualize the relationship between combinations of encodings and compressors and compression ratio

### Fig 13, 14 Insert Time, Select Time over all Datasets
+ Data sources: Data/Ingestion
+ Data configuration: non
+ Code: Code/expr.py
+ Code configuation:
    + set RESULT_PATH on line 35 to the direction where result is expected to save in 
    + set file operation to fit the storage of datasets, make sure each datatype can match data with same datatype
+ Experiment: 
    + set data and code successfully
    + run python script expr.py
    + check result and visulise insert time and select time on each combinations of compressors and encodings.
### Fig 15: Compression ratio and features on each dataset
+ Data sources: Data/Real-world_data
+ Data configuration: ONLY use float folders in each dataset
+ Code: Code/Ratio_expr.py
+ Code configuration: 
    + STORAGE_PATH on line 119:
        ```
        STORAGE_PATH = <IoTDB_PATH>/apache-iotdb-0.12.4-all-bin/apache-iotdb-0.12.4-all-bin/data/data/sequence/root.test/0/0
        ```
    + set file operation to fit dataset storage
    + set RESULT1_PATH to the direction where the result is expected to save in
    + set encodings on line 176 and compressors on line 178:
        ```
        encodings = [TSEncoding.RAKE,TSEncoding.RLE, TSEncoding.TS_2DIFF,TSEncoding.GORILLA, TSEncoding.PLAIN,TSEncoding.SPRINTZ,TSEncoding.RLBE]
        compressors = [Compressor.UNCOMPRESSED]
        ```
+ Experiment:
    + set data and code successfully
    + run python script Ratio_expr.py
    + check the result
    + Compute the average of each data feature and compression ratio, then visualize them

## 7.2 Varying Data Features
### Fig 16-20: Varing Data Features
+ Data source: Data/Synthetic_data
+ Data configuration: use each synthetic data group separately
+ Code: Code/Ratio_expr.py
+ Code configuration: 
    + STORAGE_PATH on line 119:
        ```
        STORAGE_PATH = <IoTDB_PATH>/apache-iotdb-0.12.4-all-bin/apache-iotdb-0.12.4-all-bin/data/data/sequence/root.test/0/0
        ```
    + set file operation to fit dataset storage
    + set RESULT1_PATH to the direction where the result is expected to save in
    + set encodings on line 176 and compressors on line 178:
        ```
        encodings = [TSEncoding.RAKE,TSEncoding.RLE, TSEncoding.TS_2DIFF,TSEncoding.GORILLA, TSEncoding.PLAIN,TSEncoding.SPRINTZ,TSEncoding.RLBE]
        compressors = [Compressor.UNCOMPRESSED]
        ```
+ Experiment:
    + set data and code successfully
    + run python script Ratio_expr.py
    + visualize the correlation between certain feature and compression ratio

## 7.3 Recommendation Performance
+ Data source: Data/Synthetic_data & Data/Real-world_data
+ Code: Code/TrainData.py & Code/train.ipynb
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

## Visualization
Codes of result visualization are in Visualization