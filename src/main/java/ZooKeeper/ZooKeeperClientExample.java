package ZooKeeper;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.util.List;

public class ZooKeeperClientExample {
    private static ZooKeeperConnector zooKeeperManager;
    private static ZooKeeper zooKeeper;
    private static Watcher childrenChangedWatcher;
    private static AsyncCallback.ChildrenCallback childrenCallBack;

    public static void main(String[] args) throws Exception {
        zooKeeper = zooKeeperManager.connect("127.0.0.1");

        print("Strings sem fazer nada ao ZooKeeper");
        Stat stat = zooKeeper.exists("/", false);
        assert stat != null;

        printList(zooKeeper.getChildren("/", null));

        print("Adicionar um novo no No1, com data xyz");
        zooKeeper.create("/No1", "xyz".getBytes(), ZooDefs.Ids.READ_ACL_UNSAFE, CreateMode.PERSISTENT);


        Stat no1Stat = zooKeeper.exists("/", false);
        assert no1Stat != null;

        byte[] b = zooKeeper.getData("/No1", false, null);
        String result = new String(b);
        print(String.format("Data do no criado: %s", result));

        //childrenUpdateDemo();
        //Thread.sleep(15000);

        zooKeeper.delete("/No1", stat.getVersion());
    }

    public static void childrenUpdateDemo() throws Exception {
        childrenChangedWatcher = watchedEvent -> {
            if (watchedEvent.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
                try {
                    printList(zooKeeper.getChildren(watchedEvent.getPath(), null));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        childrenCallBack = new AsyncCallback.ChildrenCallback() {
            public void processResult(int rc, String path, Object ctx, List<String> children) {
                switch (KeeperException.Code.get(rc)) {
                    case CONNECTIONLOSS:
                        getUpdatedChildren();

                        break;
                    case OK:
                        print("Succesfully got a list of workers: "
                                + children.size()
                                + " workers");

                        break;
                    default:
                        print("getChildren failed");
                }
            }
        };
        printList(zooKeeper.getChildren("/", childrenChangedWatcher));
    }

    public static void getUpdatedChildren() {
        zooKeeper.getChildren("/No1", childrenChangedWatcher, childrenCallBack, null);
    }

    public static void print(String s) {
        System.out.println(s);
    }

    public static void printList(List<String> list) {
        for(String s : list)
            print(s);
    }
}
