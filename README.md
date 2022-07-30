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

This part is not included in the paper published on pVLDB, you can download our appendix from the home page of Shaoxu Song: https://sxsong.github.io/doc/encoding.pdf