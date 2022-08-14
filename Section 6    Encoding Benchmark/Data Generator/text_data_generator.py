import random
import csv, os, re

def random_index(rate):
    start = 0
    index = 0
    randnum = random.randint(1,sum(rate))
    for index, scope in enumerate(rate):
        start += scope
        if randnum <= start:
            break
    return index

# theta: value of exponent
# N: domain size
# l: average length of string
# gamma: repeat rate
def text_generator(theta, N, l, gamma, pointnum):
    dictionary = []
    for i in range(N):
        str = ''
        flag = 0
        for j in range(l):
            if flag == 0:
                flag = 1
                str += chr(random.randint(48, 122))
                continue
            isrepeat = random_index([100 * gamma, 100 * (1 - gamma)])
            if isrepeat == 0:
                str += str[j-1]
            else:
                s = chr(random.randint(41, 126))
                while s == str[j-1]:
                    s = chr(random.randint(41, 126))
                str += s
        dictionary.append(str)

    num = [0 for x in range(N)]
    base = 0
    for i in range(N):
        base += pow(1/(1+i), theta)
    for i in range(N):
        num[i] = round((pow(1/(i+1), theta)/base) * pointnum)

    data = []
    for i in range(N):
        for j in range(num[i]):
            data.append(dictionary[i])
    random.shuffle(data)
    return data

seed_num = 10
p_path = "." # to be changed
for i in range(11):
    for j in range(seed_num):
        if(os.path.exists(p_path+"/Exponent/{}".format(i)) == False):
            os.mkdir(p_path+"/Exponent/{}".format(i))
        random.seed(j)
        exp = i+2
        data = text_generator(exp, 750, 100, 0.5, 50000)
        writer = csv.writer(open(p_path+"/Exponent/{}/{}.csv".format(i, j), 'w', encoding='utf-8', newline=''),escapechar='\\')
        writer.writerow(['Sensor','s_0'])
        for k in range(len(data)):
            writer.writerow([k, data[k]])
    print("Exponent: {}".format(i))

for i in range(11):
    for j in range(seed_num):
        if(os.path.exists(p_path+"/Class/{}".format(i)) == False):
            os.mkdir(p_path+"/Class/{}".format(i))
        random.seed(j)
        Class = i*150
        if Class == 0:
            Class = 1
        data = text_generator(0, Class, 1000, 0.5, 50000)
        writer = csv.writer(open(p_path+"/Class/{}/{}.csv".format(i, j), 'w', encoding='utf-8', newline=''),escapechar='\\')
        writer.writerow(['Sensor','s_0'])
        for k in range(len(data)):
            writer.writerow([k, data[k]])
    print("Class: {}".format(i))

for i in range(11):
    for j in range(seed_num):
        if(os.path.exists(p_path+"/Length/{}".format(i)) == False):
            os.mkdir(p_path+"/Length/{}".format(i))
        random.seed(j)
        Len = (i+1)*100
        data = text_generator(0, 2, Len, 0.5, 50000)
        writer = csv.writer(open(p_path+"/Length/{}/{}.csv".format(i, j), 'w', encoding='utf-8', newline=''),escapechar='\\')
        writer.writerow(['Sensor','s_0'])
        for k in range(len(data)):
            writer.writerow([k, data[k]])
    print("Len: {}".format(i))

for i in range(11):
    for j in range(seed_num):
        if(os.path.exists(p_path+"/Repeat/{}".format(i)) == False):
            os.mkdir(p_path+"/Repeat/{}".format(i))
        random.seed(j)
        data = text_generator(0, 2, 1000, (i/10)*(0.1)+0.9, 50000)
        writer = csv.writer(open(p_path+"/Repeat/{}/{}.csv".format(i, j), 'w', encoding='utf-8', newline=''),escapechar='\\')
        writer.writerow(['Sensor','s_0'])
        for k in range(len(data)):
            writer.writerow([k, data[k]])
    print("Repeat: {}".format(i))
