package ServerInTheMiddle;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import ManInTheMiddleClient.ClientServerInterface;
import DatabaseServer.DatabaseImpl;
import DatabaseServer.Product;
import DatabaseServer.Reservation;
import DatabaseServer.Interfaces.*;

public class ClientServerInterfaceImp extends UnicastRemoteObject implements ClientServerInterface {

	protected ClientServerInterfaceImp() throws RemoteException {
		super();


	}

	public String addReservation(int storeID, int productID, int quantitym, int clientID) throws RemoteException {

		IDataBase conectionBD;
		StringBuilder message = new StringBuilder();
		try {
			conectionBD = new DatabaseImpl();

			List<Product> listaProd = conectionBD.getShopProducts(storeID);

			// verifica se esse produto existe
			for (Product p : listaProd) {
				if (productID == p.getProductID()) {
					// falta ver se ja existe uma reserva DESTE CLIENTE
					Reservation reserve = conectionBD.findClientReservation(clientID, storeID, productID);

					if (reserve != null) {
						reserve.setQuantity(reserve.getQuantity() + quantitym);
						//elimina a reserva antiga e adiciona uma nova  
						conectionBD.removeReservation(conectionBD.findClientReservation(clientID, storeID, productID));
						conectionBD.addReservation(reserve);
						return message.append("Reserva foi atualizada com sucesso ").toString();

					} else {
						// a reserva nao exite sera que exite stock disponivel
						if (p.getAvailable() >= quantitym) {
							reserve = new Reservation(clientID, storeID, productID, quantitym);

							conectionBD.addReservation(reserve);

							return message.append("Nova reserva efetuada com sucesso " + p.getAvailable()
									+ "para o produto " + productID).toString();

						} else {
							// nao exite uma reserva nem stock disponivel
							return message.append("Isto não é a Amazon só temos de disponivel " + p.getAvailable()
									+ "para o produto " + productID).toString();
						}

					}

				}
			}

			return message.append("Bro esse produto não existe!!").toString();

		} catch (Exception e) {
			System.err.println("Erro na conexão entre o ServerInTheMiddle e a BD");
			e.printStackTrace();
		}
		return message.append("Server is Down!!!").toString();
	}

	public List<String> cancel(int clientID) {
		IDataBase conectionBD;

		try {
			conectionBD = new DatabaseImpl();

			// obtem todas as reservas
			List<Reservation> listreserves = conectionBD.getClientReservations(clientID);
			
			List<String> returnReserves = new ArrayList<String>();
			
			if(listreserves.size()==0) 
			{
				
				for (Reservation r : listreserves) {
					
					returnReserves.add("O id do produto"+ r.getProductID() + "a quantidade cancelada "+ r.getQuantity());
					// ahhhhhh ja percebi false ele diminui
					conectionBD.productUpdateReservation(r.getShopID(), r.getProductID(), r.getQuantity(), false);
					
				}
				
				boolean res = conectionBD.cancelAllReservations(clientID);
				
				if (res)
					return returnReserves;
				else
					return (new ArrayList<>());				
			}
			return returnReserves;
		} catch (Exception e) {
			System.err.println("Erro na conexão entre o ServerInTheMiddle e a BD");
			e.printStackTrace();
		}
		System.err.println("Erro no Servidor !!!");
		return (new ArrayList<>());

	}

	public List<String> getList(int storeID) throws RemoteException {
		// devolve uma lista com a informação de cada produto existente na loja
		// <storeID>
		IDataBase connectionDB;
		List<String> stockList = new ArrayList<String>();

		try {
			connectionDB = new DatabaseImpl();
			List<Product> listaProd = connectionDB.getShopProducts(storeID); // vai buscar a lista de produtos da loja
																				// pedida
			for (Product p : listaProd) {
				stockList.add(p.toString()); // para cada produto na lista vai buscar a sua informação completa (String)
			}
			return stockList;
		} catch (Exception e) {
			System.err.println("Erro na conexão entre o ServerInTheMiddle e a BD!");
			e.printStackTrace();
		}
		return stockList;
	}

	public String buy(int clientID, int storeID, int productID, int quantity) throws RemoteException {
		IDataBase connectionDB;

		try {
			connectionDB = new DatabaseImpl();
			List<Product> prodList = connectionDB.getShopProducts(storeID);

			// verifica se o produto existe nessa loja
			for (Product p : prodList) {
				if (productID == p.getProductID()) {

					// verifica se o cliente já tem reservas desse produto e
					// se tiver, verifica se é em menor ou maior quantidade da pretendida
					Reservation reservedProduct = connectionDB.findClientReservation(clientID, storeID, productID);

					if (reservedProduct != null) {

						// se qnt reservado > qnt, compra o produto e atualiza a reserva
						if (reservedProduct.getQuantity() > quantity) {

							Boolean check = connectionDB.buyProduct(storeID, productID, quantity, clientID);
							
							if(check) {
								return "Compra efetuada com sucesso! Unidades ainda disponíveis: " + reservedProduct.getQuantity()
									+ " reservadas e " + p.getAvailable() + " em stock.";
							}

						} else { // se qnt reservado <= qnt, verifica se há a diferença em loja
							if (quantity - reservedProduct.getQuantity() <= p.getAvailable()) {

								Boolean check = connectionDB.buyProduct(storeID, productID, quantity, clientID);

								if(check) {
									return "Compra efetuada com sucesso! Unidades ainda disponíveis: 0 reservadas e "
											+ p.getAvailable() + " em stock.";
								}

							} else {
								return "Erro: Não é possível efetuar a compra! Tem " + reservedProduct.getQuantity()
										+ " unidades desse produto reservado e existem " + p.getAvailable()
										+ " unidades disponíveis em stock.";
							}
						}

					} else { // se não tiver nenhuma reserva desse produto, verifica se existe a quantidade
								// em loja e efetua ou não a compra
						if (quantity <= p.getAvailable()) {

							Boolean check = connectionDB.buyProduct(storeID, productID, quantity, clientID);

							if(check) {
								return "Compra efetuada com sucesso! Unidades ainda disponíveis: " + p.getAvailable();
							}

						} else {
							return "Erro: só existem " + p.getAvailable() + " unidades desse produto em stock!";
						}
					}
				}
			}
			return "Esse produto não existe nessa loja!";

		} catch (Exception e) {
			System.err.println("Erro na conexão entre o ServerInTheMiddle e a BD!");
			e.printStackTrace();
		}
		return " ";
	}

	public List<String> buyAll(int clientID) throws RemoteException {
		IDataBase connectionDB;
		List<String> soldList = new ArrayList<String>();

		try {
			connectionDB = new DatabaseImpl();

			List<Reservation> reservedList = connectionDB.getClientReservations(clientID);

			if (reservedList != null) {
				for (Reservation r : reservedList) { // para cada reserva do cliente, efetua a compra
					int prod = r.getProductID();
					int qnt = r.getQuantity();

					connectionDB.buyProduct(r.getShopID(), r.getProductID(), r.getQuantity(), clientID);

					String info = "Produto " + prod + ", " + qnt + "unidades.";
					soldList.add(info);
				}

				return soldList;

			} else {

				soldList.add(" ");
				return soldList;

			}

		} catch (Exception e) {
			System.err.println("Erro na conexão entre o ServerInTheMiddle e a BD!");
			e.printStackTrace();
		}

		return null;
	}

}
