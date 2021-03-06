package com.zyblue.fastim.leaf.processor;

import com.zyblue.fastim.leaf.config.ZKConfig;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 获取workerId
 * @author will
 */
@Component
public class NodeProcessor {

    private final static Logger logger = LoggerFactory.getLogger(NodeProcessor.class);

    /**
     * leaf的持久化节点
     **/
    private static final String LEAF_FOREVER = "/forever";
    /**
     * leaf的临时节点
     **/
    private static final String LEAF_TEMPORARY = "/temporary";

    /**
     * 心跳时间间隔
     */
    private static final Long HEARTBEAT_DELAY = 3L;

    /**
     * workerId 存储的位置
     */
    private static final String WORKERID_PATH = "/tmp";

    @Resource
    private ZKConfig zkConfig;

    @Resource
    private CuratorFramework curatorFramework;

    @Value("${server.port}")
    private int serverPort ;

    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * 本机的在ZK的存储的标识
     * IP:PORT-000001
     */
    private String nodeAddress;


    private Long lastUpdateTime = 0L;

    @Resource
    private ScheduledExecutorService scheduledExecutorService;

    @PostConstruct
    public void init(){
        if(curatorFramework == null){
            return;
        }

        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            /*
             * 本机的标识 IP:PORT
             */
            String pathAddress = hostAddress + ":" + serverPort;
            logger.info("pathAddress:{}", pathAddress);

            int workerId = 0;
            boolean exists = curatorFramework.checkExists().forPath(zkConfig.getZkRoot() + LEAF_FOREVER) != null;
            logger.info("exists:{}", exists);
            if(!exists){
                // 创建永久节点
                curatorFramework.create().withMode(CreateMode.PERSISTENT).forPath(zkConfig.getZkRoot() + LEAF_FOREVER);
                String nodeAddress = curatorFramework.create().withMode(CreateMode.PERSISTENT_SEQUENTIAL)
                        .forPath(zkConfig.getZkRoot() + LEAF_FOREVER + "/" + pathAddress + "-", Long.valueOf(System.currentTimeMillis()).toString().getBytes());
                logger.info("nodeAddress:{}", nodeAddress);
            }else {
                // 格式 ip:port-00001
                List<String> children = curatorFramework.getChildren().forPath(zkConfig.getZkRoot() + LEAF_FOREVER);
                boolean createNewNode = false;
                if(CollectionUtils.isEmpty(children)){
                    createNewNode = true;
                }else {
                    // 存在本机节点
                    List<String> nodeAddressList = children.stream().filter(x->x.startsWith(pathAddress)).collect(Collectors.toList());
                    int size = nodeAddressList.size();
                    logger.info("nodeAddressList size:{}", size);
                    if(size == 0){
                        createNewNode = true;
                    }else if(size == 1){
                        workerId = Integer.parseInt(nodeAddressList.get(0).split("-")[1]);
                        // 赋值，心跳用
                        nodeAddress = nodeAddressList.get(0);
                    }else {
                        throw new RuntimeException("匹配的节点过多，请检查zookeeper。节点：" + pathAddress);
                    }
                }

                if(createNewNode){
                    // 表示新启动的节点,创建持久节点，不用check时间

                    curatorFramework.create().withMode(CreateMode.PERSISTENT).forPath(zkConfig.getZkRoot() + LEAF_FOREVER);
                    nodeAddress = curatorFramework.create().withMode(CreateMode.PERSISTENT_SEQUENTIAL)
                            .forPath(zkConfig.getZkRoot() + LEAF_FOREVER + "/" + pathAddress + "-", Long.valueOf(System.currentTimeMillis()).toString().getBytes());
                    logger.info("nodeAddress:{}", nodeAddress);
                    workerId = Integer.parseInt(nodeAddress.split("-")[1]);
                }
            }

            logger.info("workerId:{}", workerId);
            zkConfig.setWorkerId(workerId);

            /*
             * 写入文件
             */
            writeWorkerId2Local(workerId);

            /*
             * 定时心跳 写入时间
             */
            scheduledExecutorService.scheduleWithFixedDelay(this::zookeeperHeartBeat, 1L, HEARTBEAT_DELAY, TimeUnit.SECONDS);
        }catch (Exception e){
            logger.error("e:", e);
            readWorkerIdFromLocal();
        }
    }

    /**
     * 文件读取workerId
     */
    private void readWorkerIdFromLocal(){
        logger.info("开始从文件中读取workerId");
        String path = WORKERID_PATH + File.separator + applicationName + File.separator + "workerId.properties";
        File file = new File(path);
        if(file.exists() && file.isFile()){
            Properties properties = new Properties();
            try {
                properties.load(new FileInputStream(file));
                zkConfig.setWorkerId(Integer.valueOf(properties.getProperty("workerId")));
            }catch (Exception e1){
                logger.error("e:", e1);
                throw new RuntimeException("workerId.properties is not available");
            }
        }else {
            throw new RuntimeException("workerId.properties is not exits or zookeeper is not available");
        }
    }

    /**
     * 高可用，防止zookeeper挂了，本地本机生效
     */
    private void writeWorkerId2Local(int workerId){
        String path = WORKERID_PATH + File.separator + applicationName + File.separator + "workerId.properties";
        File file = new File(path);
        try (RandomAccessFile rFile = new RandomAccessFile(file, "rw")){
            if(file.exists() && file.isFile()){
                rFile.setLength(0);
                rFile.write(("workerId=" + workerId).getBytes());
            }else {
                boolean mkdirs = file.getParentFile().mkdirs();
                if(mkdirs){
                    try {
                        if (file.createNewFile()) {
                            rFile.write(("workerId=" + workerId).getBytes());
                            logger.info("local file cache workerID is {}", workerId);
                        }
                    }catch (Exception e){
                        logger.error("e:", e);
                    }
                }
            }
        }catch (Exception e){
            logger.error("e:", e);
        }

    }

    public void zookeeperHeartBeat(){
        logger.info("upload local time to Zookeeper");
        long now = System.currentTimeMillis();
        if(now < lastUpdateTime){
            logger.error("wrong!! lastUpdateTime > now");
            return;
        }else {
            // 定时写入本机时间
            try {
                curatorFramework.setData().forPath(zkConfig.getZkRoot() + LEAF_FOREVER + "/" + nodeAddress,
                        Long.valueOf(now).toString().getBytes());
            } catch (Exception e) {
                logger.error("e:", e);
            }
            lastUpdateTime = now;
        }
        logger.info("upload local time to Zookeeper end");
    }
}
