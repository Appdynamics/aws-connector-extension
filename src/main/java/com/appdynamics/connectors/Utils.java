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

import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.entity.api.IImageStore;
import com.singularity.ee.connectors.entity.api.IProperty;

/**
 * Utilities that are shared across the EC2 connector.
 */
class Utils
{
	public static final String SECRET_KEY_PROP = "Secret Access Key";

	public static final String ACCESS_KEY_PROP = "Access Key";
	
	public static final String MANIFEST_PREFIX_PROP = "Manifest Prefix";	
	
	public static final String S3_BUCKET_PROP = "Amazon S3 Bucket";
	
	public static final String ACCOUNT_PROPERTY = "AWS Account ID";
	
	public static final String ZONE = "Availability Zone";

	public static final String SECURITY_GROUP = "Security Group";

	public static final String KEY_PAIR = "Key Pair";

	public static final String INSTANCE_TYPE = "Instance Type";

	public static final String KERNEL = "Kernel";

	public static final String RAMDISK = "Ram disk";

	public static final String ELASTIC_IP = "Elastic IP";
	
	public static final String X509_CERT = "X.509 Certificate";

	public static final String X509_PRIVATE_KEY = "X.509 Private Key";		
	
	public static final String IMAGE_AMI_ID_PROP = "AMI ID";

	public static final String TASK_EXEC_IMAGE_PROP = "Image";

	public static final String DEFAULT_SECURITY_GROUP = "default";

	public static final String IMAGE_MANIFEST_XML_SUFFIX = ".manifest.xml";	
	
	public static final String REGION_PROP = "Region";
	
	private Utils() {}

	public static String getRegion(IProperty[] properties, IControllerServices controllerServices)
	{
		return controllerServices.getStringPropertyValueByName(properties, REGION_PROP);
	}

	public static String getAccessKey(IProperty[] properties, IControllerServices controllerServices)
	{
		return controllerServices.getStringPropertyValueByName(properties, ACCESS_KEY_PROP);
	}
	
	public static String getSecretKey(IProperty[] properties, IControllerServices controllerServices)
	{
		return controllerServices.getStringPropertyValueByName(properties, SECRET_KEY_PROP);
	}
	
	public static String getManifestPrefix(IProperty[] properties, IControllerServices controllerServices)
	{
		return controllerServices.getStringPropertyValueByName(properties, MANIFEST_PREFIX_PROP);
	}
	
	public static String getBucket(IProperty[] properties, IControllerServices controllerServices)
	{
		return controllerServices.getStringPropertyValueByName(properties, S3_BUCKET_PROP);
	}
	
	public static String getAccountId(IProperty[] properties, IControllerServices controllerServices)
	{
		return controllerServices.getStringPropertyValueByName(properties, ACCOUNT_PROPERTY);
	}

	public static String getRamDisk(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, RAMDISK));
	}

	public static String getKernel(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, KERNEL));
	}

	public static String getZone(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, ZONE));
	}

	public static String getKeyPair(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, KEY_PAIR));
	}

	public static String getAMIName(IProperty[] properties, IControllerServices controllerServices)
	{
		return controllerServices.getStringPropertyValueByName(properties, IMAGE_AMI_ID_PROP);
	}	
	
	public static String getSecurityGroup(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, SECURITY_GROUP));
	}
	
	public static String getInstanceType(IProperty[] properties, IControllerServices controllerServices)
	{
		return controllerServices.getStringPropertyValueByName(properties, INSTANCE_TYPE);		
	}
	
	public static String getElasticIP(IProperty[] properties, IControllerServices controllerServices)
	{
		return controllerServices.getStringPropertyValueByName(properties, ELASTIC_IP);
	}
	
	/**
	 * image location is of the format 
	 * 
	 * /<bucket>/<manifestPrefix>.manifest.xml
	 */
	public static String getManifestPrefix(String imageLocation)
	{
		return imageLocation.substring(imageLocation.lastIndexOf('/') + 1,
				imageLocation.lastIndexOf(".manifest.xml"));
	}

	/**
	 * image location is of the format 
	 * 
	 * /<bucket>/<manifestPrefix>.manifest.xml
	 */	
	public static String getBucket(String imageLocation)
	{
		return imageLocation.substring(0, imageLocation.lastIndexOf('/'));
	}
	
	public static String getAWSAccountId(IImageStore imageStore, IControllerServices controllerServices)
	{
		return getAWSAccountId(imageStore.getProperties(), controllerServices);
	}

	public static String getAWSAccountId(IProperty[] properties, IControllerServices controllerServices)
	{
		return getAWSAccountId(getAccountId(properties, controllerServices).trim());
	}

	private static String getAWSAccountId(final String accountId)
	{
		final String[] split = accountId.split("-");

		if (split.length == 1)
			return split[0];

		StringBuilder buffer = new StringBuilder(accountId.length() - split.length + 1);

		for (String part : split)
		{
			buffer.append(part);
		}

		return buffer.toString();
	}	
	
	private static String getValue(String value)
	{
		return value == null || value.trim().length() == 0 ? null : value;
	}

}
