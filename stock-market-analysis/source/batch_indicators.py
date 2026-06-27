# ==================== 模块导入 ====================
# 导入 SparkSession，用于创建 Spark 应用入口
from pyspark.sql import SparkSession
# 导入聚合函数 avg 和列操作函数 col
from pyspark.sql.functions import avg, col
# 导入窗口函数 Window，用于定义滑动窗口
from pyspark.sql.window import Window

# ==================== Spark 会话初始化 ====================
# 创建 SparkSession 实例，并配置 Delta Lake 扩展
# 应用名称
# 启用 Delta SQL 扩展
# 设置 Delta 目录
spark = SparkSession.builder \
    .appName("BatchIndicators") \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog") \
    .getOrCreate()
# 获取或创建会话

# ==================== 读取清洗后的行情数据 ====================
# 从 HDFS 的 Delta 表中读取已清洗的股票日线数据
df = spark.read.format("delta").load("/user/stock/delta/clean_prices")

# 将 DataFrame 注册为临时视图，便于使用 SQL 查询
df.createOrReplaceTempView("prices")

# ==================== 使用 SQL 窗口函数计算移动平均线 ====================
# 定义 SQL 语句：计算每只股票按时间排序的 SMA_5/10/20
# SMA_5: 当前行与前4行的收盘价平均值（共5行）
# SMA_10: 前9行 + 当前行（共10行）
# SMA_20: 前19行 + 当前行（共20行）
sma_sql = """
SELECT symbol, trade_time, close, volume,
       AVG(close) OVER (PARTITION BY symbol ORDER BY trade_time ROWS BETWEEN 4 PRECEDING AND CURRENT ROW) AS sma_5,
       AVG(close) OVER (PARTITION BY symbol ORDER BY trade_time ROWS BETWEEN 9 PRECEDING AND CURRENT ROW) AS sma_10,
       AVG(close) OVER (PARTITION BY symbol ORDER BY trade_time ROWS BETWEEN 19 PRECEDING AND CURRENT ROW) AS sma_20
FROM prices
"""

# 执行 SQL 查询，得到包含技术指标的新 DataFrame
result_df = spark.sql(sma_sql)

# ==================== 结果写入 HDFS ====================
# 将计算结果以 Delta 格式覆盖写入 HDFS 指定路径
result_df.write.format("delta").mode("overwrite").save("/user/stock/delta/indicators")

# 打印完成信息
print("SMA indicators computed and saved to HDFS.")