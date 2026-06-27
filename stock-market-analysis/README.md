# 基于 Spark 的股票市场实时行情分析与预测系统

本科课程大作业：大数据技术综合实验

## 项目简介

基于 Apache Spark Streaming 构建实时数据管道，A 股行情数据通过 Kafka 消息队列接入，综合运用 Spark 窗口函数计算技术指标、SnowNLP 进行情感分析、XGBoost 进行涨跌预测，Flask Web 界面提供可视化看板。

## 技术栈

- **数据采集**：baostock, akshare, Kafka Producer
- **流式处理**：Apache Spark Streaming, Delta Lake, HDFS
- **指标计算**：Spark SQL Window 函数 (MA5/MA10/MA20, MACD, RSI, BOLL)
- **情感分析**：SnowNLP（中文文本情感打分）
- **模型训练**：XGBoost（特征工程 + 时间序列标注）
- **数据存储**：MySQL, Delta Lake, HDFS
- **可视化**：Flask, ECharts

## 系统架构

```
数据采集 → Kafka → Spark Streaming（预处理）
    ↓
K线指标计算 (Window) + 情感分析 (SnowNLP) + XGBoost 预测
    ↓
MySQL 持久化 → Flask Web 可视化看板
```

## 核心功能

- 实时行情采集：baostock/akshare 获取 A 股实时数据
- 技术指标计算：MA5/MA10/MA20、MACD、RSI、BOLL
- 情感分析：SnowNLP 新闻情感打分
- 涨跌预测：XGBoost 次日涨跌预测
- 可视化看板：Flask + ECharts 实时展示

## 项目文件

```
source/
├── collector.py                 数据采集器
├── preprocess_stream.py         Spark Streaming 预处理
├── batch_indicators.py          K 线指标计算
├── sentiment_stream.py          情感分析
├── train_model.py               XGBoost 模型训练
├── web_server.py                Flask Web 服务
├── write_*_to_mysql.py          结果持久化
└── templates/index.html         Web 页面
```

## 截图

详见 [screenshots](./screenshots/) 目录。
