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

import java.util.HashMap;
import java.util.Map;

import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.entity.api.IComputeCenter;
import com.singularity.ee.connectors.entity.api.IImageStore;
import com.singularity.ee.connectors.entity.api.IProperty;
import com.xerox.amazonws.ec2.Jec2;

class ConnectorLocator
{
	private static final ConnectorLocator INSTANCE = new ConnectorLocator();
	
	private final Map<String, Jec2> accessKeyVsEc2 = new HashMap<String, Jec2>();
	
	private final Object connectorLock = new Object();
	
	/**
	 * Private constructor on singleton.
	 */
	private ConnectorLocator() {}
	
	public static ConnectorLocator getInstance()
	{
		return INSTANCE;
	}
	
	public Jec2 getConnector(IComputeCenter computeCenter, IControllerServices controllerServices)
	{
		return getConnector(computeCenter.getProperties(), controllerServices);
	}

	public Jec2 getConnector(IImageStore imageStore, IControllerServices controllerServices)
	{
		return getConnector(imageStore.getProperties(), controllerServices);
	}
	
	public Jec2 getConnector(IProperty[] properties, IControllerServices controllerServices)
	{
		String accessKey = Utils.getAccessKey(properties, controllerServices);
		String secretKey = Utils.getSecretKey(properties, controllerServices);

		// TODO: FIX CORE-3805
		// for now return a new instance everytime.
		// We have lot of Multi tenancy and compute centers and image repositores
		// containing the same AWS account information issues
		if(true)
		{
			return new Jec2(accessKey, secretKey);
		}
		
		synchronized (connectorLock)
		{
			Jec2 ec2 = accessKeyVsEc2.get(accessKey);
			if (ec2 != null)
				return ec2;
			ec2 = new Jec2(accessKey, secretKey);
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
