# ==================== 导入模块 ====================
from pyspark.sql import SparkSession
from pyspark.sql.functions import udf, from_json, col, to_timestamp
from pyspark.sql.types import StringType, DoubleType, StructType, StructField
from snownlp import SnowNLP   # 中文情感分析库

# ==================== Spark 初始化（Delta 支持） ====================
spark = SparkSession.builder \
    .appName("SentimentAnalysis") \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog") \
    .getOrCreate()

# ==================== 情感分析函数 ====================
def get_sentiment(text):
    """
    使用 SnowNLP 计算文本情感得分，返回 0~1 之间的浮点数。
    情感值越接近 1 表示越积极，接近 0 表示消极。
    若文本为空或过短，返回 0.5（中性）。
    """
    if not text or len(text.strip()) < 5:
        return 0.5
    try:
        s = SnowNLP(text[:300])   # 限制长度提高性能
        return float(s.sentiments)
    except:
        return 0.5

# 注册为 Spark UDF（用户自定义函数）
sentiment_udf = udf(get_sentiment, DoubleType())

# ==================== 定义新闻消息 Schema ====================
news_schema = StructType([
    StructField("symbol", StringType()),
    StructField("title", StringType()),
    StructField("content", StringType()),
    StructField("publish_time", StringType()),
    StructField("source", StringType()),
    StructField("data_type", StringType())
])

# ==================== 从 Kafka 读取新闻流 ====================
kafka_news = spark.readStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "localhost:9092") \
    .option("subscribe", "stock-news") \
    .option("startingOffsets", "earliest") \
    .load()

# 解析 JSON 并转换时间列
parsed_news = kafka_news.select(from_json(col("value").cast("string"), news_schema).alias("data")).select("data.*")
parsed_news = parsed_news.withColumn("publish_time", to_timestamp("publish_time", "yyyy-MM-dd HH:mm:ss"))

# 添加情感得分列
news_with_sentiment = parsed_news.withColumn("sentiment_score", sentiment_udf(col("title")))

# ==================== 定义写入 HDFS 的函数 ====================
def write_news_to_hdfs(df, epoch_id):
    if df.count() > 0:
        df.write.format("delta").mode("append").save("/user/stock/delta/news_sentiment")

# ==================== 启动流查询 ====================
# 每 30 秒处理一批新闻
query = news_with_sentiment.writeStream \
    .foreachBatch(write_news_to_hdfs) \
    .trigger(processingTime="30 seconds") \
    .start()

query.awaitTermination()