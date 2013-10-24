aws-connector-extension
===========================

Amazon Web Services EC2 Connector Extension

##Use Case

##Directory Structure

<table><tbody>
<tr>
<th align="left"> File/Folder </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> lib </td>
<td class='confluenceTd'> Contains third-party project references </td>
</tr>
<tr>
<td class='confluenceTd'> src </td>
<td class='confluenceTd'> Contains source code to the aws connector extension </td>
</tr>
<tr>
<td class='confluenceTd'> dist </td>
<td class='confluenceTd'> Only obtained when using ant. Run 'ant build' to get binaries. Run 'ant package' to get the distributable .zip file </td>
</tr>
<tr>
<td class='confluenceTd'> build.xml </td>
<td class='confluenceTd'> Ant build script to package the project (required only if changing Java code) </td>
</tr>
</tbody>
</table>

##Installation

1. Clone the aws-connector-extension from GitHub
2. Run 'ant package' from the cloned aws-connector-extension directory
3. Download the file aws-connector.zip located in the 'dist' directory into \<controller install dir\>/lib/connectors
4. Unzip the downloaded file
5. Restart the Controller
6. Go to the controller dashboard on the browser. Under Setup->My Preferences->Advanced Features enable "Show Cloud Auto-Scaling features" if it is not enabled. 
7. On the controller dashboard click "Cloud Auto-Scaling" and configure the compute cloud and the image.

Click Compute Cloud->Register Compute Cloud. Refer to the image below

![alt tag](https://raw.github.com/Appdynamics/https://raw.github.com/Appdynamics/aws-connector-extension/master/Amazon%20Elastic%20Computing%20Cloud%20Fields.png?login=rvasanda&token=6b927599ef85c6498274b0bef89d546f)

Click Image->Register Image. Refer to the image below

![alt tag](https://raw.github.com/Appdynamics/aws-connector-extension/master/AMI.png?login=rvasanda&token=b8c95758939efd0f22fbcd23537669c5)

##Contributing

Always feel free to fork and contribute any changes directly via [GitHub](https://github.com/Appdynamics/aws-connector-extension).

##Community

Find out more in the [AppSphere] community.

##Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:ace-request@appdynamics.com).

