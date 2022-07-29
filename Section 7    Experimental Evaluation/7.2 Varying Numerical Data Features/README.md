# 7.2 Varying Data Features
## Fig 16-20: Varing Data Features
+ Data source: Data/Synthetic_data
+ Data configuration: use each synthetic data group separately
+ Code: Code/Ratio_expr.py
+ Code configuration: 
    + STORAGE_PATH on line 119:
        ```
        STORAGE_PATH = <IoTDB_PATH>/apache-iotdb-0.12.4-all-bin/apache-iotdb-0.12.4-all-bin/data/data/sequence/root.test/0/0
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
    + visualize the correlation between certain feature and compression ratio