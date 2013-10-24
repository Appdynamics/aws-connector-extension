package com.appdynamics.connectors;

import static com.singularity.ee.connectors.entity.api.MachineState.STARTED;
import static com.singularity.ee.connectors.entity.api.MachineState.STARTING;
import static com.singularity.ee.connectors.entity.api.MachineState.STOPPED;
import static com.singularity.ee.connectors.entity.api.MachineState.STOPPING;
import static com.singularity.ee.controller.KAppServerConstants.CONTROLLER_SERVICES_HOST_NAME_PROPERTY_KEY;
import static com.singularity.ee.controller.KAppServerConstants.CONTROLLER_SERVICES_PORT_PROPERTY_KEY;
import static com.singularity.ee.controller.KAppServerConstants.DEFAULT_CONTROLLER_PORT_VALUE;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

import com.singularity.ee.agent.resolver.AgentResolutionEncoder;
import com.singularity.ee.connectors.api.ConnectorException;
import com.singularity.ee.connectors.api.IConnector;
import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.api.InvalidObjectException;
import com.singularity.ee.connectors.entity.api.IAccount;
import com.singularity.ee.connectors.entity.api.IComputeCenter;
import com.singularity.ee.connectors.entity.api.IImage;
import com.singularity.ee.connectors.entity.api.IImageStore;
import com.singularity.ee.connectors.entity.api.IMachine;
import com.singularity.ee.connectors.entity.api.IMachineDescriptor;
import com.singularity.ee.connectors.entity.api.IProperty;
import com.singularity.ee.connectors.entity.api.MachineState;
import com.xerox.amazonws.ec2.EC2Exception;
import com.xerox.amazonws.ec2.GroupDescription;
import com.xerox.amazonws.ec2.GroupDescription.IpPermission;
import com.xerox.amazonws.ec2.InstanceType;
import com.xerox.amazonws.ec2.Jec2;
import com.xerox.amazonws.ec2.RegionInfo;
import com.xerox.amazonws.ec2.ReservationDescription;
import com.xerox.amazonws.ec2.ReservationDescription.Instance;

public class AWSConnector implements IConnector
{
	private static Logger logger = Logger.getLogger(AWSConnector.class.getName());
	
	private static final Map<String, String> regionVsURLs;
	
	static
	{
	    Map<String, String> tmpRegionVsURLs = new HashMap<String, String>();	    
	    
	    tmpRegionVsURLs.put("ap-southeast-1", "ec2.ap-southeast-1.amazonaws.com");
	    tmpRegionVsURLs.put("eu-west-1", "ec2.eu-west-1.amazonaws.com");
	    tmpRegionVsURLs.put("us-east-1", "ec2.us-east-1.amazonaws.com");
	    tmpRegionVsURLs.put("us-west-1", "ec2.us-west-1.amazonaws.com");
	    
	    regionVsURLs = Collections.unmodifiableMap(tmpRegionVsURLs);
	}
	
	private IControllerServices controllerServices;
	
	/**
	 * Public no-arg constructor is required by the connector framework.
	 */
	public AWSConnector() {}

	public void setControllerServices(IControllerServices controllerServices)
	{
		this.controllerServices = controllerServices;
	}
	
	public int getAgentPort()
	{
		return controllerServices.getDefaultAgentPort();
	}	

