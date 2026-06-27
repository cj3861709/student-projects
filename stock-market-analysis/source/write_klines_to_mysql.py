# ==================== 导入模块 ====================
from pyspark.sql import SparkSession
from pyspark.sql.functions import first, last, sum as spark_sum, to_date, col, max, min, to_timestamp, expr

# ==================== Spark 初始化 ====================
spark = SparkSession.builder \
    .appName("WriteKlinesToMySQL") \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog") \
    .getOrCreate()

# ==================== 读取 HDFS 清洗后的数据 ====================
df = spark.read.format("delta").load("/user/stock/delta/clean_prices")

# ==================== 按日聚合生成 K 线 ====================
# 将 trade_time 转换为日期（去掉时分秒）
df_daily = df.withColumn("trade_date", to_date("trade_time")) \
    .groupBy("symbol", "trade_date") \
    .agg(
        first("open").alias("open_price"),      # 当日开盘价 = 第一笔
        max("high").alias("high_price"),        # 当日最高价
        min("low").alias("low_price"),          # 当日最低价
        last("close").alias("close_price"),     # 当日收盘价 = 最后一笔
        spark_sum("volume").alias("volume")     # 当日总成交量
    )

# ==================== 添加与 MySQL 表匹配的 start_time 和 end_time 列 ====================
# start_time 设为日期当天 00:00:00
# end_time 设为当天 23:59:59（通过加1天减1秒实现）
df_daily = df_daily.withColumn("start_time", to_timestamp("trade_date")) \
                   .withColumn("end_time", expr("start_time + interval 1 day - interval 1 second"))

# ==================== 重命名列并选择所需列 ====================
df_daily = df_daily.withColumnRenamed("symbol", "stock_code") \
                   .select("stock_code", "start_time", "end_time", "open_price", "high_price", "low_price", "close_price", "volume")

# ==================== 写入 MySQL ====================
# 使用 JDBC 连接，表名 realtime_klines，模式 append（追加）
df_daily.write \
    .format("jdbc") \
    .option("url", "jdbc:mysql://localhost:3306/stock_db?useSSL=false&serverTimezone=UTC") \
    .option("driver", "com.mysql.cj.jdbc.Driver") \
    .option("dbtable", "realtime_klines") \
    .option("user", "root") \
    .option("password", "123456") \
    .mode("append") \
    .save()

print("K线数据写入MySQL完成")