package loadbalancer;

import shared.OperationServerSharedInfo;

import java.util.ArrayList;

public class OperationTaskResult {
    private ArrayList<String> operations;
    private int result;
    //private OperationServerSharedInfo serverInfo; // server who calculated the result

    OperationTaskResult(ArrayList<String> operations, int result/*, OperationServerSharedInfo serverInfo*/)
    {
        this.operations = operations;
        this.result = result;
        //this.serverInfo = serverInfo;
    }

    public int getResult() {
        return result;
    }

    public ArrayList<String> getOperations() {
        return operations;
    }

    /*public OperationServerSharedInfo getServerInfo() {
        return serverInfo;
    }*/
}