	public IMachine createMachine(IComputeCenter computeCenter, IImage image, IMachineDescriptor machineDescriptor)
			throws InvalidObjectException, ConnectorException
	{
		boolean succeeded = false;
		Exception createFailureRootCause = null;
		Instance instance = null;

		try
		{
			IProperty[] macProps = machineDescriptor.getProperties();
			
			Jec2 connector = getConnector(image, computeCenter, controllerServices);
			String imageName = Utils.getAMIName(image.getProperties(), controllerServices);
			List<String> securityGroups = getSecurityGroup(macProps, connector);
			validateAndConfigureSecurityGroups(securityGroups, connector);
			
			controllerServices.getStringPropertyByName(macProps, Utils.SECURITY_GROUP).
								setValue(getSecurityGroupsAsString(securityGroups));

			String keyPair = Utils.getKeyPair(macProps, controllerServices);
			
			InstanceType instanceType = getInstanceType(macProps);

			String zone = Utils.getZone(macProps, controllerServices);
			String kernel = Utils.getKernel(macProps, controllerServices);
			String ramdisk = Utils.getRamDisk(macProps, controllerServices);

			String controllerHost = System.getProperty(CONTROLLER_SERVICES_HOST_NAME_PROPERTY_KEY, 
													  InetAddress.getLocalHost().getHostName());
			
			int controllerPort = Integer.getInteger(CONTROLLER_SERVICES_PORT_PROPERTY_KEY, 
													DEFAULT_CONTROLLER_PORT_VALUE);
			
			IAccount account = computeCenter.getAccount();
			
			String accountName = account.getName();
			String accountAccessKey = account.getAccessKey();
			
			AgentResolutionEncoder agentResolutionEncoder = 
				new AgentResolutionEncoder(controllerHost, controllerPort, 
										   accountName, accountAccessKey);
			
			String userData = agentResolutionEncoder.encodeAgentResolutionInfo();
			
			logger.info("Starting EC2 machine of Image :" + imageName + " security :" + securityGroups + " keypair :"
					+ keyPair + " instance :" + instanceType + " zone :" + zone + " kernel :" + kernel + " ramdisk :"
					+ ramdisk + " userData :"+userData);

			List<Instance> instances = 
				connector.runInstances(imageName, 1, 1, securityGroups, 
									   userData, keyPair, true, instanceType, 
									   zone, kernel, ramdisk, null).getInstances();

			if (instances.size() == 0)
				throw new ConnectorException("Cannot create instance for image :" + image.getName());

			instance = instances.get(0);
			
			logger.info("EC2 machine started; id:"+instance.getInstanceId());
						
			IMachine machine;
			
			if (StringUtils.isBlank(instance.getDnsName()))
			{
				machine = 
					controllerServices.createMachineInstance(instance.getInstanceId(), 
														     agentResolutionEncoder.getUniqueHostIdentifier(), 
														     computeCenter, 
														     machineDescriptor, 
														     image, getAgentPort());
			}
			else
			{
				machine = 
					controllerServices.createMachineInstance(instance.getInstanceId(), 
															 agentResolutionEncoder.getUniqueHostIdentifier(), 
														     instance.getDnsName(), 
														     computeCenter, 
														     machineDescriptor, 
														     image, getAgentPort());
			}

			if (kernel == null)
			{
				controllerServices.getStringPropertyByName(macProps, Utils.KERNEL).
										setValue(instance.getKernelId());				
			}

			if (zone == null)
			{
				controllerServices.getStringPropertyByName(macProps, Utils.ZONE).
										setValue(instance.getAvailabilityZone());
			}

			controllerServices.getStringPropertyByName(macProps, Utils.INSTANCE_TYPE).
										setValue(instance.getInstanceType().getTypeId());

			succeeded = true;
			
			return machine;

		}
		catch (InvalidObjectException e)
		{
			createFailureRootCause = e;
			throw e;
		}
		catch (ConnectorException e)
		{
			createFailureRootCause = e;
			throw e;
		}
		catch (Exception e)
		{
			createFailureRootCause = e;
			throw new ConnectorException(e.getMessage(), e);
		}
		finally
		{
			// We have to make sure to terminate any orphan EC2 instances if 
			// the machine create fails.
			if (!succeeded && instance != null)
			{
				try
				{
					ConnectorLocator.getInstance().getConnector(computeCenter, controllerServices).
												terminateInstances(new String[] { instance.getInstanceId() });
				}
				catch (Exception e)
				{
					throw new ConnectorException(
							"Machine create failed, but terminate failed as well! " +
							"We have an orphan EC2 instance with id: "+instance.getInstanceId()+
							" that must be shut down manually. Root cause for machine " +
							"create failure is following: ", createFailureRootCause);
				}				
			}
		}
	}

	public void refreshMachineState(IMachine machine) throws InvalidObjectException, ConnectorException
	{
		Jec2 connector = getConnector(machine.getImage(), machine.getComputeCenter(), controllerServices);

		Instance ec2Instance = getEc2Instance(machine, connector);

		MachineState currentState = machine.getState();

		if (ec2Instance == null)
		{
			// machine not found. MUST have been terminated
			if (currentState != STOPPED)
				machine.setState(STOPPED);
		}
		else
		{
			MachineState newState = getMachineState(ec2Instance);

			if (newState == STARTED)
			{
				// IP may only be set when the machine is running
				String elasticIp = 
					Utils.getElasticIP(machine.getMachineDescriptor().getProperties(), controllerServices);

				elasticIp = elasticIp == null ? "" : elasticIp.trim();
				
				if (elasticIp.length() > 0)
				{
					// associate the address
					setElasticIp(machine, elasticIp, connector);
				}
				else
				{
					machine.setIpAddress(ec2Instance.getDnsName());
				}
			}
			
			if (newState != currentState)
			{
				machine.setState(newState);
			}
		}
	}

