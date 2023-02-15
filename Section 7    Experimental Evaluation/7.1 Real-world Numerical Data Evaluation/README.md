# Section 7.1 Real-world Data Evaluation

## Fig 9: Compression ratio over all numerical datasets
+ Data sources: real-world numerical datasets placed in section 6 folder
+ Data configuration: merge 10 datasets' data into 4 folders according to datatype
+ Code: Ratio_expr.py

+ Code configuration:
    + STORAGE_PATH on line 119:
        ```
        STORAGE_PATH = <IoTDB_PATH>/apache-iotdb-1.0.0-all-bin/apache-iotdb-1.0.0-all-bin/data/data/sequence/root.test/0/0
        ```
    + set dataset on line 149 and dirs on line 143 to the directory where data are stored
    + set RESULT1_PATH to the directory where the result is expected to save in
    + set encodings on line 176 and compressors on line 178:
        ```
        encodings = [TSEncoding.RAKE,TSEncoding.RLE, TSEncoding.TS_2DIFF,TSEncoding.GORILLA, TSEncoding.PLAIN,TSEncoding.SPRINTZ,TSEncoding.RLBE]
        compressors = [Compressor.UNCOMPRESSED,Compressor.GZIP,Compressor.LZ4,Compressor.SNAPPY]
        ```
+ Experiment:
    + set data and code successfully
    + run python script Ratio_expr.py
    + check result and visualize the relationship between combinations of encodings and compressors and compression ratio

## Fig 10, 11 Insert Time, Select Time over all numerical Datasets
+ Data sources: Ingestion (in this folder)
+ Data configuration: non
+ Code: expr.py
+ Code configuation:
    + set RESULT_PATH on line 35 to the directory where result is expected to save in 
    + set file operation to fit the storage of datasets, make sure each datatype can match data with same datatype
+ Experiment: 
    + set data and code successfully
    + run python script expr.py
    + check result and visulise insert time and select time on each combinations of compressors and encodings.
## Fig 12: Compression ratio and features on each dataset
+ Data sources: real-world numerical datasets placed in section 6 folder
+ Data configuration: ONLY use float folders in each dataset
+ Code: Ratio_expr.py
+ Code configuration: 
    + STORAGE_PATH on line 119:
        ```
        STORAGE_PATH = <IoTDB_PATH>/apache-iotdb-1.0.0-all-bin/apache-iotdb-1.0.0-all-bin/data/data/sequence/root.test/0/0
        ```
    + set file operation to fit dataset storage
    + set RESULT1_PATH to the directory where the result is expected to save in
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

## Figure 13: Trade-off between time and compression ratio 

To test time more precisely, we created a java class to test encoding, decoding, compressing, uncompressing time while IoTDB is running. 

Java test class: https://github.com/apache/iotdb/blob/research/encoding-exp/tsfile/src/test/java/org/apache/iotdb/tsfile/encoding/decoder/EncodeTest.java

Run this test class and result will be recorded in csv file.