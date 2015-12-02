/**
 * Copyright 2013 AppDynamics, Inc.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.appdynamics.connectors;

import static com.singularity.ee.connectors.entity.api.MachineState.STARTED;
import static com.singularity.ee.connectors.entity.api.MachineState.STARTING;
import static com.singularity.ee.connectors.entity.api.MachineState.STOPPED;
import static com.singularity.ee.connectors.entity.api.MachineState.STOPPING;
import static com.singularity.ee.controller.KAppServerConstants.CONTROLLER_SERVICES_HOST_NAME_PROPERTY_KEY;
import static com.singularity.ee.controller.KAppServerConstants.CONTROLLER_SERVICES_PORT_PROPERTY_KEY;
import static com.singularity.ee.controller.KAppServerConstants.DEFAULT_CONTROLLER_PORT_VALUE;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.AssociateAddressRequest;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DeregisterImageRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.RebootInstancesRequest;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.util.Base64;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AWSConnector implements IConnector {
    private static Logger logger = Logger.getLogger(AWSConnector.class.getName());

    private static final Map<String, String> regionVsURLs;

    static {
        Map<String, String> tmpRegionVsURLs = new HashMap<String, String>();

        tmpRegionVsURLs.put("ap-southeast-1", "ec2.ap-southeast-1.amazonaws.com");
        tmpRegionVsURLs.put("eu-west-1", "ec2.eu-west-1.amazonaws.com");
        tmpRegionVsURLs.put("us-east-1", "ec2.us-east-1.amazonaws.com");
        tmpRegionVsURLs.put("us-west-1", "ec2.us-west-1.amazonaws.com");
        tmpRegionVsURLs.put("us-west-2", "ec2.us-west-2.amazonaws.com");
        tmpRegionVsURLs.put("ap-southeast-2", "ec2.ap-southeast-2.amazonaws.com");
        tmpRegionVsURLs.put("ap-northeast-1", "ec2.ap-northeast-1.amazonaws.com");
        tmpRegionVsURLs.put("sa-east-1", "ec2.sa-east-1.amazonaws.com");

        regionVsURLs = Collections.unmodifiableMap(tmpRegionVsURLs);
    }

    private IControllerServices controllerServices;

    /**
     * Public no-arg constructor is required by the connector framework.
     */
    public AWSConnector() {
    }

    public void setControllerServices(IControllerServices controllerServices) {
        this.controllerServices = controllerServices;
    }

    public int getAgentPort() {
        return controllerServices.getDefaultAgentPort();
    }

    public IMachine createMachine(IComputeCenter computeCenter, IImage image, IMachineDescriptor machineDescriptor)
            throws InvalidObjectException, ConnectorException {
        boolean succeeded = false;
        Exception createFailureRootCause = null;
        Instance instance = null;

        try {
            IProperty[] macProps = machineDescriptor.getProperties();

            AmazonEC2 connector = getConnector(image, computeCenter, controllerServices);
            String amiName = Utils.getAMIName(image.getProperties(), controllerServices);

            List<String> securityGroupIds = getSecurityGroupIDs(macProps);


            List<String> securityGroups = getSecurityGroup(macProps);


            if (securityGroupIds != null && securityGroupIds.size() > 0) {
                validateAndConfigureSecurityGroups(securityGroupIds, connector, false);
            } else if (securityGroups != null && securityGroups.size() > 0) {
                validateAndConfigureSecurityGroups(securityGroups, connector, true);
            }


            String subnetID = Utils.getSubnetID(macProps, controllerServices);

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

            String instanceName = Utils.getInstanceName(macProps, controllerServices);

            logger.info("Starting EC2 machine of Image :" + amiName + " Name :" + instanceName + " security group:" + securityGroups + " security group IDs: " + securityGroupIds
                    + " Subnet Id: " + subnetID + " keypair :"
                    + keyPair + " instance :" + instanceType + " zone :" + zone + " kernel :" + kernel + " ramdisk :"
                    + ramdisk + " userData :" + userData);

            RunInstancesRequest runInstancesRequest = new RunInstancesRequest(amiName, 1, 1);

            if (securityGroupIds != null && securityGroupIds.size() > 0) {
                runInstancesRequest.withSecurityGroupIds(securityGroupIds);
                if (subnetID != null) {
                    runInstancesRequest.withSubnetId(subnetID);
                }
            } else if (securityGroups != null && securityGroups.size() > 0) {
                runInstancesRequest.withSecurityGroups(securityGroups);
            }
            runInstancesRequest.setUserData(Base64.encodeAsString(userData.getBytes()));
            runInstancesRequest.setKeyName(keyPair);
            runInstancesRequest.setInstanceType(instanceType);
            runInstancesRequest.setKernelId(kernel);
            runInstancesRequest.setRamdiskId(ramdisk);

            Reservation reservation = connector.runInstances(runInstancesRequest).getReservation();
            List<Instance> instances = reservation.getInstances();

            if (instances.size() == 0)
                throw new ConnectorException("Cannot create instance for image :" + image.getName());

            instance = instances.get(0);


            //Set name for the instance
            if (!Strings.isNullOrEmpty(instanceName)) {
                CreateTagsRequest createTagsRequest = new CreateTagsRequest();
                createTagsRequest.withResources(instance.getInstanceId()).withTags(new Tag("Name", instanceName));
                connector.createTags(createTagsRequest);
            }


            logger.info("EC2 machine started; id:" + instance.getInstanceId());

            IMachine machine;

            String dnsName = instance.getPublicDnsName();

            if (Strings.isNullOrEmpty(dnsName)) {
                dnsName = instance.getPrivateDnsName();
            }


            if (Strings.isNullOrEmpty(dnsName)) {
                machine =
                        controllerServices.createMachineInstance(instance.getInstanceId(),
                                agentResolutionEncoder.getUniqueHostIdentifier(),
                                computeCenter,
                                machineDescriptor,
                                image, getAgentPort());
            } else {
                machine =
                        controllerServices.createMachineInstance(instance.getInstanceId(),
                                agentResolutionEncoder.getUniqueHostIdentifier(),
                                dnsName,
                                computeCenter,
                                machineDescriptor,
                                image, getAgentPort());
            }

            if (kernel == null) {
                controllerServices.getStringPropertyByName(macProps, Utils.KERNEL).
                        setValue(instance.getKernelId());
            }

            if (zone == null) {
                DescribeAvailabilityZonesResult describeAvailabilityZonesResult = connector.describeAvailabilityZones();
                List<AvailabilityZone> availabilityZones = describeAvailabilityZonesResult.getAvailabilityZones();
                controllerServices.getStringPropertyByName(macProps, Utils.ZONE).
                        setValue(availabilityZones.get(0).getZoneName());
            }

            controllerServices.getStringPropertyByName(macProps, Utils.INSTANCE_TYPE).
                    setValue(instance.getInstanceType());

            succeeded = true;

            return machine;

        } catch (InvalidObjectException e) {
            createFailureRootCause = e;
            throw e;
        } catch (ConnectorException e) {
            createFailureRootCause = e;
            throw e;
        } catch (Exception e) {
            createFailureRootCause = e;
            throw new ConnectorException(e.getMessage(), e);
        } finally {
            // We have to make sure to terminate any orphan EC2 instances if 
            // the machine create fails.
            if (!succeeded && instance != null) {
                try {
                    ConnectorLocator.getInstance().getConnector(computeCenter, controllerServices).
                            terminateInstances(new TerminateInstancesRequest(Lists.newArrayList(instance.getInstanceId())));
                } catch (Exception e) {
                    throw new ConnectorException(
                            "Machine create failed, but terminate failed as well! " +
                                    "We have an orphan EC2 instance with id: " + instance.getInstanceId() +
                                    " that must be shut down manually. Root cause for machine " +
                                    "create failure is following: ", createFailureRootCause);
                }
            }
        }
    }

    public void refreshMachineState(IMachine machine) throws InvalidObjectException, ConnectorException {
        AmazonEC2 connector = getConnector(machine.getImage(), machine.getComputeCenter(), controllerServices);

        Instance ec2Instance = getEc2Instance(machine, connector);

        MachineState currentState = machine.getState();

        if (ec2Instance == null) {
            // machine not found. MUST have been terminated
            if (currentState != STOPPED)
                machine.setState(STOPPED);
        } else {
            MachineState newState = getMachineState(ec2Instance);

            if (newState == STARTED) {
                // IP may only be set when the machine is running
                String elasticIp =
                        Utils.getElasticIP(machine.getMachineDescriptor().getProperties(), controllerServices);

                elasticIp = elasticIp == null ? "" : elasticIp.trim();

                if (elasticIp.length() > 0) {
                    // associate the address
                    setElasticIp(machine, elasticIp, connector);
                } else {

                    String dnsName = ec2Instance.getPublicDnsName();

                    if (Strings.isNullOrEmpty(dnsName)) {
                        dnsName = ec2Instance.getPrivateDnsName();
                    }
                    machine.setIpAddress(dnsName);
                }
            }

            if (newState != currentState) {
                machine.setState(newState);
            }
        }
    }

    public void restartMachine(IMachine machine) throws InvalidObjectException, ConnectorException {
        try {
            getConnector(machine.getImage(), machine.getComputeCenter(), controllerServices).
                    rebootInstances(new RebootInstancesRequest(Lists.newArrayList(machine.getName())));
        } catch (Exception e) {
            throw new ConnectorException("Machine restart failed: " + machine.getName(), e);
        }
    }

    public void terminateMachine(IMachine machine) throws InvalidObjectException, ConnectorException {
        AmazonEC2 connector = null;

        try {
            connector = getConnector(machine.getImage(), machine.getComputeCenter(), controllerServices);

            // First check if the instance still exists		    
            Instance ec2Instance = getEc2Instance(machine, connector);

            if (ec2Instance == null) {
                // The instance doesn't exist so no need to terminate
                return;
            } else {
                connector.terminateInstances(new TerminateInstancesRequest(Lists.newArrayList(machine.getName())));
            }
        } catch (Exception e) {
            if (connector != null) {
                // Check again if the instance exists - we may get here because 
                // of a race condition in terminating the instance manually and 
                // terminating it here through the connector.
                Instance ec2Instance = getEc2Instance(machine, connector);

                if (ec2Instance == null) {
                    // Terminate failed but the machine doesnt exist so we are ok.
                    return;
                }
            }

            throw new ConnectorException("Machine terminate failed: " + machine.getName(), e);
        }
    }

    public void deleteImage(IImage image) throws InvalidObjectException, ConnectorException {
        String imageAMI = Utils.getAMIName(image.getProperties(), controllerServices);

        try {
            getConnector(image, image.getImageStore().getProperties(), controllerServices)
                    .deregisterImage(new DeregisterImageRequest(imageAMI));
        } catch (AmazonServiceException e) {
            logger.log(Level.WARNING, "", e);

            throw new InvalidObjectException("Failed to delete the image. Cause " +
                    e.getMessage(), e);
        } catch (AmazonClientException e) {
            logger.log(Level.WARNING, "", e);

            throw new InvalidObjectException("Failed to delete the image. Cause " +
                    e.getMessage(), e);
        }
    }

    public void refreshImageState(IImage image) throws InvalidObjectException, ConnectorException {
        // TODO Implement: See CORE-1081

    }

    public void configure(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException {
    }

    public void configure(IImageStore imageStore) throws InvalidObjectException, ConnectorException {
        // do nothing
    }

    public void configure(IImage image) throws InvalidObjectException, ConnectorException {
        // do nothing
    }

    public void unconfigure(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException {
    }

    public void unconfigure(IImageStore imageStore) throws InvalidObjectException, ConnectorException {
        // do nothing
    }

    public void unconfigure(IImage image) throws InvalidObjectException, ConnectorException {
        // do nothing
    }

    public void validate(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException {
        validate(computeCenter.getProperties());
    }

    public void validate(IImageStore imageStore) throws InvalidObjectException, ConnectorException {
        validate(imageStore.getProperties());
    }

    public void validate(IImage image) throws InvalidObjectException, ConnectorException {
        // do nothing
    }

    private List<String> getSecurityGroup(IProperty[] macProps) {
        String propertyValue = Utils.getSecurityGroup(macProps, controllerServices);

        List<String> securityGroups;

        if (propertyValue == null) {
            securityGroups = new ArrayList<String>(1);
            securityGroups.add(Utils.DEFAULT_SECURITY_GROUP);
        } else {
            // security group names are separated by ,
            securityGroups = Arrays.asList(propertyValue.split(","));
        }

        return securityGroups;
    }

    private List<String> getSecurityGroupIDs(IProperty[] macProps) {
        String propertyValue = Utils.getSecurityGroupID(macProps, controllerServices);

        List<String> securityGroupIds = null;
        if (!Strings.isNullOrEmpty(propertyValue)) {
            securityGroupIds = Arrays.asList(propertyValue.split(","));
        }

        if (securityGroupIds == null) {
            securityGroupIds = new ArrayList<String>();
        }

        return securityGroupIds;
    }

    private String getSecurityGroupsAsString(List<String> securityGroups) {
        StringBuilder buffer = new StringBuilder();

        for (String group : securityGroups) {
            if (buffer.length() != 0)
                buffer.append(",");

            buffer.append(group);
        }

        return buffer.toString();
    }

    private void validateAndConfigureSecurityGroups(List<String> securityGroupNamesOrIds, AmazonEC2 connector, boolean withNames) throws ConnectorException {
        DescribeSecurityGroupsRequest describeSecurityGroupsRequest = new DescribeSecurityGroupsRequest();
        if (withNames) {
            describeSecurityGroupsRequest.withGroupNames(securityGroupNamesOrIds);
        } else {
            describeSecurityGroupsRequest.withGroupIds(securityGroupNamesOrIds);
        }

        DescribeSecurityGroupsResult describeSecurityGroupsResult = connector.describeSecurityGroups(describeSecurityGroupsRequest);

        String controllerIp = "0.0.0.0/0";
        int agentPort = controllerServices.getDefaultAgentPort();

        // check if any one of the security group
        // already has agent port and controller ip
        List<SecurityGroup> securityGroups = describeSecurityGroupsResult.getSecurityGroups();
        for (SecurityGroup securityGroup : securityGroups) {
            List<IpPermission> ipPermissions = securityGroup.getIpPermissions();
            for (IpPermission permission : ipPermissions) {
                if (permission.getIpRanges().contains(controllerIp)
                        && (agentPort >= permission.getFromPort() &&
                        agentPort <= permission.getToPort())) {
                    return;
                }
            }
        }

        String securityGroupIdOrName = null;

        if (withNames) {

            if (securityGroupNamesOrIds.contains(Utils.DEFAULT_SECURITY_GROUP)) {
                securityGroupIdOrName = Utils.DEFAULT_SECURITY_GROUP;
            } else {
                securityGroupIdOrName = securityGroups.get(0).getGroupName();
            }
        } else {
            securityGroupIdOrName = securityGroups.get(0).getGroupId();
        }

        IpPermission ipPermission = new IpPermission();
        ipPermission.setFromPort(agentPort);
        ipPermission.setToPort(agentPort);
        ipPermission.setIpProtocol("tcp");
        ipPermission.setIpRanges(Lists.newArrayList(controllerIp));

        AuthorizeSecurityGroupIngressRequest securityGroupIngressRequest = new AuthorizeSecurityGroupIngressRequest();
        securityGroupIngressRequest.withIpPermissions(ipPermission);

        if (withNames) {
            securityGroupIngressRequest.withGroupName(securityGroupIdOrName);
        } else {
            securityGroupIngressRequest.withGroupId(securityGroupIdOrName);
        }


        connector.authorizeSecurityGroupIngress(securityGroupIngressRequest);
    }

    private InstanceType getInstanceType(IProperty[] macProps) {
        String instanceTypeString = Utils.getInstanceType(macProps, controllerServices);

        InstanceType instanceType = InstanceType.M1Small;
        try {
            instanceType = InstanceType.fromValue(instanceTypeString);
        } catch (IllegalArgumentException e) { //Should never occur
            logger.log(Level.INFO, "Invalid instance type. Using m1.small", e);
        }
        return instanceType;
    }

    private Instance getEc2Instance(IMachine machine, AmazonEC2 connector) throws ConnectorException {
        DescribeInstancesResult describeInstancesResult = null;
        try {
            DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest();
            describeInstancesResult = connector.describeInstances(describeInstancesRequest.withInstanceIds(machine.getName()));
            List<Reservation> reservations = describeInstancesResult.getReservations();
            if (reservations.size() == 0) {
                // machine not found MUST have been terminated
                return null;
            }

            // always it will be in the first reservation as the query is on
            // only one machine
            List<Instance> instances = reservations.get(0).getInstances();
            for (Instance instance : instances) {
                if (instance.getInstanceId().equals(machine.getName())) {
                    // found the machine
                    return instance;
                }
            }
        } catch (Exception e) {
            throw new ConnectorException(e);
        }

        return null;
    }

    private MachineState getMachineState(Instance instance) {
        InstanceState state = instance.getState();
        String stateName = state.getName();

        if (stateName.equals("running")) {
            return STARTED;
        } else if (stateName.equals("pending")) {
            return STARTING;
        } else if (stateName.equals("shutting-down")) {
            return STOPPING;
        } else if (stateName.equals("terminated")) {
            return STOPPED;
        } else if (stateName.equals("stopped")) {
            return STARTED; // we dont need to create a new state as workflow dont support this
        } else {
            throw new IllegalStateException("State " + stateName + " is not known");
        }
    }

    private void setElasticIp(IMachine machine, String elasticIp, AmazonEC2 connector)
            throws ConnectorException {
        try {
            connector.associateAddress(new AssociateAddressRequest(machine.getName(), elasticIp));

            machine.setIpAddress(elasticIp);
        } catch (AmazonServiceException e) {
            throw new ConnectorException(e);
        } catch (AmazonClientException e) {
            throw new ConnectorException(e);
        }
    }

    private void validate(IProperty[] properties) throws InvalidObjectException {
        AmazonEC2 connector = ConnectorLocator.getInstance().getConnector(properties, controllerServices);

        // this will validate the access and secret keys
        try {
            connector.describeRegions();
        } catch (AmazonServiceException e) {
            logger.log(Level.INFO, "", e);
            throw new InvalidObjectException("The specified " + Utils.ACCESS_KEY_PROP +
                    " and/or " + Utils.SECRET_KEY_PROP + " is not valid.", e);
        } catch (AmazonClientException e) {

            logger.log(Level.INFO, "", e);
            throw new InvalidObjectException("The specified " + Utils.ACCESS_KEY_PROP +
                    " and/or " + Utils.SECRET_KEY_PROP + " is not valid.", e);
        }
    }

    private AmazonEC2 getConnector(IImage image, IComputeCenter computeCenter, IControllerServices controllerServices) {
        IProperty[] properties = computeCenter.getProperties();

        return getConnector(image, properties, controllerServices);
    }

    private AmazonEC2 getConnector(IImage image, IProperty[] properties, IControllerServices controllerServices) {
        AmazonEC2 connector = ConnectorLocator.getInstance().getConnector(properties, controllerServices);

        if (image == null) {
            return connector;
        }

        String region = Utils.getRegion(image.getProperties(), controllerServices);

        if (!Strings.isNullOrEmpty(region)) {
            region = region.toLowerCase();

            connector.setEndpoint(regionVsURLs.get(region));
        }

        return connector;
    }

}
