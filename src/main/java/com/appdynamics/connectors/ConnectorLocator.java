/**
 * Copyright 2013 AppDynamics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.appdynamics.connectors;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.entity.api.IComputeCenter;
import com.singularity.ee.connectors.entity.api.IImageStore;
import com.singularity.ee.connectors.entity.api.IProperty;
import java.util.HashMap;
import java.util.Map;

class ConnectorLocator
{
	private static final ConnectorLocator INSTANCE = new ConnectorLocator();
	
	private final Map<String, AmazonEC2> accessKeyVsEc2 = new HashMap<String, AmazonEC2>();
	
	private final Object connectorLock = new Object();
	
	/**
	 * Private constructor on singleton.
	 */
	private ConnectorLocator() {}
	
	public static ConnectorLocator getInstance()
	{
		return INSTANCE;
	}
	
	public AmazonEC2 getConnector(IComputeCenter computeCenter, IControllerServices controllerServices)
	{
		return getConnector(computeCenter.getProperties(), controllerServices);
	}

	public AmazonEC2 getConnector(IImageStore imageStore, IControllerServices controllerServices)
	{
		return getConnector(imageStore.getProperties(), controllerServices);
	}
	
	public AmazonEC2 getConnector(IProperty[] properties, IControllerServices controllerServices)
	{
		String accessKey = Utils.getAccessKey(properties, controllerServices);
		String secretKey = Utils.getSecretKey(properties, controllerServices);

        AWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);

		// TODO: FIX CORE-3805
		// for now return a new instance everytime.
		// We have lot of Multi tenancy and compute centers and image repositores
		// containing the same AWS account information issues
		if(true)
		{
			return new AmazonEC2Client(awsCredentials);
		}
		
		synchronized (connectorLock)
		{
            AmazonEC2 ec2 = accessKeyVsEc2.get(accessKey);
			if (ec2 != null)
				return ec2;
			ec2 = new AmazonEC2Client(awsCredentials);
			accessKeyVsEc2.put(accessKey, ec2);

			return ec2;			
		}
	}

	public static void main(String[] args)
	{
		long startTime = System.currentTimeMillis();
		
		Object obj = new Object();
		
		System.out.println("time taken " + (System.currentTimeMillis() - startTime));
	}
}
