# Sparkstreaming_News
大数据项目实战之基于Spark2.X的新闻话题的实时统计分析
## 一、业务需求分析
1.捕获用户浏览日志信息   

2.实时分析前20名流量最高的新闻话题  

3.实时统计当前线上已曝光的新闻话题  

4.统计哪个时段用户浏览量最高  

## 二、系统架构图设计
![image](https://github.com/huashishaojie/Sparkstreaming_News/blob/master/images/architecture.png)
## 三、项目介绍
本项目分为SparkS和SparkWeb两部分。
SparkS是本项目使用SparkStreaming近实时消费kafka中数据，使用mysqlPool向mysql中写入分析后数据的部分。
SparkWeb是本项目使用WebSocket和WebService搭建的前台展示页面，效果如下：
![image](https://github.com/huashishaojie/Sparkstreaming_News/blob/master/images/SparkWeb.jpg)
## 四、参考步骤
### 1.创建hbase表  
	create 'weblogs','info'

### 2.配置flume文件  
node2中:  
a2.sources = r1
a2.sinks = k1
a2.channels = c1

a2.sources.r1.type = exec
a2.sources.r1.command = tail -F /opt/data/weblog-flume.log
a2.sources.r1.channels = c1

a2.channels.c1.type = memory
a2.channels.c1.capacity = 1000
a2.channels.c1.transactionCapacity = 1000
a2.channels.c1.keep-alive = 5

a2.sinks.k1.type = avro
a2.sinks.k1.channel = c1
a2.sinks.k1.hostname = node1
a2.sinks.k1.port = 5555
#######################################################################

node3中:  
a3.sources = r1
a3.sinks = k1
a3.channels = c1

a3.sources.r1.type = exec
a3.sources.r1.command = tail -F /opt/data/weblog-flume.log
a3.sources.r1.channels = c1

a3.channels.c1.type = memory
a3.channels.c1.capacity = 1000
a3.channels.c1.transactionCapacity = 1000
a3.channels.c1.keep-alive = 5

a3.sinks.k1.type = avro
a3.sinks.k1.channel = c1
a3.sinks.k1.hostname = node1
a3.sinks.k1.port = 5555
########################################################################

node1中:  
a1.sources = r1
a1.channels = kafkaC hbaseC
a1.sinks = kafkaSink hbaseSink

a1.sources.r1.type = avro       
a1.sources.r1.channels = hbaseC kafkaC
a1.sources.r1.bind = node1
a1.sources.r1.port = 5555 
a1.sources.r1.threads = 5 

#****************************flume + hbase******************************  
a1.channels.hbaseC.type = memory
a1.channels.hbaseC.capacity = 10000
a1.channels.hbaseC.transactionCapacity = 10000
a1.channels.hbaseC.keep-alive = 20

a1.sinks.hbaseSink.type = asynchbase
a1.sinks.hbaseSink.table = weblogs
a1.sinks.hbaseSink.columnFamily = info
a1.sinks.hbaseSink.serializer = org.apache.flume.sink.hbase.KfkAsyncHbaseEventSerializer
a1.sinks.hbaseSink.channel = hbaseC
a1.sinks.hbaseSink.serializer.payloadColumn = datetime,userid,searchname,retorder,cliorder,cliurl

#****************************flume + kafka******************************  
a1.channels.kafkaC.type = memory
a1.channels.kafkaC.capacity = 10000
a1.channels.kafkaC.transactionCapacity = 10000
a1.channels.kafkaC.keep-alive = 20

a1.sinks.kafkaSink.channel = kafkaC
a1.sinks.kafkaSink.type = org.apache.flume.sink.kafka.KafkaSink
a1.sinks.kafkaSink.brokerList = node1:9092,node2:9092,node3:9092
a1.sinks.kafkaSink.topic = weblogs
a1.sinks.kafkaSink.zookeeperConnect = node1:2181,node2:2181,node3:2181
a1.sinks.kafkaSink.requiredAcks = 1
a1.sinks.kafkaSink.batchSize = 1
a1.sinks.kafkaSink.serializer.class = kafka.serializer.StringEncoder

### 3.日志数据格式处理   
	cat weblog.log |tr "\t" "," > weblog2.log 	// 将制表符改为逗号
	cat word.txt | sed 's/[ ][ ]*/,/g'          // 将多个空格换位逗号

### 4.自定义flume的hbase sink并打成jar包上传到flume/lib下

### 5.创建weblogs项目来采集数据,并打成jar包发布到服务器node2和node3(/opt/jars)

### 6.编写启动jar包weblogs程序的shell在node2和node3(/opt/shell)
touch weblog-shell.sh  
#/bin/bash  

echo "start log......"  
java -jar /opt/jars/weblogs.jar /opt/data/weblog.log /opt/data/weblog-flume.log  

### 7.编写flume集群服务启动脚本(node1,node2,node3中flume目录下,下例在node2中,node3中a2改为a3)  
#/bin/bash  
echo "flume-2 start"  
bin/flume-ng agent --conf conf -f conf/flume-conf.properties -n a2 -Dflume.root.logger=INFO.console  

### 8.编写测试kafka消费的shell  
vi kfk-test-consumer.sh  

#/bin/bash  
echo "kfk-kafka-consumer.sh start......"  
bin/kafka-console-consumer.sh --zookeeper node1:2181,node2:2181,node3:2181 --from-beginning --topic weblogs  

### 9.进行测试flume采集数据的全流程 
	1）启动hdfs、zookeeper、kafka、flume
	2）启动weblog-shell.sh脚本 
	3）创建名为weblogs的topic
	bin/kafka-topics.sh --create --zookeeper node1:2181,node2:2181,node3:2181 --topic weblogs --partitions 1 --replication-factor 3
	4）启动node2、node3的脚本发送数据到node1
	5）启动node1的脚本接收node2、node3的数据,发送到hbase和kafka

### 10.安装mysql 

### 11.安装hive(启动hive前需先启动yarn,因为mapreduce需在yarn上运行)
	1）启动：bin/hive
	2）测试加载数据到hive：
	load data local inpath '/opt/data/test.txt' into table test;
	3）根据业务需求创建表结构
CREATE EXTERNAL TABLE weblogs(
id string,
datetime string,
userid string,
searchname string,
retorder string,
cliorder string,
cliurl string
)
STORED BY 'org.apache.hadoop.hive.hbase.HBaseStorageHandler'
WITH SERDEPROPERTIES("hbase.columns.mapping"=
":key,info:datetime,info:userid,info:searchname,info:retorder,info:cliorder,info:cliurl")
TBLPROPERTIES("hbase.table.name"="weblogs");

### 12.Hive与Hbase集成 
1）第一种方式，比较麻烦，将hbase下配置文件拷贝到hive/conf下  
2）第二种方式  
a）在hive-site.xml中配置  
<property>  
<name>hbase.zookeeper.quorum</name>  
<value>node1,node2,node3</value>  
</property>  
b）将hbase的9个jar拷贝到hive/lib下(high-scale-lib-1.1.2.jar自己下载)  
export HBASE_HOME=/opt/soft/hbase  
export HIVE_LIB=/opt/soft/hive-1.2.1-bin  
ln -s $HBASE_HOME/lib/hbase-server-1.1.3.jar $HIVE_LIB/lib/hbase-server-1.1.3.jar  

ln -s $HBASE_HOME/lib/hbase-client-1.1.3.jar $HIVE_LIB/lib/hbase-client-1.1.3.jar

ln -s $HBASE_HOME/lib/hbase-protocol-1.1.3.jar $HIVE_LIB/lib/hbase-protocol-1.1.3.jar 

ln -s $HBASE_HOME/lib/hbase-it-1.1.3.jar $HIVE_LIB/lib/hbase-it-1.1.3.jar 

ln -s $HBASE_HOME/lib/htrace-core-3.1.0-incubating.jar $HIVE_LIB/lib/htrace-core-3.1.0-incubating.jar

ln -s $HBASE_HOME/lib/hbase-hadoop2-compat-1.1.3.jar $HIVE_LIB/lib/hbase-hadoop2-compat-1.1.3.jar 

ln -s $HBASE_HOME/lib/hbase-hadoop-compat-1.1.3.jar $HIVE_LIB/lib/hbase-hadoop-compat-1.1.3.jar 

ln -s $HBASE_HOME/lib/high-scale-lib-1.1.2.jar $HIVE_LIB/lib/high-scale-lib-1.1.2.jar 

ln -s $HBASE_HOME/lib/hbase-common-1.1.3.jar $HIVE_LIB/lib/hbase-common-1.1.3.jar

### 13.Hue安装部署 
1）下载  
2）编译Hue  
	a）安装需要依赖的包(下面的包可能多了几个)  
	yum install ant asciidoc cyrus-sasl-devel cyrus-sasl-gssapi cyrus-sasl-plain gcc gcc-c++ krb5-devel libtidy libffi-devel libxml2-devel libxslt-devel make mysql mysql-devel openldap-devel python-devel sqlite-devel gmp-devel openssl-devel mysql-devel 
	b）hue文件中：make apps  
3）配置(vi $HUE_HOME/desktop/conf/hue.ini)  
	secret_key=jFE93j;2[290-eiw.KEiwN2s3['d;/.q[eIW^y#e=+Iei*@Mn < qW5o
	http_host=node3
    http_port=8888
    time_zone=Asia/Shanghai
4）设置desktop.db的权限 
	[root@node3 desktop]# chmod o+w desktop.db
5）启动服务
	[root@node3 hue-4.0.0]# ./build/env/bin/supervisor
	如果出现错误KeyError: "Couldn't get user id for user hue"
	如下：adduser hue,并将desktop.db改为hue:hue下,不要在root下
	chown -R hue:hue desktop.db
6）Hue与Hive集成(hue.ini)
	fs_defaultfs=hdfs://node1:8020		// hdfs默认路径    
	webhdfs_url=http://node1:50070/webhdfs/v1  
	hadoop_conf_dir=/opt/soft/hadoop-2.6.4/etc/hadoop  
	hadoop_bin=/opt/soft/hadoop-2.6.4/bin  
    hadoop_hdfs_home=/opt/soft/hadoop-2.6.4  
    ------------------------------------------------------------
    // 在三台hadoop中的core-site.xml中添加内容：
    <property>
  		<name>hadoop.proxyuser.hue.hosts</name>
  		<value>*</value>
	</property>
	<property>
		<name>hadoop.proxyuser.hue.groups</name>
		<value>*</value>
	</property>
	------------------------------------------------------------
	启动hdfs：
	start-dfs.sh 
	------------------------------------------------------------
	访问url：
	http://node3:8888/filebrowser/
7）Hue与Yarn集成(hue.ini)  
	resourcemanager_host=zxl2  
	resourcemanager_port=8032  
	resourcemanager_api_url=http://node1:8088  
	proxy_api_url=http://node1:8088  
	history_server_api_url=http://node1:19888  
	------------------------------------------------------------
	启动yarn：  
	start-yarn.sh  
8）Hue与Hive集成(Hue.ini)  
	hive_server_host=node3    
	hive_server_port=10000  
	hive_conf_dir=/opt/soft/hive-1.2.1-bin/conf  
	------------------------------------------------------------
	启动hive  
	[root@node3 bin]# ./hive --service hiveserver2    
9）Hue与Mysql集成(Hue.ini)  
	nice_name="My SQL DB"		// 随意配置  
	name=metastore				// 数据库名  
	engine=mysql  
	host=node3   
	port=3306  
	user=root 
	password=1234  
	注意：[[[mysql]]]前的##要删掉  
10）Hue与Hbase集成(Hue.ini)  
	hbase_clusters=(Cluster|node1:9090)			// 随意配置集群中某一台hbase  
	hbase_conf_dir=/opt/soft/hbase/conf  
	------------------------------------------------------------
	启动hbase(thrift)  
	[root@node1 hbase]# bin/start-hbase.sh  
	[root@node1 hbase]# bin/hbase-daemon.sh start thrift	// 启动一个就行  

### 14.配置Spark集群模式，因为受内存的影响，配置为standlone模式

### 15.配置Spark SQL与Hive集成(此次在安装Hive的服务器里的spark配置)
1）将hive的配置文件hive-site.xml拷贝到spark conf目录，同时添加metastore的url配置  
	vi hive-site.xml  
	<property>  
		<name>hive.metastore.uris</name>  
		<value>thrift://node3:9083</value>  
	</property>  
2）拷贝hive中的mysql jar包到spark的jar目录下  
	cp hive-1.2.1-bin/lib/mysql-connector-java-5.1.35-bin.jar spark-2.2.0/jars/  
3）检查spark-env.sh 文件中的配置项  
	vi spark-env.sh  
	HADOOP_CONF_DIR=/opt/soft/hadoop-2.6.4/etc/hadoop  
4）启动mysql  
	service mysqld start  
5）启动hive metastore服务  
	bin/hive --service metastore
6）启动hive并测试下  
	bin/hive  
	show databases;  
	create database zxl;  
	use zxl;  
	create table if not exists test(userid string,username string)ROW FORMAT DELIMITED FIELDS TERMINATED BY ' ' STORED AS textfile;
	load data local inpath "/opt/data/test.txt" into table test;  

	more /opt/data/test.txt  
	0001 spark  
	0002 hive  
	0003 hbase  
	0004 hadoop  
7）启动spark-shell  
	bin/spark-shell  
	spark.sql("select * from zxl.test").show  
8）展示启动spark-sql  
	bin/spark-sql  
	show databases;		#查看数据库  
	default  
	zxl  
	use zxl;		#使用数据库
	show tables;	#查看表  
	test  
	select * from test;		#查看表数据  
9）Spark SQL之ThriftServer和beeline使用    
	a）启动ThriftServer  
	sbin/start-thriftserver.sh  
	b）启动beeline  
	bin/beeline !connect jdbc:hive2://node3:10000  
	show databases;		#查看数据库  
	select * from kfk.test;		#查看表数据  

### 16.配置Spark SQL与MySQL集成(spark1为test数据库中的表)
	启动spark-shell
	bin/spark-shell
	:paste		#可以多行输入,包括注释,需顶格书写
val jdbcDF = spark.read
.format("jdbc")
.option("url", "jdbc:mysql://node3:3306/test")
.option("dbtable", "spark1")
.option("user", "root")
.option("password", 1234)
.load()
  	ctr+d 		#退出
	#打印读取数据
	jdbcDF.show

### 17.配置Spark SQL与Hbase集成
	Spark SQL与HBase集成，其核心就是Spark Sql通过hive外部表来获取HBase的表数据。
	1）拷贝HBase的包和hive包到spark 的jars目录下
		hbase-client-1.1.3.jar 
		hbase-common-1.1.3.jar 
		hbase-protocol-1.1.3.jar 
		hbase-server-1.1.3.jar 
		hive-hbase-handler-1.2.1.jar 
		htrace-core-3.1.0-incubating.jar 		#incubating表示刚出现版本
		mysql-connector-java-5.1.35-bin.jar
	2）启动spark-shell
	bin/spark-shell
	val df =spark.sql("select count(1) from weblogs").show

### 18.安装nc作为外部数据源
	yum -y install nc 或者 rpm安装(rpm -ivh nc-1.84-24.el6.x86_64.rpm)

### 19.简单运行nc与spark例子
	[root@node2 ~]# nc -lk 9999
	[root@node2 spark-2.2.0]# bin/run-example --master local[2] streaming.NetworkWordCount localhost 9999
	注：记得设置master时，local[n]，n的值一定要大于worker的个数

### 20.Spark Streaming结果数据保存到外部数据库(mysql)
	// 一般与数据库建立连接时，使用foreachPartition来避免频繁创建数据库连接
	 Class.forName("com.mysql.jdbc.Driver")
     val conn = DriverManager
       .getConnection("jdbc:mysql://node3:3306/test","root","1234")
     try{
        for(row <- line){
          val sql = "insert into webCount(titleName,count)values('"+row._1+"',"+row._2+")"
          conn.prepareStatement(sql).executeUpdate()
        }
     }finally {
        conn.close()
     }

### 21.StructuredStreaming与kafka、mysql集成  
	添加spark一些jar，spark+kfk和spark+hbase

### 22.创建表webCount用来接收数据 
CREATE TABLE `webCount` (
  `titleName` varchar(255) DEFAULT NULL,
  `count` int(11) DEFAULT NULL
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

### 23.简单流程测试：
	1）启动zookeeper：zkServer.sh start 
	2）启动dfs：start-dfs.sh 
	3）启动hbase：start-hbase.sh 
	4）启动mysql；service mysqld start 
	5）node2(node3)启动flume：flume-kfk-start.sh 
	6）node1启动flume：flume-kfk-start.sh 
	7）启动kafka-0.10(最好三台都启动，不然易出错)：
		bin/kafka-server-start.sh config/server.properties > kafka.log 2>&1 & 
	8）启动node2(node3)中的脚本：weblog-shell.sh 
	9）启动应用程序
	10）解决Structured Streaming向数据库写入乱码
    1）修改数据库文件my.cnf(linux下)
    vi my.cnf 
    -----------------------------------------------------------------------------
    [client]
    socket=/var/lib/mysql/mysql.sock    //添加
    default-character-set=utf8          //添加
    [mysqld]
    character-set-server=utf8           //添加
    datadir=/var/lib/mysql
    socket=/var/lib/mysql/mysql.sock
    user=mysql
    # Disabling symbolic-links is recommended to prevent assorted security risks
    symbolic-links=0
    [mysqld_safe]
    log-error=/var/log/mysqld.log
    pid-file=/var/run/mysqld/mysqld.pid
    -----------------------------------------------------------------------------
    2）建表时形如下：
    CREATE TABLE `webCount` (
      `titleName` varchar(255) CHARACTER SET utf8 DEFAULT NULL,
      `count` int(11) DEFAULT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;
