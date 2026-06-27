# ==================== 导入模块 ====================
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, lead, when
from pyspark.sql.window import Window
import xgboost as xgb               # XGBoost 分类器
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score
import pickle                       # 保存模型

# ==================== Spark 初始化 ====================
spark = SparkSession.builder \
    .appName("TrainModel") \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog") \
    .getOrCreate()

# ==================== 读取清洗后的行情和技术指标数据 ====================
price_df = spark.read.format("delta").load("/user/stock/delta/clean_prices")
indicator_df = spark.read.format("delta").load("/user/stock/delta/indicators")

# 只选择需要的列，避免合并时列名冲突
price_clean = price_df.select("symbol", "trade_time", "close", "volume")
# indicator_df 中可能也包含 close 列，但这里只取 sma 列
indicator_clean = indicator_df.select("symbol", "trade_time", "sma_5", "sma_10", "sma_20")

# 内连接，保留同时存在于两个表中的记录
df = price_clean.join(indicator_clean, on=["symbol", "trade_time"], how="inner")

# ==================== 构造标签 ====================
# 定义窗口：按股票分区，按时间升序排序
window_spec = Window.partitionBy("symbol").orderBy("trade_time")
# 取未来第5行的收盘价（如果不足5行则为 null）
df = df.withColumn("future_close", lead("close", 5).over(window_spec))
# 如果 future_close > 当前 close，则标签为 1（上涨），否则 0（下跌或不变）
df = df.withColumn("label", when(col("future_close") > col("close"), 1).otherwise(0))

# ==================== 特征选择与数据准备 ====================
feature_cols = ['close', 'volume', 'sma_5', 'sma_10', 'sma_20']
# 选择特征列和标签列，丢弃含有 null 的行
df = df.select(feature_cols + ['label']).na.drop()

# 转换为 Pandas DataFrame（因数据量不大，可直接转换）
pdf = df.toPandas()
X = pdf[feature_cols]
y = pdf['label']

print(f"Dataset size: {len(X)}")
if len(X) == 0:
    print("No data available for training. Please check if clean_prices and indicators have data.")
    exit(1)

# ==================== 划分训练集和测试集 ====================
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

# ==================== 训练 XGBoost 模型 ====================
model = xgb.XGBClassifier(
    n_estimators=100,      # 树的数量
    max_depth=5,           # 每棵树最大深度
    learning_rate=0.1,     # 学习率
    objective='binary:logistic'   # 二分类目标函数
)
model.fit(X_train, y_train)

# ==================== 评估模型 ====================
y_pred = model.predict(X_test)
acc = accuracy_score(y_test, y_pred)
print(f"Model Accuracy: {acc:.4f}")
from sklearn.metrics import confusion_matrix, classification_report

# 混淆矩阵
cm = confusion_matrix(y_test, y_pred)
print("Confusion Matrix (rows:真实, cols:预测):")
print(cm)

# 分类报告（精确率、召回率、F1）
print("\nClassification Report:")
print(classification_report(y_test, y_pred, target_names=['跌', '涨']))

# ==================== 保存模型 ====================
with open('/tmp/stock_xgb_model.pkl', 'wb') as f:
    pickle.dump(model, f)
print("Model saved to /tmp/stock_xgb_model.pkl")