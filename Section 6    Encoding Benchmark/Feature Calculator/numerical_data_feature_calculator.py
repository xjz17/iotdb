from distutils import filelist
import time
from pathlib import Path
import os
import re

import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from sklearn import datasets
import scipy.stats as ss


def entropy(data):
    prob = []
    length = len(data)
    for i in np.unique(data):
        prob.append(np.sum(data == i) / float(length))
    return ss.entropy(prob)


def repeat_words(data, limit=8):
    lenth = len(data)
    index = 0
    key = data[0]
    count = 0
    for val in data:
        if val == key:
            count += 1
        else:
            if count >= limit:
                index += count
            key = val
            count = 0
    return float(index) / lenth


def sortedness(data):
    length = len(data)
    count = 0
    for i in range(length - 1):
        if data[i] <= data[i + 1]:
            count += 1
    return count / (length - 1)

# this function calculates the features of numerical data
# @args:
#     data: list of values
# @return:
#     a tuple of features
def statistic(data):
    ave = np.nanmean(data, axis=0)

    std = np.nanstd(data, axis=0)
    Min = np.nanmin(data, axis=0)
    Max = np.nanmax(data, axis=0)
    std_spread = Max - Min
    diff_data = np.diff(data, axis=0)
    diff_ave = np.nanmean(diff_data, axis=0)
    diff_min = np.nanmin(diff_data, axis=0)
    diff_max = np.nanmax(diff_data, axis=0)
    diff_spread = diff_max - diff_min
    diff_std = np.nanstd(diff_data, axis=0)
    repeat = repeat_words(data)
    sort = sortedness(data.tolist())
    return "{},{},{},{},{},{},{},{}".format(
        ave, std, std_spread,
        diff_ave, diff_std, diff_spread,
        repeat, sort
    )