	public void restartMachine(IMachine machine) throws InvalidObjectException, ConnectorException
	{
		try
		{
			getConnector(machine.getImage(), machine.getComputeCenter(), controllerServices).
										rebootInstances(new String[] { machine.getName() });
		}
		catch (Exception e)
		{
			throw new ConnectorException("Machine restart failed: "+machine.getName(), e);
		}
	}

	public void terminateMachine(IMachine machine) throws InvalidObjectException, ConnectorException
	{
	    Jec2 connector = null;
	    
		try
		{
		    connector = getConnector(machine.getImage(), machine.getComputeCenter(), controllerServices);

            // First check if the instance still exists		    
		    Instance ec2Instance = getEc2Instance(machine, connector);
		    
		    if (ec2Instance == null)
		    {    
		        // The instance doesn't exist so no need to terminate
		        return;
		    }
		    else
		    {    
	            connector.terminateInstances(new String[] { machine.getName() });		        
		    }
		}
		catch (Exception e)
		{
		    if (connector != null)
		    {
		        // Check again if the instance exists - we may get here because 
		        // of a race condition in terminating the instance manually and 
		        // terminating it here through the connector.
		        Instance ec2Instance = getEc2Instance(machine, connector);
  
		        if (ec2Instance == null)
		        {    
		            // Terminate failed but the machine doesnt exist so we are ok.
		            return;
		        }
		    }
		    
			throw new ConnectorException("Machine terminate failed: "+machine.getName(), e);
		}
	}	
	
	public void deleteImage(IImage image) throws InvalidObjectException, ConnectorException
	{
		String imageAMI = Utils.getAMIName(image.getProperties(), controllerServices);
		
		try
		{
			getConnector(image, image.getImageStore().getProperties(), controllerServices)
				.deregisterImage(imageAMI);
		}
		catch (EC2Exception e)
		{
			logger.log(Level.WARNING, "", e);

			throw new InvalidObjectException("Failed to delete the image. Cause " + 
					e.getMessage(), e);
		}
	}	
	
	public void refreshImageState(IImage image) throws InvalidObjectException, ConnectorException
	{
		// TODO Implement: See CORE-1081
		
	}	
		
