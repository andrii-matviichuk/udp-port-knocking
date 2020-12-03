import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;

public class Client {
    private static List<Integer> packetsUniqueNums = Collections.synchronizedList(new LinkedList<>());

    public static void main(String[] args) {
        packetsUniqueNums.add(0);
        try {
            DatagramSocket clientSocket = new DatagramSocket();
            clientSocket.setSoTimeout(5000);
            log("Client socket with port: " + clientSocket.getLocalPort() + " was created");
            InetAddress IPAddress = InetAddress.getByName(args[0]);
            String sentence = "hello";

            byte[] sendData = sentence.getBytes();
            for (int k = 1; k < args.length; k++) {
                log("Sending packet...");
                int res = sendUDPPacket(sendData, clientSocket, IPAddress, Integer.parseInt(args[k]));
                if (res == 0) {
                    log("The packet was sent!");
                } else if (res == 1) {
                    log("The packet was sent!");

                    log("Receiving port...");

                    DatagramPacket receivePacket = new DatagramPacket(new byte[4096], 4096);
                    try {
                        clientSocket.receive(receivePacket);
                        byte[] uniqueNum = Arrays.copyOfRange(receivePacket.getData(), 0, 8);
                        String answer = "ACKFILE" + ByteBuffer.wrap(uniqueNum).getInt();
                        DatagramPacket packetToSend = new DatagramPacket(answer.getBytes(), answer.getBytes().length, receivePacket.getAddress(), receivePacket.getPort());
                        clientSocket.send(packetToSend);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    String strPort = new String(Arrays.copyOfRange(receivePacket.getData(), 8, receivePacket.getData().length)).trim();
                    int port;
                    try {
                        port = Integer.parseInt(strPort);
                    } catch (NumberFormatException e) {
                        log("Server send incorrect port: " + e.getMessage());
                        continue;
                    }

                    log("Success! Port received!");

                    log("Receiving file name...");

                    receivePacket = receiveUDPPacket(clientSocket, receivePacket.getAddress(), port);
                    String fileName = new String(Arrays.copyOfRange(receivePacket.getData(), 8, receivePacket.getData().length)).trim();

                    log("File name received!");

                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss");
                    Date date = new Date(System.currentTimeMillis());


                    if (receivePacket.getAddress().getHostName().equals("localhost") || 
                        receivePacket.getAddress().getHostName().equals("127.0.0.1")) {
                        fileName = "127_0_0_1_" + clientSocket.getLocalPort() + "_" +
                                formatter.format(date) + "_" + fileName;
                    } else {
                        fileName = InetAddress.getLocalHost().toString().split("/")[1].replaceAll("[.]", "_") + "_" + clientSocket.getLocalPort() + "_" +
                                formatter.format(date) + "_" + fileName;
                    }
                    log("Receiving file size...");

                    receivePacket = receiveUDPPacket(clientSocket, receivePacket.getAddress(), port);
                    int fileSize;
                    String fileSizeStr = new String(Arrays.copyOfRange(receivePacket.getData(), 8, receivePacket.getData().length)).trim();
                    try {
                        fileSize = Integer.parseInt(fileSizeStr);
                    } catch (NumberFormatException e) {
                        log("Server send incorrect file size: " + e.getMessage());
                        continue;
                    }

                    log("File size received!");

                    log("Receiving video");

                    File videoFile = new File("C:\\skj2019dzienne\\odebrane\\" + fileName);
                    if (!videoFile.createNewFile()) {
                        log("Unable to create file. Received data will not be written to file!");
                    }

                    TreeMap<Integer, byte[]> packetsMap = new TreeMap<>();

                    try (FileOutputStream fous = new FileOutputStream(videoFile)) {
                        for (int j = 0; j < Math.ceil(fileSize / 4096.0); j++) {
                            byte[] numOfPacket;
                            byte[] data;

                            receivePacket = new DatagramPacket(new byte[4096], 4096, receivePacket.getAddress(), port);

                            clientSocket.receive(receivePacket);

                            byte[] uniqueNum = Arrays.copyOfRange(receivePacket.getData(), 0, 8);

                            String answer = "ACKFILE" + ByteBuffer.wrap(uniqueNum).getInt();

                            DatagramPacket packetToSend = new DatagramPacket(answer.getBytes(), answer.getBytes().length, receivePacket.getAddress(), receivePacket.getPort());
                            clientSocket.send(packetToSend);

                            numOfPacket = Arrays.copyOfRange(receivePacket.getData(), 0, 8);
                            data = Arrays.copyOfRange(receivePacket.getData(), 8, 4096);

                            packetsMap.put(ByteBuffer.wrap(numOfPacket).getInt(), data);
                        }
                        for (byte[] arr : packetsMap.values()) {
                            fous.write(arr);
                        }
                        fous.flush();
                    } catch (IOException e) {
                        log("Unable to write received data in file");
                        e.printStackTrace();
                    }

                    log("Video received and was written in file!");
                }
            }
            clientSocket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static int sendUDPPacket(byte[] data, DatagramSocket sender, InetAddress receiverAddress,
                                                  int receiverPort) {
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
                } else if (uniqueNumAnswer.equals("ACK" + ByteBuffer.wrap(uniqueNum).getInt())) {
                    return 0;
                }
                counter++;
            }
            log("No confirmation was received from recipient!");

        } catch (IOException e) {
            log("Socket is not able to sent UDP Packet! Reason: " + e.getMessage());
        }
        return -1;
    }

    private static DatagramPacket receiveUDPPacket(DatagramSocket recipient,
                                                                InetAddress sender, int port) {
        DatagramPacket receivePacket = new DatagramPacket(new byte[4096], 4096, sender, port);
        try {
            recipient.receive(receivePacket);

            byte[] uniqueNum = Arrays.copyOfRange(receivePacket.getData(), 0, 8);

            String answer = "ACKFILE" + ByteBuffer.wrap(uniqueNum).getInt();

            DatagramPacket packetToSend = new DatagramPacket(answer.getBytes(), answer.getBytes().length, receivePacket.getAddress(), receivePacket.getPort());
            recipient.send(packetToSend);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return receivePacket;
    }

    private static void log(String message) {
        System.out.println("[C]: " + message);
    }
}
