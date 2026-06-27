# ==================== 导入模块 ====================
from pyspark.sql import SparkSession

# ==================== Spark 初始化 ====================
spark = SparkSession.builder \
    .appName("WriteSentiment") \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog") \
    .getOrCreate()

# ==================== 读取 HDFS 中的情感数据 ====================
df = spark.read.format("delta").load("/user/stock/delta/news_sentiment")

# ==================== 重命名和选择列以匹配 MySQL 表结构 ====================
# 假设 HDFS 中的列名为：symbol, title, content, publish_time, source, sentiment_score, ...
# MySQL 表 news_sentiment_mysql 的列名为：stock_code, title, content, publish_time, source, sentiment_score
df_mysql = df.selectExpr(
    "symbol as stock_code",   # 重命名 symbol -> stock_code
    "title",
    "content",
    "publish_time",
    "source",
    "sentiment_score"
)

# ==================== 写入 MySQL ====================
df_mysql.write \
    .format("jdbc") \
    .option("url", "jdbc:mysql://localhost:3306/stock_db?useSSL=false&serverTimezone=UTC") \
    .option("driver", "com.mysql.cj.jdbc.Driver") \
    .option("dbtable", "news_sentiment_mysql") \
    .option("user", "root") \
    .option("password", "123456") \
    .mode("append") \
    .save()

print("新闻情感数据已写入 MySQL")