package sbft.demo.v2vapp;

import sbft.tom.MessageContext;
import sbft.tom.ServiceReplica;
import sbft.tom.server.defaultservices.DefaultSingleRecoverable;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
public class V2VServer extends DefaultSingleRecoverable {

    private Logger logger;
    private HashMap<String, Double> clientsAndFunds;
    private HashMap<Integer, BuyOrder> buyOrders = new HashMap<Integer, BuyOrder>();
    private ArrayList<Location> meetingPoints = new ArrayList<Location>();
    private HashMap<Integer, SellOrder> sellOrders = new HashMap<Integer, SellOrder>();
    int buyOrderId = 1;
    int sellOrderId = 1;
    double feeModifier = 0.01;
    double EVefficiency = 4.5;

    private long startTime;
    private long numRequests;
    private double maxThroughput;
    private Set<Integer> senders;


    public V2VServer(int id) {
        logger = Logger.getLogger(V2VServer.class.getName());
        clientsAndFunds = new HashMap<String, Double>();
        new ServiceReplica(id, this, this);
        senders = new HashSet<>(1000);
        Location location1 = new Location(1,1);
        Location location2 = new Location(2,2);
        Location location3 = new Location(3,3);
        Location location4 = new Location(4,4);
        meetingPoints.add(location1);
        meetingPoints.add(location2);
        meetingPoints.add(location3);
        meetingPoints.add(location4);

    }


    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: demo.map.MapServer <server id>");
            System.exit(-1);
        }
        new V2VServer(Integer.parseInt(args[0]));
    }


    @Override
    public void installSnapshot(byte[] state) {
        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(state);
             ObjectInput objIn = new ObjectInputStream(byteIn)) {
            clientsAndFunds = (HashMap<String, Double>)objIn.readObject();
        } catch (IOException | ClassNotFoundException e) {
            logger.log(Level.SEVERE, "Error while installing snapshot", e);
        }
    }

    @Override
    public byte[] getSnapshot() {
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             ObjectOutput objOut = new ObjectOutputStream(byteOut)) {
            objOut.writeObject(clientsAndFunds);
            return byteOut.toByteArray();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error while taking snapshot", e);
        }
        return new byte[0];
    }

    @Override
    public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
        byte[] reply = null;
        boolean hasReply = false;
        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(command);
                ObjectInput objIn = new ObjectInputStream(byteIn);
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                ObjectOutput objOut = new ObjectOutputStream(byteOut);) {
            V2VRequestType reqType = (V2VRequestType)objIn.readObject();
            String clientID = "";
            double amount = 0.0;
            int units = 0;
            double unitprice = 0.0;
            double originalPrice = 0.0;
            int xpos = 0;
            int ypos = 0;
            String output = "";
            numRequests ++;
            senders.add(msgCtx.getSender());
            switch (reqType) {
                case REGISTER:
                    clientID = (String)objIn.readObject();
                    if (clientsAndFunds.containsKey(clientID)) {
                        objOut.writeObject("client already registered!");
                    } else {
                        clientsAndFunds.put(clientID,0.0);
                        objOut.writeObject("Successfully registered with £0.0 funds!");
                    }
                    hasReply = true;
                    break;
                case DEPOSIT:
                    clientID = (String)objIn.readObject();
                    amount = (double)objIn.readObject();
                    if (clientsAndFunds.containsKey(clientID)) {
                        double currentFunds = clientsAndFunds.get(clientID);
                        clientsAndFunds.put(clientID, currentFunds+amount);
                        String success = "Deposited into client " + clientID + " amount £" + amount;
                        objOut.writeObject(success);
                    } else {
                        String notFound = "FAILED! Client " + clientID + "not found!";
                        objOut.writeObject(notFound);
                    }
                    hasReply = true;
                    break;
                case WITHDRAW:
                    clientID = (String)objIn.readObject();
                    amount = (double)objIn.readObject();
                    if (clientsAndFunds.containsKey(clientID)) {
                        double currentFunds = clientsAndFunds.get(clientID);
                        if (currentFunds < amount) {
                            String insufficient = "Insufficient Funds for " + clientID + " to withdraw £" + amount;
                            objOut.writeObject(insufficient);
                        } else {
                            clientsAndFunds.put(clientID, currentFunds-amount);
                            String success = "Withdrew from client " + clientID + " amount £" + amount;
                            objOut.writeObject(success);
                        }
                    } else {
                        String notFound = "FAILED! Client " + clientID + "not found!";
                        objOut.writeObject(notFound);
                    }
                    hasReply = true;
                    break;
                case BUYORDER:

                    clientID = (String)objIn.readObject();
                    units = (int)objIn.readObject();
                    xpos = (int)objIn.readObject();
                    ypos = (int)objIn.readObject();
                    originalPrice = (double)objIn.readObject();
                    output = buyOrder(clientID, units, xpos, ypos, originalPrice);
                    System.out.println(output);
                    hasReply = true;
                    objOut.writeObject(output);
                    break;
                case SELLORDER:
                    clientID = (String)objIn.readObject();
                    units = (int)objIn.readObject();
                    unitprice = (double)objIn.readObject();
                    xpos = (int)objIn.readObject();
                    ypos = (int)objIn.readObject();
                    originalPrice = (double)objIn.readObject();
                    output = sellOrder(clientID,units, unitprice, xpos, ypos, originalPrice);
                    hasReply = true;
                    objOut.writeObject(output);
                    break;
            }
            if (hasReply) {
                objOut.flush();
                byteOut.flush();
                reply = byteOut.toByteArray();
            } else {
                reply = new byte[0];
            }

        } catch (IOException | ClassNotFoundException e) {
            logger.log(Level.SEVERE, "Ocurred during server operation execution", e);
        }
        printMeasurement();

        return reply;
    }

    private void printMeasurement() {
        long currentTime = System.nanoTime();
        double deltaTime = (currentTime - startTime) / 1_000_000_000.0;
        if (true) {
            long delta = currentTime - startTime;
            double throughput = numRequests / deltaTime;
            if (throughput > maxThroughput)
                maxThroughput = throughput;
            System.out.println("M:(clients[" + senders.size() + "]|requests[" + numRequests + "]|delta[" + delta + "]|throughput[" + throughput + "ops/s], max[" + maxThroughput + "ops/s])");
            numRequests = 0;
            startTime = currentTime;
            senders.clear();
        }
    }


    private String buyOrder(String clientID, int units, int xpos, int ypos, double originalprice) {
        BuyOrder order = new BuyOrder(clientID, units, xpos, ypos, originalprice);
        int thisOrderID = buyOrderId;
        this.buyOrderId = this.buyOrderId + 1;
        buyOrders.put(thisOrderID, order);
        boolean found = false;
        OrderMatch match = new OrderMatch(-1, -1, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        double fee = order.units * this.feeModifier;
        for (Map.Entry<Integer, SellOrder> entry : sellOrders.entrySet()) {
            if (entry.getValue().unitsoffered < order.units) {
                continue;
            }
            double consumerEnergyCost = entry.getValue().unitPrice * order.units;
            double providerEnergyProfit = entry.getValue().unitPrice * order.units;
            for (Location mp : meetingPoints) {
                double consumerTravelCost = order.location.distance(mp) * EVefficiency * order.originalPrice;
                double providerTravelCost = entry.getValue().location.distance(mp) * EVefficiency * entry.getValue().originalPrice;
                double consumercost = (consumerEnergyCost + consumerTravelCost + fee) / units;
                double providerprofit = (providerEnergyProfit - providerTravelCost - fee) / units;
                if (providerprofit <= 0.0) {
                    continue;
                } else {
                    if (consumercost <= match.consumerCost) {
                        if (providerprofit > match.providerProfit) {
                            found = true;
                            match.buyOrder = thisOrderID;
                            match.sellOrder = entry.getKey();
                            match.consumerCost = consumercost;
                            match.providerProfit = providerprofit;
                        }
                    }
                }
            }
        }

        if (found) {
            BuyOrder bestBuyer = buyOrders.get(match.buyOrder);
            SellOrder bestSeller = sellOrders.get(match.sellOrder);
            String output = "CONFIRMED TRANSACTION \n CONSUMER:" + bestBuyer.cliendID + " PROVIDER: " + bestSeller.clientID + " UNITS: " + bestBuyer.units +
                    " UNITPRICE: " + bestSeller.unitPrice + " CONSUMERCOSTPERUNIT: " + match.consumerCost + " PROVIDERPROFITPERUNIT: " + match.providerProfit;
            buyOrders.remove(match.buyOrder);
            sellOrders.remove(match.sellOrder);
            return output;
        } else {
            return "NO MATCH FOUND CURRENTLY, INSERTING INTO SYSTEM";
        }

    }


    private String sellOrder(String clientID, int units, double unitprice, int xpos, int ypos, double originalprice) {
        SellOrder order = new SellOrder(clientID, units, unitprice, xpos, ypos, originalprice);
        int thisOrderID = sellOrderId;
        this.sellOrderId = this.sellOrderId + 1;
        sellOrders.put(thisOrderID, order);
        boolean found = false;
        OrderMatch match = new OrderMatch(-1, -1, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
        for (Map.Entry<Integer, BuyOrder> entry : buyOrders.entrySet()) {
            if (order.unitsoffered < entry.getValue().units) {
                continue;
            }
            double fee = feeModifier * entry.getValue().units;
            double consumerEnergyCost = order.unitPrice * entry.getValue().units;
            double providerEnergyProfit = order.unitPrice * entry.getValue().units;
            for (Location mp : meetingPoints) {
                double consumerTravelCost = entry.getValue().location.distance(mp) * EVefficiency * entry.getValue().originalPrice;
                double providerTravelCost = order.location.distance(mp) * EVefficiency * order.originalPrice;
                double consumercost = (consumerEnergyCost + consumerTravelCost + fee) / units;
                double providerprofit = (providerEnergyProfit - providerTravelCost - fee) / units;
                if (providerprofit <= 0.0) {
                    continue;
                } else {
                    if (consumercost <= match.consumerCost) {
                        if (providerprofit > match.providerProfit) {
                            found = true;
                            match.buyOrder = entry.getKey();
                            match.sellOrder = thisOrderID;
                            match.consumerCost = consumercost;
                            match.providerProfit = providerprofit;
                        }
                    }
                }
            }
        }

        if (found) {
            System.out.println(match.buyOrder);
            System.out.println(match.sellOrder);
            System.out.println(buyOrders.keySet());
            System.out.println(sellOrders.keySet());
            BuyOrder bestBuyer = buyOrders.get(match.buyOrder);
            SellOrder bestSeller = sellOrders.get(match.sellOrder);
            if (bestBuyer == null || bestSeller == null) {
                System.out.println("JAJAJAJAJ");
            }
            System.out.println(bestBuyer.cliendID);
            System.out.println(bestSeller.clientID);
            System.out.println(bestBuyer.units);
            System.out.println(bestSeller.unitPrice);
            System.out.println(match.consumerCost);
            System.out.println(match.providerProfit);
            String output = "CONFIRMED TRANSACTION \n CONSUMER:" + bestBuyer.cliendID + " PROVIDER: " + bestSeller.clientID + " UNITS: " + bestBuyer.units +
                    " UNITPRICE: " + bestSeller.unitPrice + " CONSUMERCOSTPERUNIT: " + match.consumerCost + " PROVIDERPROFITPERUNIT: " + match.providerProfit;
            buyOrders.remove(match.buyOrder);
            sellOrders.remove(match.sellOrder);
            return output;
        } else {
            return "NO MATCH FOUND CURRENTLY, INSERTING INTO SYSTEM";
        }
    }

    @Override
    public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
        return new byte[0];
    }
}
