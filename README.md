# Guideline: Experimental Reproduction
In our paper Time Series Data Encoding for Efficient Storage A Comparative Analysis in Apache IoTDB, we introduced two data generators, several comparison experiments and TSEC, timeseries encoding classifier. 

To enable reproductivity, we open source all datasets, algorithms and codes introduced in the paper, and this document produces a guideline of reproduction. This readme elaborates the file structure of the whole repository, and folders are organised by the sequence of sections.

All visualization code and result files are in the folder Visualization.

Please read the readme file in each folder and follow the instructions to reproduce our experimetal results.

## Section 6: Encoding Benchmark
In this section, we introduced the datasets we use including synthetic numerical data, real-world numerical data, synthetic text data and real-world numerical data. We also introduced the evaluation metrix: several data features and how to calculate them. 

In this repository, we open source both synthetic datasets and real-world datasets, as well as the data generator of both numerical and text synthetic data. We also open source the code we used to calculate data features.

## Section 7: Experimental Evaluation
In this section, we carried out evaluations on real-world numerical data, synthetic numerical data, real-world text data and synthetic text data. 

We uploaded the codes used in the evaluations. Detailed information including how to reproduce the result is written in the readme file in each sub folder.

## Appendix
Based on the analyze of data features above, we propose Time Series EncodingClassi"er (TSEC) to automatically recommend a proper encodingmethod upon the profiled data features.

This part is not included in the paper published on pVLDB, you can download our appendix from this link: https://sxsong.github.io/doc/encoding.pdf

---

## File Structure
+ Section 6    Encoding Benchmark: datasets, data generators and feature calculators
  + Data Generator: numerical and text data generator
    + numerical_data_generator.py: numerical data generator
    + text_data_generator.py: text data generator
  + DataSets: all datasets of this paper excluding ingestion datasets
    + Real-world Datasets: real-world datasets of both numerical and text data
    + Synthetic Dataset: synthetic datasets of both numerical and text data
  + Feature Calculator: two feature calculator for numerical and text datasets
    + numerical_data_feature_calculator.py: feature calculator for numerical data
    + text_data_feature_calculator.py: feature calculator for text data
+ Section 7    Experimental Evaluation: codes, data, and intruction file of each subsection
  + 7.1 Real-world Numerical Data Evaluation
  + 7.2 Varying Numerical Data Features 
  + 7.3 Real-world Text Data Evaluation 
  + 7.4 Varying Text Data Features 
+ Visualization: result files and visualization files (download the directory and run the python script according to instruction file directly, as result files are organized by us). Each folder is for each figure.
  + Fig 10-11 Insert time and select time over all numerical datasets
  + Fig 12 Compression ratio and features on each datasets 
  + Fig 13 Trade-off between time and compression ratio 
  + Fig 14-18 Varying data features 
  + Fig 19 Performance of text encoding on real datasets 
  + Fig 20-23 Varying text features 
  + Fig 9 Compression ratio over all numerical datasets 
+ Appendix: test code and visualization code of appendix
  + Correlation: codes used to calculate the pearson correlation between each feature and predicted result
  + TSEC: machine learning models used in TSEC and python scripts used to train them
  + Compare: the comparison code of TSEC, CodecDB and C-store
  + Extra Cost: the code used to text the time cost of feature extracting

## Environment Requirement
+ IoTDB: download from branch https://github.com/apache/iotdb/tree/research/encoding-exp
+ python: 3.8+
+ modules needed: seaborn 0.11.1+ (used in visualization), scikit-learn 0.24.1+ (used in TSEC), joblib 1.0.1+ (used in TSEC), numpy, pandas

## Example Invocation

- Before experiment:
  - Clone this repository
  - Set IoTDB (two methods)
    - Download from (recommended): , then copy IoTDB to the root directory of this repository
    - Compile from: , then find the folder distribution/target/apache-iotdb-0.13.0-SNAPSHOT-all-bin, copy to the root directory of this repository and rename as "iotdb"
  - Set IoTDB's config
    - In iotdb/conf/iotdb-engine.properties, change line 201 to 
      ```py
      wal_buffer_size=200000000
      ```

- Take the code of drawing Fig 10-11 as an example

- run iotdb-server

  ```bash
  cd iotdb/sbin/
  ./start-server.sh
  ```

- produce the result of insert time and select time of real-world datasets

  ```bash
  <!-- return to root -->
  cd ../../

  cd script
  ./fig10-11.sh
  ```

- find fig 10-11
  ```bash
  <!-- return to root -->
  cd ../

  cd Section 7    Experimental Evaluation/7.1 Real-world Numerical Data Evaluation
  <!-- fig10.png and fig11.png are the 2 figures generated by experiment -->
  ```



