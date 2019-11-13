package DatabaseServer;

import DatabaseServer.Interfaces.IDataBase;
import ZooKeeper.ZooKeeperConnector;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

// import zookeeper classes
import org.apache.zookeeper.*;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.data.Stat;

public class DatabaseServer {

    private static final String FILE_SEPARATOR = File.separator;
    // property fetches the home path
    private static final String ZK_PATH = System.getProperty("user.home")
            + FILE_SEPARATOR + "AllstoresDB" + FILE_SEPARATOR;

    private static ZooKeeperConnector zooKeeperConnector;


    public static void main(String[] args) throws Exception {
        // connect and register in zookeeper
        String zooKeeperHost = getZooKeeperHost();
        ZooKeeper zooKeeper = zooKeeperConnector.connect(zooKeeperHost);

        Stat appStat = zooKeeper.exists("/db", false);
        if (appStat == null) {
            zooKeeper.create("/db", "15500".getBytes(), ZooDefs.Ids.READ_ACL_UNSAFE, CreateMode.PERSISTENT);
            appStat = zooKeeper.exists("/db", false);
        }

        String znodePath = zooKeeper.create("/db/", "".getBytes(), ZooDefs.Ids.READ_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        String[] znodePathSplit = znodePath.split("/");
        int sequentialNumber = Integer.parseInt(znodePathSplit[znodePathSplit.length - 1].replace("0",""));
        int port = Integer.parseInt(new String(zooKeeper.getData("/db", false, appStat))) + sequentialNumber;

        // port must be higher than 15500 so it doesn't collide with AllStoresApp
        assert port > 15500;

        IDataBase database = new DatabaseImpl();

        /* Create registry and rebind it to port DATABASE_PORT */
        Registry registry = null;
        try {
            registry = LocateRegistry.createRegistry(port);
            registry.rebind("AllstoresDatabaseServer", database);
        } catch (Exception e) {
            System.err.println("DataBase: ERROR trying to start server");
            e.printStackTrace();
            System.exit(0);
        }

        /* Get server address */
        String address = null;
        try {
            address = System.getProperty("java.rmi.server.hostname");
            // is address null? if so then it is 127.0.0.1 (localhost), else it still is address
            address = address == null ? "127.0.0.1" : address;
        } catch (Exception e) {
            System.out.println("Can't get inet address.");
            System.exit(0);
        }

        String myID = address + ":" + "AllstoresDatabaseServer";
        System.out.println(myID);
    }

    private static String getZooKeeperHost() throws IOException {
        BufferedReader fileReader = new BufferedReader(new FileReader(ZK_PATH + "conf.cfg"));
        String result = fileReader.readLine();
        fileReader.close();
        return result;
    }

}
