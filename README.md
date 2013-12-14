Amazon Web Services EC2 Connector Extension
===========================================


##Use Case

Elastically grow/shrink instances into cloud/virtualized environments. There are four use cases for the connector. 

First, if the Controller detects that the load on the machine instances hosting an application is too high, the aws-connector-extension may be used to automate creation of new virtual machines to host that application. The end goal is to reduce the load across the application by horizontally scaling up application machine instances.

Second, if the Controller detects that the load on the machine instances hosting an application is below some minimum threshold, the aws-connector-extension may be used to terminate virtual machines running that application. The end goal is to save power/usage costs without sacrificing application performance by horizontally scaling down application machine instances.

Third, if the Controller detects that a machine instance has terminated unexpectedly when the connector refreshes an application machine state, the aws-connector-extension may be used to create a replacement virtual machine to replace the terminated application machine instance. This is known as our failover feature.

Lastly, the aws-connector-extension may be used to stage migration of an application from a physical to virtual infrastructure. Or the aws-connector-extension may be used to add additional virtual capacity to an application to augment a preexisting physical infrastructure hosting the application. 


##Installation
<ol>

<li>Clone the aws-connector-extension from GitHub.
</li>
<li>
Run 'ant package' from the cloned aws-connector-extension directory.
</li>
<li>
Download the file aws-connector.zip located in the 'dist' directory into the &lt;controller install the dir&gt;/lib/connectors directory.
</li>
<li>
Unzip the downloaded file.
</li>
<li>Restart the Controller and then log into AppDynamics.
</li>
<li>In the upper right corner of the window, click <b>Setup -&gt; My Preferences</b>. In the Advanced Features section, enable Show Cloud Auto-Scaling features if it is not enabled. 
</li>
<li>In the left navigation pane, click Cloud Auto-Scaling to configure the compute cloud and the image.
</li>
<ul>
<li>Click <b>Compute Clouds -&gt; Register Compute Cloud</b>. Fill in the required information (shown in the image below) and then click Register Compute Cloud to save the information.
<p>
&nbsp; 
</p><img src = "https://raw.github.com/Appdynamics/aws-connector-extension/master/Amazon%20Elastic%20Computing%20Cloud%20Fields.png">
</li>
<p>
&nbsp; 
</p>
<li>Click <b>Images -&gt; Register Image</b>. Fill in the required information (shown in the image below) and then click Register Image to save the information.
<p>
&nbsp; 
</p>
<img src = "https://raw.github.com/Appdynamics/aws-connector-extension/master/AMI.png">

</li>
</ul>

</ol>




##Contributing

Always feel free to fork and contribute any changes directly here on GitHub.

##Community

Find out more in the [AppSphere](http://appsphere.appdynamics.com/t5/eXchange/Amazon-Web-Services-AWS-EC2-Cloud-Connector-Extension/idi-p/5431) community.

##Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:ace-request@appdynamics.com).
