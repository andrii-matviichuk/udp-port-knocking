import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class Server {
    private static List<DatagramSocket> sockets;
    private static List<MyClient> clients;
    private final static String ACK = "ACK";
    private static List<Integer> packetsUniqueNums;
    private static List<Integer> freePorts;

    public static synchronized void main(String[] args) {
        try {
            new Server(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Server(String[] args) {
        sockets = Collections.synchronizedList(new LinkedList<>());
        clients = Collections.synchronizedList(new ArrayList<>());
        packetsUniqueNums = Collections.synchronizedList(new LinkedList<>());
        freePorts = Collections.synchronizedList(new LinkedList<>());
        packetsUniqueNums.add(0);

        for (int i = 10000; i < 50000; i++) {
            freePorts.add(i);
        }


        for (int i = 0; i < args.length; i++) {
            if (Integer.parseInt(args[i]) < 1025) {
                log("Can`t use port < 1024");
                continue;
            }
            DatagramSocket serverSocket = null;
            try {
                serverSocket = new DatagramSocket(Integer.parseInt(args[i]));
                serverSocket.setSoTimeout(2000);
            } catch (BindException e) {
                continue;
            } catch (SocketException e) {
                e.printStackTrace();
            }

            sockets.add(serverSocket);
            freePorts.remove((Integer) serverSocket.getLocalPort());

            log("Server on port: " + serverSocket.getLocalPort() + " was created");

            int finalI = i;
            new Thread(() -> {
                log("Server listens on port: " + sockets.get(finalI).getLocalPort());
                while (true) {
                    DatagramPacket receivePacket = new DatagramPacket(new byte[4096], 4096);
                    try {
                        sockets.get(finalI).receive(receivePacket);
                    } catch (SocketTimeoutException e) {
                        continue;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    new Thread(() -> {
                        InetAddress clientAddress = receivePacket.getAddress();
                        int clientPort = receivePacket.getPort();
                        byte[] clientData = receivePacket.getData();

                        String message = new String(Arrays.copyOfRange(clientData, 8, clientData.length)).trim();

                        log("To server on port: " + sockets.get(finalI).getLocalPort() + " , was send message: \"" + message + "\" by : " + clientAddress.toString().split("/")[1] + ":" + clientPort);
                        boolean clientExists = false;
                        boolean correctOrder = false;
                        MyClient myClient = null;


                        synchronized (Server.class) {
                            for (MyClient client : clients) {
                                if (client.getAddress().equals(clientAddress.toString())
                                        && client.getPort() == clientPort) {

                                    client.sendData(sockets.get(finalI).getLocalPort());
                                    clientExists = true;

                                    int counter = 0;
                                    if (client.getDataSendingOrder().size() == sockets.size()) {
                                        for (int p = 0; p < client.getDataSendingOrder().size(); p++) {
                                            if (sockets.get(p).getLocalPort() == client.getDataSendingOrder().get(p)) {
                                                counter++;
                                            }
                                        }
                                        if (counter == client.getDataSendingOrder().size()) {
                                            correctOrder = true;
                                        } else {
                                            log("\tThe client " + clientAddress.toString().split("/")[1] + ":" + client.getPort() + " has sent packets in incorrect order\n" +
                                                    "\t**** The file would not be sent!");
                                            clients.remove(client);
                                            return;
                                        }
                                    }
                                    myClient = client;
                                    break;
                                }
                            }
                        }
                        log("Server on port : " + sockets.get(finalI).getLocalPort() + " is sending answer...");
                        if (correctOrder) {
                            String ackFileStr = "ACKFILE" + ByteBuffer.wrap(Arrays.copyOfRange(clientData, 0, 8)).getInt();
                            byte[] ACKfileData = ackFileStr.getBytes();
                            DatagramPacket sendPacket =
                                    new DatagramPacket(ACKfileData, ACKfileData.length, clientAddress, clientPort);
                            try {
                                sockets.get(finalI).send(sendPacket);
                                log("The answer was sent by server on port: " + sockets.get(finalI).getLocalPort());
                            } catch (IOException e) {
                                log("Server is unable to sent answer to client! Reason: " + e.getMessage());
                            }

                            log("\tSending file to: " + clientAddress.toString().split("/")[1] + ":" + clientPort);
                            File videoFile = new File("C:\\skj2019dzienne\\film.mpg");
                            try {
                                int freePort = freePorts.get(0);
                                freePorts.remove(0);

                                DatagramSocket serverSocketForVideo = new DatagramSocket(freePort, InetAddress.getByName("localhost"));
                                serverSocketForVideo.setSoTimeout(3000);

                                log("\tSocket for sending file was created. Sending port to client");
                                String serverPort = String.valueOf(freePort);

                                byte[] uniqueNum = ByteBuffer.allocate(8).putInt(packetsUniqueNums.get(packetsUniqueNums.size() - 1)).array();
                                packetsUniqueNums.add(packetsUniqueNums.get(packetsUniqueNums.size() - 1) + 1);
                                byte[] sendData = new byte[4096];
                                System.arraycopy(uniqueNum, 0, sendData, 0, 8);
                                System.arraycopy(serverPort.getBytes(), 0, sendData, 8, serverPort.getBytes().length);

                                DatagramPacket packetToSend = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
                                DatagramPacket packetToReceive = new DatagramPacket(new byte[4096], 4096);
                                try {
                                    int counter = 0;
                                    while (counter < 3) {
                                        serverSocketForVideo.send(packetToSend);
                                        serverSocketForVideo.receive(packetToReceive);
                                        String uniqueNumAnswer = new String(packetToReceive.getData()).trim();
                                        if (uniqueNumAnswer.equals("ACKFILE" + ByteBuffer.wrap(uniqueNum).getInt())) {
                                            break;
                                        }
                                        counter++;
                                    }
                                } catch (IOException e) {
                                    log("Socket is not able to sent UDP Packet! Reason: " + e.getMessage());
                                }


                                log("\tSending file name to client!");

                                String fileName = videoFile.getName();
                                sendUDPPacket(fileName.getBytes(), serverSocketForVideo, clientAddress, clientPort);

                                log("\tSending file size to client!");

                                String fileSize = String.valueOf(videoFile.length());
                                sendUDPPacket(fileSize.getBytes(), serverSocketForVideo, clientAddress, clientPort);

                                log("\tSending file to client!");

                                FileInputStream fin = new FileInputStream(videoFile);

                                for (int k = 0; k < Math.ceil(videoFile.length() / 4096.0); k++) {
                                    byte[] fileData = new byte[4088];
                                    if (fin.available() != 0) {
                                        fin.read(fileData);
                                    }
                                    if (sendUDPPacket(fileData, serverSocketForVideo, clientAddress, clientPort) == -1) {
                                        log("\tSomething went wrong while sending file data!");
                                        break;
                                    }
                                }
                                fin.close();
                                serverSocketForVideo.close();
                                log("\tThe file was sent to: " + clientAddress.toString().split("/")[1] + ":" + clientPort);
                            } catch (Exception e) {
                                log("\tSomething went wrong while sending file! Reason: " + e.getMessage());
                            }
                            clients.remove(myClient);
                        } else {
                            String ACKAnswer = ACK + ByteBuffer.wrap(Arrays.copyOfRange(clientData, 0, 8)).getInt();
                            byte[] ACKData = ACKAnswer.getBytes();
                            DatagramPacket sendPacket =
                                    new DatagramPacket(ACKData, ACKData.length, clientAddress, clientPort);
                            try {
                                sockets.get(finalI).send(sendPacket);
                                log("The answer was sent by server on port: " + sockets.get(finalI).getLocalPort());
                            } catch (IOException e) {
                                log("Server is unable to sent answer to client! Reason: " + e.getMessage());
                            }
                        }

                        if (!clientExists) {
                            MyClient client = new MyClient(clientAddress.toString(), clientPort);
                            clients.add(client);
                            client.sendData(sockets.get(finalI).getLocalPort());
                        }
                    }).start();
                }
            }).start();
        }
    }

    private synchronized static int sendUDPPacket(byte[] data, DatagramSocket sender, InetAddress receiverAddress, int receiverPort) {
        if (data.length > 4088) {
            log("Can`t send datagram packet. It`s too long!");
            return -1;
        }
        byte[] uniqueNum = ByteBuffer.allocate(8).putInt(packetsUniqueNums.get(packetsUniqueNums.size() - 1)).array();
        packetsUniqueNums.add(packetsUniqueNums.get(packetsUniqueNums.size() - 1) + 1);
        byte[] sendData = new byte[4096];
        System.arraycopy(uniqueNum, 0, sendData, 0, 8);
        System.arraycopy(data, 0, sendData, 8, data.length);

        DatagramPacket packetToSend = new DatagramPacket(sendData, sendData.length, receiverAddress, receiverPort);
        DatagramPacket packetToReceive = new DatagramPacket(new byte[4096], 4096, receiverAddress, receiverPort);
        try {
            int counter = 0;
            while (counter < 3) {
                sender.send(packetToSend);
                sender.receive(packetToReceive);
                String uniqueNumAnswer = new String(packetToReceive.getData()).trim();
                if (uniqueNumAnswer.equals("ACKFILE" + ByteBuffer.wrap(uniqueNum).getInt())) {
                    return 1;
                }
                counter++;
            }
            log("No confirmation was received from recipient!");

        } catch (IOException e) {
            log("Socket is not able to sent UDP Packet! Reason: " + e.getMessage());
        }
        return -1;
    }


    private static void log(String message) {
        System.out.println("[S]: " + message);
    }


}


