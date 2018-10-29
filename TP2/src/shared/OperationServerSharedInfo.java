package shared;

import java.io.Serializable;

public class OperationServerSharedInfo implements Serializable {
    private String ipAddress;
    private int capacity;

    public OperationServerSharedInfo(String ipAddress, int capacity)
    {
        this.ipAddress = ipAddress;
        this.capacity = capacity;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getCapacity() {
        return capacity;
    }
}
