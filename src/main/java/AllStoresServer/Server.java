package AllStoresServer;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import AllStoresServer.Interfaces.AllStoresServerInterface;
import ZooKeeper.*;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.rmi.registry.Registry;


public class Server {

	private static ZooKeeperConnector zooKeeperConnector;
	private static final String FILE_SEPARATOR = File.separator;
	// property fetches the home path
	private static final String ZK_PATH = System.getProperty("user.home")
			+ FILE_SEPARATOR + "AllstoresDB" + FILE_SEPARATOR;

	public static void main(String[] args) throws Exception {

		String zooKeeperHost = getZooKeeperHost();
		ZooKeeper zooKeeper = zooKeeperConnector.connect(zooKeeperHost);

		Stat appStat = zooKeeper.exists("/app", false);
		if (appStat == null) {
			zooKeeper.create("/app", "15000".getBytes(), ZooDefs.Ids.READ_ACL_UNSAFE, CreateMode.PERSISTENT);
			appStat = zooKeeper.exists("/app", false);
		}

		String znodePath = zooKeeper.create("/app/", "".getBytes(), ZooDefs.Ids.READ_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
		String[] znodePathSplit = znodePath.split("/");
		int sequentialNumber = Integer.parseInt(znodePathSplit[znodePathSplit.length - 1].replace("0",""));
		int port = Integer.parseInt(new String(zooKeeper.getData("/app", false, appStat))) + sequentialNumber;

		// port must be lower than 15500 so it doesn't collide with DB
		assert port < 15500;

		// todo esta parte agora já não se processa assim!!
		AllStoresServerInterface testing = new AllStoresServerImp(!!, !!);
		
		Registry registry =  null;
		try {
			registry = LocateRegistry.createRegistry(port);
			registry.rebind("ClientServerInterface", testing);
			System.out.println("Server ready!");
		}
		catch (Exception e) {
			System.err.println("Server exception: Error trying to start.");
			System.exit(0);
		}
		
		// getting the server address
		String address = null;
		try {
			address = System.getProperty("java.rmi.server.hostname");
			address = address == null ? "127.0.0.1" : address;
		} catch (Exception e) {
			System.out.println("Can't get inet address.");
			System.exit(0);
		}

		String myID = address + ":" + "ClientServerInterface";
		System.out.println(myID);
	}

	private static String getZooKeeperHost() throws IOException {
		BufferedReader fileReader = new BufferedReader(new FileReader(ZK_PATH + "conf.cfg"));
		String result = fileReader.readLine();
		fileReader.close();
		return result;
	}

}
