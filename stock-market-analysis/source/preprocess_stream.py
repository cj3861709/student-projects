# ==================== 导入必要模块 ====================
from pyspark.sql import SparkSession
from pyspark.sql.functions import from_json, col, to_timestamp, when
from pyspark.sql.types import StructType, StringType, DoubleType, LongType, TimestampType

# ==================== Spark 会话初始化（启用 Delta Lake） ====================
spark = SparkSession.builder \
    .appName("StockPreprocessing") \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog") \
    .getOrCreate()

# ==================== 定义消息 Schema ====================
# 与 Kafka 中 JSON 消息的结构一致
# 股票代码
# 交易时间字符串
schema = StructType() \
    .add("symbol", StringType()) \
    .add("trade_time", StringType()) \
    .add("open", DoubleType()) \
    .add("high", DoubleType()) \
    .add("low", DoubleType()) \
    .add("close", DoubleType()) \
    .add("volume", LongType()) \
    .add("data_type", StringType())          # 数据类型：daily/realtime

# ==================== 从 Kafka 读取流数据 ====================
# 订阅主题
# 从最早开始消费
# 允许数据丢失继续运行
kafka_df = spark.readStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "localhost:9092") \
    .option("subscribe", "stock-prices") \
    .option("startingOffsets", "earliest") \
    .option("failOnDataLoss", "false") \
    .load()

# ==================== 解析 JSON ====================
# 将 Kafka 的 value 字段（二进制）转为 JSON，然后展开为列
parsed = kafka_df.select(from_json(col("value").cast("string"), schema).alias("data")).select("data.*")
# 将 trade_time 字符串转为 Timestamp 类型
parsed = parsed.withColumn("trade_time", to_timestamp("trade_time", "yyyy-MM-dd HH:mm:ss"))

# ==================== 数据清洗 ====================
# 1. 去重：基于 symbol 和 trade_time 组合去重
# 2. 过滤：close > 0（无效价格剔除）
# 3. 过滤：volume >= 0
# 4. 填充缺失值：volume, open, high, low, close 缺失时填充 0
cleaned = parsed.dropDuplicates(["symbol", "trade_time"]) \
    .filter(col("close") > 0) \
    .filter(col("volume") >= 0) \
    .fillna({"volume": 0, "open": 0, "high": 0, "low": 0, "close": 0})

# ==================== 定义写入 HDFS 的函数 ====================
def write_to_hdfs(df, epoch_id):
    """
    每个微批次调用此函数，将清洗后的数据以 Delta 格式追加到 HDFS
    """
    if df.count() > 0:
        df.write.format("delta").mode("append").save("/user/stock/delta/clean_prices")

# ==================== 启动流查询 ====================
# 每 10 秒处理一次微批次，调用 foreachBatch 写入 HDFS
query = cleaned.writeStream \
    .foreachBatch(write_to_hdfs) \
    .trigger(processingTime="10 seconds") \
    .start()

# 等待流作业终止（实际会一直运行）
query.awaitTermination()