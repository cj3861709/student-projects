# ==================== 导入模块 ====================
import pickle
import mysql.connector
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, row_number
from pyspark.sql.window import Window

# ==================== Spark 初始化 ====================
spark = SparkSession.builder \
    .appName("WritePredictions") \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog") \
    .getOrCreate()

# ==================== 加载训练好的模型 ====================
with open('/tmp/stock_xgb_model.pkl', 'rb') as f:
    model = pickle.load(f)

# ==================== 读取指标数据（包含 sma 列） ====================
df = spark.read.format("delta").load("/user/stock/delta/indicators")

# ==================== 取每只股票的最新一条记录 ====================
# 窗口：按股票分区，按时间降序排序，取行号=1
window = Window.partitionBy("symbol").orderBy(col("trade_time").desc())
latest_df = df.withColumn("rn", row_number().over(window)).filter(col("rn") == 1).drop("rn")

# ==================== 构建特征向量并进行预测 ====================
feature_cols = ['close', 'volume', 'sma_5', 'sma_10', 'sma_20']
# 转为 Pandas DataFrame 以调用 model.predict
features_pd = latest_df.select(feature_cols).toPandas()
X = features_pd[feature_cols].values
pred_probs = model.predict_proba(X)[:, 1]   # 上涨概率
pred_labels = model.predict(X)              # 0/1 标签

# ==================== 连接到 MySQL ====================
conn = mysql.connector.connect(
    host='localhost',
    user='root',
    password='123456',
    database='stock_db'
)
cursor = conn.cursor()

# ==================== 遍历每只股票，插入预测结果 ====================
rows = latest_df.collect()
for i, row in enumerate(rows):
    direction = 'UP' if pred_labels[i] == 1 else 'DOWN'
    sql = "INSERT INTO predictions (stock_code, predict_time, predicted_direction, confidence) VALUES (%s, NOW(), %s, %s)"
    cursor.execute(sql, (row['symbol'], direction, float(pred_probs[i])))

conn.commit()
cursor.close()
conn.close()
print("预测结果已写入 MySQL predictions 表")