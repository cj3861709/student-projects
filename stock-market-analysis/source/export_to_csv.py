from pyspark.sql import SparkSession

spark = SparkSession.builder \
    .appName("ExportDeltaToCSV") \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog") \
    .getOrCreate()

# 读取 Delta 表
df = spark.read.format("delta").load("/user/stock/delta/clean_prices")

# 直接写入本地文件系统（不是 HDFS）
df.coalesce(1).write.mode("overwrite") \
    .option("header", "true") \
    .csv("/home/chenjin/Desktop/clean_prices_output")

print("导出完成，文件位于 /home/chenjin/Desktop/clean_prices_output/")