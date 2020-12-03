import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;


public class MyClient {
    private int port;
    private String adress;
    private LinkedList<Integer> dataSendingOrder;

    MyClient(String address, int port) {
        this.port = port;
        this.adress = address;
        dataSendingOrder = new LinkedList<>();
    }

    void sendData(int onPort) {
        dataSendingOrder.add(onPort);
    }

    int getPort() {
        return port;
    }

    String getAddress() {
        return adress;
    }

    LinkedList<Integer> getDataSendingOrder() {
        return dataSendingOrder;
    }

    @Override
    public String toString() {
        return "MyClient{" +
                "port=" + port +
                ", adress='" + adress + '\'' +
                ", dataSendingOrder=" + dataSendingOrder +
                '}';
    }
}

