package ZooKeeper;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class ZooKeeperConnector {
    private ZooKeeper zooKeeper;
    private final CountDownLatch connSignal = new CountDownLatch(1);

    /**
     * Connect to a ZooKeeper server
     * @param host all ip:host servers separated with a comma.
     * @return zooKeeper object with the connection
     * @throws IOException
     * @throws InterruptedException
     */
    public ZooKeeper connect(String host) throws IOException, InterruptedException {
        zooKeeper = new ZooKeeper(host, 3000, new Watcher() {
            @Override
            public void process(WatchedEvent watchedEvent) {
                if (watchedEvent.getState() == Event.KeeperState.SyncConnected) {
                    // The client is in the connected state - it is connected to a server in the ensemble (
                    connSignal.countDown();
                }
            }
        });
        connSignal.await();
        return zooKeeper;
    }

    /**
     * Disconnect from ZooKeeper server
     * @throws InterruptedException
     */
    public void close() throws InterruptedException {
        zooKeeper.close();
    }

}