	public void configure(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException
	{
	}

	public void configure(IImageStore imageStore) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	public void configure(IImage image) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	public void unconfigure(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException
	{
	}

	public void unconfigure(IImageStore imageStore) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	public void unconfigure(IImage image) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	public void validate(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException
	{
		validate(computeCenter.getProperties());
	}

	public void validate(IImageStore imageStore) throws InvalidObjectException, ConnectorException
	{
		validate(imageStore.getProperties());
	}

	public void validate(IImage image) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}
	
	private List<String> getSecurityGroup(IProperty[] macProps, Jec2 connector)
	{
		String propertyValue = Utils.getSecurityGroup(macProps, controllerServices);

		List<String> securityGroups;

		if (propertyValue == null)
		{
			securityGroups = new ArrayList<String>(1);
			securityGroups.add(Utils.DEFAULT_SECURITY_GROUP);
		}
		else
		{
			// security group names are separated by ,
			securityGroups = Arrays.asList(propertyValue.split(","));
		}

		return securityGroups;
	}
	
	private String getSecurityGroupsAsString(List<String> securityGroups)
	{
		StringBuilder buffer = new StringBuilder();

		for (String group : securityGroups)
		{
			if (buffer.length() != 0)
				buffer.append(",");

			buffer.append(group);
		}

		return buffer.toString();
	}
	
	private void validateAndConfigureSecurityGroups(List<String> securityGroups, Jec2 connector) throws EC2Exception, ConnectorException
	{
		List<GroupDescription> describeSecurityGroups = connector.describeSecurityGroups(securityGroups);

		String controllerIp = "0.0.0.0/0";
		int agentPort = controllerServices.getDefaultAgentPort();

		// check if any one of the security group
		// already has agent port and controller ip
		for (GroupDescription securityGroup : describeSecurityGroups)
		{
			for (IpPermission permission : securityGroup.getPermissions())
			{
				if (permission.getIpRanges().contains(controllerIp)
						&& (agentPort >= permission.getFromPort() && 
								agentPort <= permission.getToPort()))
				{
					return;
				}
			}
		}

		String securityGroup = null;

		if (securityGroups.contains(Utils.DEFAULT_SECURITY_GROUP))
		{
			securityGroup = Utils.DEFAULT_SECURITY_GROUP;			
		}
		else
		{
			securityGroup = securityGroups.get(0);			
		}

		connector.authorizeSecurityGroupIngress(securityGroup, "tcp", agentPort, agentPort, controllerIp);
	}	
	
	private InstanceType getInstanceType(IProperty[] macProps)
	{
		String instanceType = Utils.getInstanceType(macProps, controllerServices);

		// MUST be m1.small, m1.large, m1.xlarge, c1.medium, and c1.xlarge.
		if (instanceType == null)
			return InstanceType.DEFAULT;

		if (instanceType.endsWith("m1.small"))
			return InstanceType.DEFAULT;

		if (instanceType.endsWith("m1.large"))
			return InstanceType.LARGE;

		if (instanceType.endsWith("m1.xlarge"))
			return InstanceType.XLARGE;

		if (instanceType.endsWith("c1.medium"))
			return InstanceType.MEDIUM_HCPU;

		if (instanceType.endsWith("c1.xlarge"))
			return InstanceType.XLARGE_HCPU;

		return InstanceType.DEFAULT;
	}
	
	private Instance getEc2Instance(IMachine machine, Jec2 connector) throws ConnectorException
	{
		List<ReservationDescription> reservationDescriptions = null;
		try
		{
			reservationDescriptions = connector.describeInstances(new String[] { machine.getName() });

			if (reservationDescriptions.size() == 0)
			{
				// machine not found MUST have been terminated
				return null;
			}

			// always it will be in the first reservation as the query is on
			// only one machine
			final List<Instance> instances = reservationDescriptions.get(0).getInstances();
			for (Instance instance : instances)
			{
				if (instance.getInstanceId().equals(machine.getName()))
				{
					// found the machine
					return instance;
				}
			}
		}
		catch (Exception e)
		{
			throw new ConnectorException(e);
		}

		return null;
	}	
	
	private MachineState getMachineState(Instance instance)
	{
		String stateCode = instance.getState();
		
		if(stateCode.equals("running"))
		{
			return STARTED;
		}
		else if(stateCode.equals("pending"))
		{
			return STARTING;
		}
		else if(stateCode.equals("shutting-down"))
		{
			return STOPPING;
		}
		else if(stateCode.equals("terminated"))
		{
			return STOPPED;
		}
		else if(stateCode.equals("stopped"))
		{
			return STARTED; // we dont need to create a new state as workflow dont support this
		}
		else
		{
			throw new IllegalStateException("State " + stateCode + " is not known");
		}
	}
	
	private void setElasticIp(IMachine machine, String elasticIp, Jec2 connector)
		throws ConnectorException
	{
		try
		{
			connector.associateAddress(machine.getName(), elasticIp);

			machine.setIpAddress(elasticIp);
		}
		catch (EC2Exception e)
		{
			throw new ConnectorException(e);
		}
	}
	
	private void validate(IProperty[] properties) throws InvalidObjectException
	{
		Jec2 connector = ConnectorLocator.getInstance().getConnector(properties, controllerServices);

		// this will validate the access and secret keys
		try
		{
			connector.describeRegions(Collections.<String> emptyList());
		}
		catch (EC2Exception e)
		{
			logger.log(Level.INFO, "", e);

			throw new InvalidObjectException("The specified " + Utils.ACCESS_KEY_PROP + 
					" and/or " + Utils.SECRET_KEY_PROP + " is not valid.", e);
		}
	}
	
	private Jec2 getConnector(IImage image, IComputeCenter computeCenter, IControllerServices controllerServices)
	{
		IProperty[] properties = computeCenter.getProperties();

		return getConnector(image, properties, controllerServices);
	}

	private Jec2 getConnector(IImage image, IProperty[] properties, IControllerServices controllerServices)
	{
		Jec2 connector = ConnectorLocator.getInstance().getConnector(properties, controllerServices);
		
		if(image == null)
		{
			return connector;
		}
		
		String region = Utils.getRegion(image.getProperties(), controllerServices);
		
		if(!StringUtils.isBlank(region))
		{
			region = region.toLowerCase();
			
			connector.setRegion(new RegionInfo(region, regionVsURLs.get(region)));
		}
		
		return connector;
	}

}
