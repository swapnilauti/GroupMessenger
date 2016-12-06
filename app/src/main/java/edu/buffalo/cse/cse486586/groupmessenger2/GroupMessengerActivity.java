package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
/**
 * Activity Class
 */
public class GroupMessengerActivity extends Activity {
    private Integer priorityCount = 0;
    private Integer mid = 0;
    private Integer keyid =0;
    private Uri mUri = null;
    private HashSet<Integer> remotePorts;
    private HashSet<Integer> waitingPorts = new HashSet<Integer>();
    private HashMap<String,HashSet<Integer>> waitingPortHM;
    private HashMap<Integer,Socket> socketHM;
    private boolean hmCreated = false;
    private HashMap<String,Integer> msgHM;
    private String myPort = null;
    private PriorityQueue<PQNode> msgQueue = new PriorityQueue<PQNode>();
    private TextView localTextView = null;

    private void buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        mUri= uriBuilder.build();
    }
    private void initHashSet(){
        waitingPortHM = new HashMap<String, HashSet<Integer>>();
        remotePorts = new HashSet<Integer>();
        msgHM = new HashMap<String,Integer>();
        remotePorts.add(11108);
        remotePorts.add(11112);
        remotePorts.add(11116);
        remotePorts.add(11120);
        remotePorts.add(11124);
    }
    private void initMyPort(){
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        Log.d("My Port", "Init my port " + myPort);

    }
    private void insertContentProvider(String message){
        ContentValues cv = new ContentValues();
        Log.d("insertContentProvider", "" + keyid + " " + message);
        cv.put("key", keyid++);
        cv.put("value", message);
        getContentResolver().insert(mUri, cv);
    }
    private String prepareOrigMsg(String msg){
        String uniqueId = ""+(++mid)+"-"+myPort;
        return ""+uniqueId+" "+msg;
    }
    private Integer getPriorityCount(){
        Integer currentPriority=0;
        synchronized (priorityCount){
            currentPriority=++priorityCount;
        }
        return currentPriority;
    }
    private void setPriorityCount(Integer i){
        synchronized (priorityCount){
            priorityCount=i;
        }
    }
    private void prepareProposedMsg(String uniqueId,Integer currentPriority){
        String proposedMsg= uniqueId+":"+currentPriority+":"+myPort;
        String senderID[] = uniqueId.split("-");
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, proposedMsg,"1",senderID[1]);
    }
    private String prepareAgreedMsg(String uniqueId,Integer maxPrioirity){
        Integer currentPriority=getPriorityCount();
        if(maxPrioirity>currentPriority){
            setPriorityCount(maxPrioirity);
        }
        else{
            maxPrioirity=currentPriority;
        }
        return uniqueId+":"+maxPrioirity+"."+myPort;
    }
    private void sendAgreedPriority(String uniqueId){
        Integer curPriority = getPriorityCount();
        Integer maxPrioirity = msgHM.get(uniqueId);
        msgHM.remove(uniqueId);
        if(maxPrioirity>curPriority)
            setPriorityCount(maxPrioirity);
        else
            maxPrioirity=curPriority;
        String msg = prepareAgreedMsg(uniqueId, maxPrioirity);
        String arr2[] = msg.split(":");
        String temp[] = arr2[0].split("-");
        changePQ(uniqueId, Double.parseDouble(arr2[1]));
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, "0", temp[1]);
    }

    private void createSockets()
    {
        Socket socket = null;
        socketHM = new HashMap<Integer, Socket>();
        for (Integer portNo : remotePorts) {
            try {
                socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), portNo);
                socketHM.put(portNo, socket);

            } catch (Exception e) {
                Log.e("createSockets", e.getMessage());
            }
        }
        hmCreated=true;
    }
    private void removeDeadMsgs(Integer deadPort){
        PriorityQueue<PQNode> newQueue = new PriorityQueue<PQNode>();
        synchronized (msgQueue) {
            for (PQNode p : msgQueue) {
                String id = p.getUniqueId();
                String arr[] = id.split("-");
                if (arr[1].equals(deadPort.toString())) {
                    msgHM.remove(id);
                    p.setPriority(100.0);
                    p.setDeliverable(true);
                }
                newQueue.offer(p);
            }
            msgQueue = newQueue;
        }
        queueUpdate();
    }
    private void changePQ(String id,double finalPriority){
        synchronized (msgQueue) {
            PriorityQueue<PQNode> newQueue = new PriorityQueue<PQNode>();
            for (PQNode p : msgQueue) {
                if (p.getUniqueId().equals(id)) {
                    if(!msgHM.isEmpty()&&msgHM.containsKey(id))
                         msgHM.remove(id);
                    p.setPriority(finalPriority);
                    p.setDeliverable(true);
                    newQueue.offer(p);
                } else {
                    newQueue.offer(p);
                }
            }
            msgQueue = newQueue;
        }
        queueUpdate();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initHashSet();
        initMyPort();
        buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
        setContentView(R.layout.activity_group_messenger);
        TextView tv = (TextView) findViewById(R.id.textView1);
        localTextView = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());
        findViewById(R.id.button1).setOnClickListener(new OnPTestClickListener(tv, getContentResolver()));
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText editText = (EditText) findViewById(R.id.editText1);
                Editable ed = editText.getText();
                if (ed != null) {
                    String msg1 = ed.toString() + "\n";
                    String msg=prepareOrigMsg(msg1);
                    String arr2[]=msg.split(" ");
                    String uniqueId = arr2[0];
                    waitingPorts = (HashSet<Integer>) remotePorts.clone();
                    waitingPorts.remove(Integer.parseInt(myPort));
                    waitingPortHM.put(uniqueId,waitingPorts);
                    Integer priority= getPriorityCount();
                    msgHM.put(uniqueId, priority);
                    PQNode node = new PQNode(uniqueId,priority,msg1);
                    msgQueue.offer(node);
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, "0", myPort.toString());
                    TextView localTextView = (TextView) findViewById(R.id.textView1);
                    localTextView.append("\t" + msg1);
                    editText.setText(""); // This is one way to reset the input box.
                }

            }

        });

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(10000);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e("onCreate", "Can't create a ServerSocket");
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    public SharedPreferences getSharedPreferences(String key) {
        return getSharedPreferences(key, 0);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        while (!msgQueue.isEmpty()) {
            Log.d("Server Task", "Inside QueryUpdate");
            PQNode temp = msgQueue.poll();
            insertContentProvider(temp.getMessage());
        }
    }

    protected class PQNode implements Comparable<PQNode>{
        boolean deliverable;
        String uniqueId;
        Double priority;
        String message;

        public PQNode(String uniqueId, double priority, String message) {
            this.uniqueId = uniqueId;
            this.priority = priority;
            this.message = message;
            deliverable = false;
        }

        public boolean isDeliverable() {
            return deliverable;
        }

        public void setDeliverable(boolean deliverable) {
            this.deliverable = deliverable;
        }

        public String getUniqueId() {
            return uniqueId;
        }

        public void setUniqueId(String uniqueId) {
            this.uniqueId = uniqueId;
        }

        public Double getPriority() {
            return priority;
        }

        public void setPriority(Double priority) {
            this.priority = priority;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public int compareTo(PQNode b){
            return this.priority.compareTo(b.priority);
        }

        @Override
        public boolean equals(Object o) {
            if(o instanceof PQNode){
                return ((PQNode) o).getUniqueId().equals(this.getUniqueId());
            }

            return super.equals(o);
        }
    }
    public void queueUpdate(){
        synchronized (msgQueue) {
            while (!msgQueue.isEmpty() && msgQueue.peek().isDeliverable()) {
                Log.d("Server Task", "Inside QueryUpdate");
                PQNode temp = msgQueue.poll();
                insertContentProvider(temp.getMessage());
            }
        }
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {


        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            BufferedReader br = null;
            InputStream is = null;
            Socket socket = null;
            try {
                while (true) {
                    socket = serverSocket.accept();
                    is = socket.getInputStream();
                    br = new BufferedReader(new InputStreamReader(is));
                    String temp = br.readLine();
                    if (temp!=null && temp != ""){
                        String arr[]=temp.split(" ");
                        if(arr.length>1){
                            String uniqueId = arr[0];
                            String message =  temp.substring(uniqueId.length() + 1);
                            Integer priority= getPriorityCount();
                            PQNode node = new PQNode(uniqueId,priority,message);
                            msgQueue.offer(node);
                            prepareProposedMsg(uniqueId,priority);
                        }
                        else{
                            String arr2[] = temp.split(":");
                            String uniqueId = arr2[0];
                            if(arr2.length==2) {                                                    //Agreed Priority
                                Log.d("Server Task", "Received agreed prioirty");
                                Double finalPriority = Double.parseDouble(arr2[1]);
                                changePQ(uniqueId,finalPriority);
                            }
                            else{                                                                       //Proposed Priority
                                Log.d("Server Task", "Processing Proposed Priority");
                                if(msgHM.containsKey(uniqueId)) {                                         // if msg belong to this avd
                                    Log.d("Server Task", "Proposed Priority Received at Sender");
                                    waitingPorts.remove(Integer.parseInt(arr2[2]));
                                    waitingPortHM.get(uniqueId).remove(Integer.parseInt(arr2[2]));
                                    Integer maxPriority = msgHM.get(uniqueId);
                                    Integer curPriority = Integer.parseInt(arr2[1]);
                                    if(maxPriority<curPriority)
                                        maxPriority=curPriority;
                                    msgHM.put(uniqueId, maxPriority);
                                    if(waitingPortHM.get(uniqueId).isEmpty()) {                                                        //If all avds have sent their Proposed Priority
                                        sendAgreedPriority(uniqueId);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            catch (Exception e){
                Log.d("Server Task", "waiting ports size = "+waitingPorts.size());
                if(waitingPorts.size()<=1) {
                    for (Iterator<Integer> it = waitingPorts.iterator(); it.hasNext(); ) {
                        Integer deadPort = it.next();
                        remotePorts.remove(deadPort);
                        for (Map.Entry<String, HashSet<Integer>> m : waitingPortHM.entrySet()) {
                            HashSet<Integer> temp = m.getValue();
                            String uniqueId = m.getKey();
                            if (!temp.isEmpty() && temp.contains(deadPort)) {
                                temp.remove(deadPort);
                            }
                            if (temp.isEmpty()) {
                                sendAgreedPriority(uniqueId);
                            }
                        }
                        removeDeadMsgs(deadPort);
                    }
                }
            }
            finally {
                try {
                    is.close();
                    br.close();
                    socket.close();
                }
                catch (Exception e){

                }

            }


            return null;

        }

        protected void onProgressUpdate(String... strings) {
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            Socket socket = null;
            BufferedWriter bw = null;
            OutputStreamWriter osw = null;
            OutputStream os = null;
            PrintWriter out = null;
            String proposedMsg = null;
            Integer maxPriority = 0;
            Integer curPort = 0;
            String arr[] = null;
            if(msgs[0]==null || msgs[0]=="")
                return null;
            String msgToSend = msgs[0];
            if(msgs[1].equals("0")) {
                    for (Iterator<Integer> portNo = remotePorts.iterator(); portNo.hasNext(); ) {
                        curPort = portNo.next();
                        if(curPort==Integer.parseInt(myPort)) {
                            continue;
                        }
                        try {
                            socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), curPort);
                            os = socket.getOutputStream();
                            osw = new OutputStreamWriter(os);
                            bw = new BufferedWriter(osw);
                            out = new PrintWriter(osw, true);
                            out.println(msgToSend);
                            out.flush();
                        }
                        catch (Exception e) {
                            Log.e("Client Task", "Exception in part 1");
                            waitingPorts.remove(curPort);
                            for(Map.Entry<String,HashSet<Integer>> m: waitingPortHM.entrySet()){
                                HashSet<Integer> temp = m.getValue();
                                String uniqueId = m.getKey();
                                if(!temp.isEmpty()&&temp.contains(curPort)){
                                    temp.remove(curPort);
                                }
                                if(temp.isEmpty()) {
                                    sendAgreedPriority(uniqueId);
                                }
                            }
                            portNo.remove();
                            waitingPorts.remove(curPort);
                            removeDeadMsgs(curPort);
                        }
                        finally{
                            try {
                                socket.close();
                                bw.close();
                                osw.close();
                                os.close();
                                out.close();
                            }
                            catch(Exception e){
                            }
                        }
                    }
            }
                else{
                    try {
                        socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(msgs[2]));
                        os = socket.getOutputStream();
                        osw = new OutputStreamWriter(os);
                        bw = new BufferedWriter(osw);
                        out = new PrintWriter(osw, true);
                        out.println(msgToSend);
                        out.flush();
                    }
                    catch (Exception e) {
                        Log.e("Client Task", "Exception in part 2");
                        Integer deadPort = Integer.parseInt(msgs[2]);
                        waitingPorts.remove(deadPort);
                        remotePorts.remove(deadPort);
                        for(Map.Entry<String,HashSet<Integer>> m: waitingPortHM.entrySet()){
                            HashSet<Integer> temp = m.getValue();
                            String uniqueId = m.getKey();
                            if(!temp.isEmpty()&&temp.contains(deadPort)){
                                temp.remove(deadPort);
                            }
                            if(temp.isEmpty()) {
                                sendAgreedPriority(uniqueId);
                            }
                        }
                        removeDeadMsgs(deadPort);
                    }
                    finally{
                        try {
                            socket.close();
                            bw.close();
                            osw.close();
                            os.close();
                            out.close();
                        }
                        catch(Exception e){
                        }
                    }

                }


            return null;
        }
    }

}