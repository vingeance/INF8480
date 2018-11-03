package shared;

import java.io.Serializable;

public class OperationServerSharedInfo implements Serializable {
    private String ipAddress;
    private String port;
    private int capacity;

    public OperationServerSharedInfo(String ipAddress, String port, int capacity)
    {
        this.ipAddress = ipAddress;
        this.port = port;
        this.capacity = capacity;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getPort() { return  port; }

    public int getCapacity() {
        return capacity;
    }
}
