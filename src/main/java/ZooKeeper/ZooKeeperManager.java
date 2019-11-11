package ZooKeeper;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.util.List;

public class ZooKeeperManager {

    private ZooKeeperConnector zooKeeperConnector;
    private ZooKeeper zooKeeper;

    public ZooKeeperManager(String path) {
        initialize(path);
    }

    /*
     * Initialize connection with ZooKeeper server
     */
    private void initialize(String path) {
        try {
            zooKeeperConnector = new ZooKeeperConnector();
            zooKeeper = zooKeeperConnector.connect(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Close the ZooKeeper connection
     */
    public void closeConnection() {
        try {
            zooKeeperConnector.close();
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Creates a new zNode and saves data on it
     * @param path zNode path
     * @param data data to be saved
     * @param mode zNode mode,
     *             check available modes here https://zookeeper.apache.org/doc/r3.1.2/api/org/apache/zookeeper/CreateMode.html
     * @throws Exception
     */
    public void create(String path, byte[] data, CreateMode mode) throws Exception {
        zooKeeper.create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, mode);
    }

    /**
     *
     * @param path
     * @param data
     * @throws Exception
     */
    public void updateNode(String path, byte[] data) throws Exception {
        zooKeeper.setData(path, data, getStats(path, false).getVersion());
    }

    /**
     *
     * @param path
     * @throws Exception
     */
    public void deleteNode(String path) throws Exception {
        zooKeeper.delete(path, getStats(path, false).getVersion());
    }

    /**
     * Get the zNode stats
     * @param path zNode path
     * @param watch if a watch should be left on the zNode
     * @return
     * @throws KeeperException
     * @throws InterruptedException
     */
    public Stat getStats(String path, boolean watch) throws KeeperException, InterruptedException {
        return zooKeeper.exists(path, watch);
    }

    /**
     *
     * @param path
     * @param watch
     * @return
     * @throws Exception
     */
    public String getData(String path, Watcher watch) throws Exception {
        Stat stat = getStats(path, true);
        byte[] b;
        String result = null;
        if (stat != null) {
            b = zooKeeper.getData(path, watch, null);
            result = new String(b);
        }
        return result;
    }

    /**
     *
     * @param path
     * @return
     * @throws KeeperException
     * @throws InterruptedException
     */
    public List<String> getNodeChildren(String path) throws KeeperException, InterruptedException {
        Stat stat = getStats(path, false);
        return stat != null ? zooKeeper.getChildren(path, false) : null;
    }

}
