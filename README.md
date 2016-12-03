<h1><b>Group Messenger with Total and FIFO Ordering Guarantees</b></h1>

<ul>
<li>This project is an implementation of Ordered Multicast providing Total and FIFO guarantees. The grading program ensures that there will be a failure of an app instance in the middle of the execution. </li>
<li>This project can be executed by deploying this app on 5 (Android Virtual Devices)AVDs.</li>
<li>The app multicasts every user-entered message to all app instances (including the one that is sending the message)</li>
<li>Each message is stored as a <key, value> pair where the key is the final delivery sequence number for the message (as a string) and the value is the actual message (again, as a string).</li>
</ul>
