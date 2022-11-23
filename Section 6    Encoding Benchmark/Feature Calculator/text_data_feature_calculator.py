

# this function calculates the 4 features of text data
# @args:
#       data: list of string
# @return:
#        a tuple: (theta, N, repeat, average_length)
def cal_feature(data):
    N = 0
    repeat = 0
    length = 0
    tot = 0
    dic = []
    dic_time = []
    dic_time_fst = 0
    dic_time_sec = 0
    for ln in data:
        ln = "".join(ln)
        flag = 0
        for j in range(len(dic)):
            if dic[j] == ln:
                flag = 1
                dic_time[j] += 1
                if dic_time[j] > dic_time_fst:
                    dic_time_sec = dic_time_fst
                    dic_time_fst = dic_time[j]
                elif dic_time[j] <= dic_time_fst and dic_time[j] > dic_time_sec:
                    dic_time_sec = dic_time[j]
        if flag == 0:
            N += 1
            dic.append(ln)
            dic_time.append(1)
            j = len(dic) - 1
            if dic_time[j] > dic_time_fst:
                dic_time_sec = dic_time_fst
                dic_time_fst = dic_time[j]
            elif dic_time[j] <= dic_time_fst and dic_time[j] > dic_time_sec:
                dic_time_sec = dic_time[j]
        tot += len(ln)
        for i in range(len(ln)):
            if i!=0 and ln[i] == ln[i-1]:
                repeat += 1
                #print(repeat)
    theta = log(dic_time_fst/dic_time_sec) / log(2).real
    return "{},{},{},{}\n".format(theta, N, repeat/tot, tot/len(data))