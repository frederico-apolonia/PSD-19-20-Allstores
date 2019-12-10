package AllStoresServer;

import AllStoresServer.Interfaces.AllStoresServerInterface;
import DatabaseServer.Interfaces.IDataBase;
import ZooKeeper.ZooKeeperConnector;
import DatabaseServer.Product;
import DatabaseServer.Reservation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

public class AllStoresServerImp extends UnicastRemoteObject implements AllStoresServerInterface {

	private ZooKeeper zooKeeper;

	private static final int NUMBER_OF_STORES = 600;
	// zookeeperID, serverIP:port
	private HashMap<Integer, String> dbServers;

	public AllStoresServerImp(ZooKeeper zooKeeper) throws Exception {
		this.zooKeeper = zooKeeper;
		this.dbServers = new HashMap<>();
		fetchDbServers();
		setDbServersChangedWatcher();
	}

	private void fetchDbServers() {

		try {
			List<String> dbServerNames = this.zooKeeper.getChildren("/db/clients", false);
			dbServerNames.sort(String::compareTo);

			System.out.println(String.format("Found %d database servers", dbServerNames.size()));

			Stat currDb;
			byte[] zNodeData;
			String serverHost;
			int serverId;
			this.dbServers = new HashMap<>();
			for (String dbServer: dbServerNames) {
				serverId = Integer.parseInt(dbServer.replaceFirst("^0+(?!$)", ""));
				String dbServerPath = "/db/clients/".concat(dbServer);
				currDb = this.zooKeeper.exists(dbServerPath, false);
				zNodeData = this.zooKeeper.getData(dbServerPath, false, currDb);
				serverHost = new String(zNodeData);
				this.dbServers.put(serverId, serverHost);
			}
			System.out.println("Detected database servers:");
			for (int i: this.dbServers.keySet()) {
				System.out.println(String.format("Server with id %d: %s", i, this.dbServers.get(i)));
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	private void setDbServersChangedWatcher() throws KeeperException, InterruptedException {
		System.out.println("Setting DB servers changed watcher...");
		Watcher dbsChangedWatcher = watchedEvent -> {
			if (watchedEvent.getType() == Watcher.Event.EventType.NodeChildrenChanged) {
				assert watchedEvent.getPath().equals("/db/clients");
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				fetchDbServers();

				try {
					setDbServersChangedWatcher();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		zooKeeper.getChildren("/db/clients", dbsChangedWatcher);
	}

	/* Finds the correct database address for the given storeID */
	private String findDatabaseServer(int storeID) {
		String result = null;
		try {

			int numberServers = this.dbServers.size();
			int inf = 1;
			int sup = NUMBER_OF_STORES / numberServers;
			List<Integer> keyset = new ArrayList<>(this.dbServers.keySet());
			boolean found = false;
			for (int i = 0; i < numberServers && !found; i++) {

				if(storeID >= inf && storeID <= sup) {
					//get the database address related to the storeID
					result = this.dbServers.get(keyset.get(i));
					found = true;
				} else {
					inf = sup + 1;
					sup += NUMBER_OF_STORES / numberServers;
				}
			}
			return  result;
		} catch (Exception e) { e.printStackTrace(); }

		return result;
	}

	/*
	 * Connects to DBServer given a znode data
	 */
	private IDataBase connectToDatabaseServer(String address) {
		assert address != null;

		String[] data = address.split(":");
		assert data.length == 2;

		String databaseHost = data[0];
		int databasePort = Integer.parseInt(data[1]);

		try {
			Registry registry = LocateRegistry.getRegistry(databaseHost, databasePort);
			return (IDataBase) registry.lookup("AllstoresDatabaseServer");

		} catch (AccessException e) {
			System.err.println("No access to execute the lookup method");
		} catch (RemoteException e) {
			System.err.println("Error while connecting to server! (Maybe it's offline?)");
		} catch (NotBoundException e) {
			System.err.println("No server found on the registry!");
		}
		return null;
	}

	@Override
	public String addReservation(int storeID, int productID, int quantity, int clientID) throws RemoteException {
		String databaseServer;
		IDataBase connectionDB;
		StringBuilder message = new StringBuilder();

		try {
			databaseServer = findDatabaseServer(storeID);
			connectionDB = connectToDatabaseServer(databaseServer);

			Product product = getProductFromList(connectionDB.getShopProducts(storeID), productID);
			if(product != null) {
				// verify if there is enough quantity to reserve
				if (product.getAvailable() >= quantity) {
					// check if the client already has a current reservation for this product
					Reservation reservation = connectionDB.findClientReservation(clientID, storeID, productID);
					if (reservation != null) {
						int updateQuantity = reservation.getQuantity() + quantity;
						if(connectionDB.updateClientReservation(reservation, updateQuantity)) {
							message.append("Reservation updated with success.\n");
						} else {
							message.append("There was a problem while updating your reservation.\n");
						}
						// client doesn't have an existing reservation for this product
					} else {
						reservation = new Reservation(clientID, storeID, productID, quantity);

						if(connectionDB.addReservation(reservation)) {
							message.append("New reservation added with success.\n");
						} else {
							message.append("There was a problem while adding your reservation.\n");
						}
					}
					int remainingAvailable = product.getAvailable() - quantity;
					message.append(String.format("Remaining stock: %d", remainingAvailable));
				}  else {
					// not enough stock to fulfill this reservation request
					message.append("There's no available stock to fulfill your request.");
					message.append(String.format("Remaining stock: %d", product.getAvailable()));
				}
			} else {
				message.append(String.format("Product %d doesn't exist", productID));
			}

		} catch (NullPointerException e) {
			// if Null Pointer is thrown, call the function again. The server might have gone down
			// let the DBs notice and reorganize
			try {
				Thread.sleep(500);
			}
			catch(InterruptedException ex) {
				// https://www.javaspecialists.eu/archive/Issue056.html
				Thread.currentThread().interrupt();
			}
			return addReservation(storeID, productID, quantity, clientID);
		}
		return message.toString();
	}

	@Override
	public List<String> cancel(int clientID) throws RemoteException {
		IDataBase connectionDB;
		boolean result = false;
		List<String> returnReserves = new ArrayList<>();

		List<Integer> dbServersKeys = new ArrayList<>(this.dbServers.keySet());

		List<Reservation> clientReservations;
		for (int i = 0; i < dbServersKeys.size(); i++) {
			// goes thru each database server and removes all reservations associated with this client
			connectionDB = connectToDatabaseServer(this.dbServers.get(dbServersKeys.get(i)));

			try {
				clientReservations = connectionDB.getClientReservations(clientID);

				if(clientReservations.size() != 0) {
					List<String> tmpReserves = new ArrayList<>();
					for (Reservation r : clientReservations) {
						tmpReserves.add(String.format("Product ID %d: canceled %d products.", r.getProductID(),
								r.getQuantity()));
					}
					if(connectionDB.cancelAllReservations(clientID)) {
						returnReserves.addAll(tmpReserves);
					} else {
						returnReserves.add("ERROR");
					}
				}
			} catch (NullPointerException e) {
				System.err.println(String.format("Error while connecting to database %s", dbServersKeys.get(i)));
			}
		}

		return returnReserves;

	}

	public List<String> getList(int storeID) throws RemoteException {
		// devolve uma lista com a informação de cada produto existente na loja
		// <storeID>
		String databaseServer;
		IDataBase connectionDB;
		List<String> result = new ArrayList<>();

		databaseServer = findDatabaseServer(storeID);
		connectionDB = connectToDatabaseServer(databaseServer);
		try {
			List<Product> shopProducts = connectionDB.getShopProducts(storeID); // vai buscar a lista de produtos da loja
			// pedida
			for (Product p : shopProducts) {
				result.add(p.toString()); // para cada produto na lista vai buscar a sua informação completa (String)
			}
		} catch (NullPointerException e) {
			// if Null Pointer is thrown, call the function again. The server might have gone down
			// let the DBs notice and reorganize
			try {
				Thread.sleep(500);
			}
			catch(InterruptedException ex) {
				// https://www.javaspecialists.eu/archive/Issue056.html
				Thread.currentThread().interrupt();
			}
			return getList(storeID);
		}
		return result;
	}


	public String buy(int clientID, int storeID, int productID, int quantity) throws RemoteException {
		String databaseServer;
		IDataBase connectionDB;
		StringBuilder sb = new StringBuilder();

		try {
			databaseServer = findDatabaseServer(storeID);
			connectionDB = connectToDatabaseServer(databaseServer);
			assert connectionDB != null;
			Product product = getProductFromList(connectionDB.getShopProducts(storeID), productID);

			if(product != null) {
				// verifica se o cliente já tem reservas desse produto e
				// se tiver, verifica se é em menor ou maior quantidade da pretendida
				Reservation reservedProduct = connectionDB.findClientReservation(clientID, storeID, productID);

				// cliente tem reserva
				if (reservedProduct != null) {
					int shopRemainingAvailable = product.getAvailable();
					int reservationRemainingAvailable = reservedProduct.getQuantity() - quantity;
					// se qnt reservado > qnt, compra o produto e atualiza a reserva
					if (reservedProduct.getQuantity() > quantity) {

						if (connectionDB.buyProduct(storeID, productID, quantity, clientID)) {
							sb.append(String.format("Product bought with success! You still have %d reserved units and there " +
									"are %d units available on the shop.",reservationRemainingAvailable, shopRemainingAvailable));
						} else {
							sb.append("Something happened while your product was being bought. Try again later.");
						}

					} else { // se qnt reservado <= qnt, verifica se há a diferença em loja
						if (quantity - reservedProduct.getQuantity() <= product.getAvailable()) {

							if (connectionDB.buyProduct(storeID, productID, quantity, clientID)) {
								sb.append(String.format("Product bought with success! You have 0 reserved units and there" +
										" are %d units available on the shop.",shopRemainingAvailable));
							} else {
								sb.append("Something happened while your product was being bought. Try again later.");
							}

						} else {
							sb.append(String.format("ERROR! You're trying to buy more than what's available! There's %d available " +
									"units reserved and there are %d products in stock.", reservedProduct.getQuantity(), product.getAvailable()));
						}
					}

				} else { // se não tiver nenhuma reserva desse produto, verifica se existe a quantidade
					// em loja e efetua ou não a compra
					if (quantity <= product.getAvailable()) {

						if(connectionDB.buyProduct(storeID, productID, quantity, clientID)) {
							sb.append(String.format("Product bought with success! Number of remaining units: %d ", product.getAvailable()));
						}

					} else {
						sb.append(String.format("ERROR! You're trying to buy more than what's currently available! There " +
								"are %d units left in stock", product.getAvailable()));
					}
				}
			} else {
				sb.append("ERROR! The product that you're trying to buy doesn't exist!");
			}

		} catch (NullPointerException e) {
			// if Null Pointer is thrown, call the function again. The server might have gone down
			// let the DBs notice and reorganize
			try {
				Thread.sleep(500);
			}
			catch(InterruptedException ex) {
				// https://www.javaspecialists.eu/archive/Issue056.html
				Thread.currentThread().interrupt();
			}
			return buy(clientID, storeID, productID, quantity);
		}
		return sb.toString();
	}


	private Product getProductFromList(List<Product> prodList, int productID) {
		Product result = null;
		for (Product p : prodList) {
			if (productID == p.getProductID()) {
				result = p;
				break;
			}
		}
		return result;
	}

	public List<String> buyAll(int clientID) throws RemoteException {
		IDataBase connectionDB;
		List<String> soldList = new ArrayList<String>();

		int dbServers = this.dbServers.size();
		for (int i = 1; i <= dbServers; i++) {
			connectionDB = connectToDatabaseServer(this.dbServers.get(i));

			List<Reservation> reservedList = connectionDB.getClientReservations(clientID);

			if (reservedList != null) {
			for (Reservation r : reservedList) { // para cada reserva do cliente, efetua a compra
				int prod = r.getProductID();
				int qnt = r.getQuantity();

				connectionDB.buyProduct(r.getShopID(), r.getProductID(), r.getQuantity(), clientID);

				String info = "Produto " + prod + ", " + qnt + "unidades.";
				soldList.add(info);
			}
		} else {
			soldList.add(" ");
		}
	}
		return soldList;
	}
}
