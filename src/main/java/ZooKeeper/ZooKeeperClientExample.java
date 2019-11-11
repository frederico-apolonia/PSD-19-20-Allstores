package ZooKeeper;

import org.apache.log4j.BasicConfigurator;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.util.List;

public class ZooKeeperClientExample {
    private static ZooKeeperManager zooKeeperManager = new ZooKeeperManager("127.0.0.1");

    public static void main(String[] args) throws Exception {
        print("Strings sem fazer nada ao ZooKeeper");
        printList(zooKeeperManager.getNodeChildren("/"));

        print("Adicionar um novo no No1, com data xyz");
        zooKeeperManager.create("/No1", "xyz".getBytes(), CreateMode.PERSISTENT);
        printList(zooKeeperManager.getNodeChildren("/"));

        String data = zooKeeperManager.getData("/No1", null);
        print(String.format("Data do no criado: %s", data));

        zooKeeperManager.deleteNode("/No1");
    }

    public static void print(String s) {
        System.out.println(s);
    }

    public static void printList(List<String> list) {
        for(String s : list)
            print(s);
    }
}
