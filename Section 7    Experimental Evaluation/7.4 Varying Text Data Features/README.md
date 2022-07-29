# Section 7.4 Real-world Text Data Evaluation

## Fig 20-23: Varying text feature (compression ratio part)
+ Data sources: synthetic text datasets placed in section 6 folder
+ Data configuration: none
+ Code: feature_ratio_expr_text.py

+ Code configuration:
    + STORAGE_PATH on line 70:
        ```
        STORAGE_PATH = <IoTDB_PATH>/apache-iotdb-0.12.4-all-bin/apache-iotdb-0.12.4-all-bin/data/data/sequence/root.test/0/0
        ```
    + set RESULT1_PATH on line 86 to the directory where the result is expected to save in
    + set dataset on line 91 to the directory where data is stored
+ Experiment:
    + set data and code successfully
    + run python script feature_ratio_expr_text.py
    + check result and visualize the relationship between combinations of encodings and compressors and compression ratio

## Fig 20-23: Varying text feature (insert and select time part)
+ Data sources: synthetic text datasets placed in section 6 folder
+ Data configuration: none
+ Code: time_expr_text.py

+ Code configuration:
    + STORAGE_PATH on line 70:
        ```
        STORAGE_PATH = <IoTDB_PATH>/apache-iotdb-0.12.4-all-bin/apache-iotdb-0.12.4-all-bin/data/data/sequence/root.test/0/0
        ```
    + set RESULT1_PATH on line 87 to the directory where the result is expected to save in
    + set dataset on line 91 to the directory where data is stored
+ Experiment:
    + set data and code successfully
    + run python script time_expr_text.py
    + check result and visualize the relationship between combinations of encodings and compressors and insert, select time. 
