from __future__ import division, print_function
from sklearn import datasets

from statistical_gap import gap_statistic as gs
import xlrd
import view
import data_generator as dg


def verified_data_set():
    wb = xlrd.open_workbook("./datasets_example/s-originals/s1-groundtruth-plot.xls")
    sheet = wb.sheet_by_index(0)
    x = sheet.col_slice(colx=1, start_rowx=15)
    y = sheet.col_slice(colx=2, start_rowx=15)
    obs_x = (val.value for val in x)
    obs_y = (val.value for val in y)
    obs_x = map(int, obs_x)
    obs_y = map(int, obs_y)

    data = zip(obs_x, obs_y)
    k, plot_info, centers = gs(data)
    print("the returned k is : {}".format(k))
    view.view_result(data, centers, plot_info)


def test_iris():
    iris_dat = datasets.load_iris()
    data = iris_dat.data
    k, plot_info, centers = gs(data)
    print("the returned k is : {}".format(k))
    view.view_result(data, centers, plot_info)
    target = iris_dat.target
    dataset_accuracy(data, target, centers)


def test_digits():
    digits_dat = datasets.load_digits()
    data = digits_dat.data
    k, plot_info = gs(data)
    print("the returned k is : {}".format(k))
    view.plot_info(plot_info)
    # target = digits_dat.target
    # kk = Kmeans(10, data)
    # clusters = kk.clusterize()
    # dataset_accuracy(data, target, clusters)


def test_wine():
    wine_dat = datasets.load_wine()
    data = wine_dat.data
    k, plot_info, clusters = gs(data)
    print("the returned k is : {}".format(k))
    view.plot_info(plot_info)
    # target = wine_dat.target
    # kk = Kmeans(3, data)
    # clusters = kk.clusterize()
    # dataset_accuracy(data, target, clusters)


def five_clusters():
    exp_k = 5
    data = dg.generate_5_clusters()
    k, plot_info, centers = gs(data)

    print("the expected number of k is: {}".format(exp_k))
    print("the returned k is : {}".format(k))
    view.view_result(data, centers, plot_info)


def k_clusters(k=2):
    exp_k = k
    n = k * 150
    data = dg.generate_groups(n, k, dg.random_3d_point)
    k, plot_info, centers = gs(data)

    print("the expected number of k is: {}".format(exp_k))
    print("the returned k is : {}".format(k))
    view.view_result(data, centers, plot_info)


def dataset_accuracy(data, target, clusters):
    dl = map(list, data)
    dl = list(dl)
    hits = 0
    for center in clusters:
        center_ind = dl.index(list(center.features))
        center_target = target[center_ind]
        print(center_target)

        for obs in center.cluster:
            obs_ind = dl.index(list(obs.features))
            obs_target = target[obs_ind]
            if obs_target == center_target:
                hits += 1

    print("{:.2f}% success rate according to real groups.".format((hits / len(data)) * 100))

