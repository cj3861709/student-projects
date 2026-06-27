from pyspark.sql import SparkSession
spark = SparkSession.builder \
    .appName("ExportNews") \
    .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension") \
    .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog") \
    .getOrCreate()
df = spark.read.format("delta").load("/user/stock/delta/news_sentiment")
df.coalesce(1).write.mode("overwrite").option("header", "true").csv("/home/chenjin/Desktop/news_output")
print("导出完成")
