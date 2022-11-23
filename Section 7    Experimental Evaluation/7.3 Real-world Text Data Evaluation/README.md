# Section 7.3 Real-world Text Data Evaluation

## Fig 19: Compression ratio over all numerical datasets
+ Data sources: real-world text datasets placed in section 6 folder
+ Data configuration: none
+ Code: ratio_text_expr_real_world.py

+ Code configuration:
    + STORAGE_PATH on line 38:
        ```
        STORAGE_PATH = <IoTDB_PATH>/apache-iotdb-0.12.4-all-bin/apache-iotdb-0.12.4-all-bin/data/data/sequence/root.test/0/0
        ```
    + set dataset on line 63 to the directory where data are stored
    + set RESULT1_PATH on line 58 to the directory where the result is expected to save in
+ Experiment:
    + set data and code successfully
    + run python script ratio_text_expr_real_world.py
    + check result and visualize the relationship between combinations of encodings and compressors and compression ratio
