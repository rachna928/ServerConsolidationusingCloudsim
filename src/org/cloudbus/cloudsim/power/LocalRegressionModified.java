package org.cloudbus.cloudsim.power;

//public class LocalRegressionModified {
//
//}

//package org.cloudbus.cloudsim.power;

import java.util.List;
import java.util.Set;
import java.lang.Math.*;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.power.*;
import org.cloudbus.cloudsim.power.PowerVmAllocationPolicyMigrationAbstract;
import org.cloudbus.cloudsim.util.MathUtil;
public class LocalRegressionModified extends PowerVmAllocationPolicyMigrationAbstract{


//public class PowerVmAllocationPolicyMigrationLocalRegression extends PowerVmAllocationPolicyMigrationAbstract {

	/** The scheduling interval. */
	private double schedulingInterval;

	/** The safety parameter. */
	private double safetyParameter;

	/** The fallback vm allocation policy. */
	private PowerVmAllocationPolicyMigrationAbstract fallbackVmAllocationPolicy;

	/**
	 * Instantiates a new power vm allocation policy migration local regression.
	 * 
	 * @param hostList the host list
	 * @param vmSelectionPolicy the vm selection policy
	 * @param schedulingInterval the scheduling interval
	 * @param fallbackVmAllocationPolicy the fallback vm allocation policy
	 * @param utilizationThreshold the utilization threshold
	 */
	public LocalRegressionModified(
			List<? extends Host> hostList,
			PowerVmSelectionPolicy vmSelectionPolicy,
			double safetyParameter,
			double schedulingInterval,
			PowerVmAllocationPolicyMigrationAbstract fallbackVmAllocationPolicy,
			double utilizationThreshold) {
		super(hostList, vmSelectionPolicy);
		setSafetyParameter(safetyParameter);
		setSchedulingInterval(schedulingInterval);
		setFallbackVmAllocationPolicy(fallbackVmAllocationPolicy);
	}

	/**
	 * Instantiates a new power vm allocation policy migration local regression.
	 * 
	 * @param hostList the host list
	 * @param vmSelectionPolicy the vm selection policy
	 * @param schedulingInterval the scheduling interval
	 * @param fallbackVmAllocationPolicy the fallback vm allocation policy
	 */
	public LocalRegressionModified(
			List<? extends Host> hostList,
			PowerVmSelectionPolicy vmSelectionPolicy,
			double safetyParameter,
			double schedulingInterval,
			PowerVmAllocationPolicyMigrationAbstract fallbackVmAllocationPolicy) {
		super(hostList, vmSelectionPolicy);
		setSafetyParameter(safetyParameter);
		setSchedulingInterval(schedulingInterval);
		setFallbackVmAllocationPolicy(fallbackVmAllocationPolicy);
	}

	/**
	 * Checks if is host over utilized.
	 * 
	 * @param host the host
	 * @return true, if is host over utilized
	 */
	@Override
	protected boolean isHostOverUtilized(PowerHost host) {
		PowerHostUtilizationHistory _host = (PowerHostUtilizationHistory) host;
		
		
		
		double[] utilizationHistory = _host.getUtilizationHistory();
		int length = 10; // we use 10 to make the regression responsive enough to latest values
		if (utilizationHistory.length < length) {
			return getFallbackVmAllocationPolicy().isHostOverUtilized(host);
		}
		double[] utilizationHistoryReversed = new double[length];
		for (int i = 0; i < length; i++) {
			utilizationHistoryReversed[i] = utilizationHistory[length - i - 1];
		}
		double[] estimates = null;
		try {
			estimates = getParameterEstimates(utilizationHistoryReversed);
		} catch (IllegalArgumentException e) {
			return getFallbackVmAllocationPolicy().isHostOverUtilized(host);
		}
		double migrationIntervals = Math.ceil(getMaximumVmMigrationTime(_host) / getSchedulingInterval());
		double predictedUtilization = estimates[0] + estimates[1] * (length + migrationIntervals);
		predictedUtilization *= getSafetyParameter();
		
		double totalPredictedMips =0;
		addHistoryEntry(host, predictedUtilization);
		double hMips = _host.getTotalMips();
		for (Vm vm : _host.getVmList()) {
			PowerVm pwm = (PowerVm) vm;
			
			double sd = java.lang.Math.sqrt(pwm.getUtilizationVariance());
			//System.out.println("mips is "+pwm.getUtilizationMean()+" sd "+sd+" hmips "+hMips+" pu "+predictedUtilization);
			 totalPredictedMips+=pwm.getUtilizationMean()+Math.sqrt(pwm.getUtilizationVariance());
		}
		
		System.out.println("mips is "+totalPredictedMips+" hmips "+hMips);
		
		return (totalPredictedMips / hMips) >=1;
		//return predictedUtilization >= 1;
	}

	/**
	 * Gets the parameter estimates.
	 * 
	 * @param utilizationHistoryReversed the utilization history reversed
	 * @return the parameter estimates
	 */
	protected double[] getParameterEstimates(double[] utilizationHistoryReversed) {
		return MathUtil.getLoessParameterEstimates(utilizationHistoryReversed);
	}

	/**
	 * Gets the maximum vm migration time.
	 * 
	 * @param host the host
	 * @return the maximum vm migration time
	 */
	protected double getMaximumVmMigrationTime(PowerHost host) {
		int maxRam = Integer.MIN_VALUE;
		for (Vm vm : host.getVmList()) {
			int ram = vm.getRam();
			if (ram > maxRam) {
				maxRam = ram;
			}
		}
		return maxRam / ((double) host.getBw() / (2 * 8000));
	}

	/**
	 * Sets the scheduling interval.
	 * 
	 * @param schedulingInterval the new scheduling interval
	 */
	protected void setSchedulingInterval(double schedulingInterval) {
		this.schedulingInterval = schedulingInterval;
	}

	/**
	 * Gets the scheduling interval.
	 * 
	 * @return the scheduling interval
	 */
	protected double getSchedulingInterval() {
		return schedulingInterval;
	}

	/**
	 * Sets the fallback vm allocation policy.
	 * 
	 * @param fallbackVmAllocationPolicy the new fallback vm allocation policy
	 */
	public void setFallbackVmAllocationPolicy(
			PowerVmAllocationPolicyMigrationAbstract fallbackVmAllocationPolicy) {
		this.fallbackVmAllocationPolicy = fallbackVmAllocationPolicy;
	}

	/**
	 * Gets the fallback vm allocation policy.
	 * 
	 * @return the fallback vm allocation policy
	 */
	public PowerVmAllocationPolicyMigrationAbstract getFallbackVmAllocationPolicy() {
		return fallbackVmAllocationPolicy;
	}

	public double getSafetyParameter() {
		return safetyParameter;
	}

	public void setSafetyParameter(double safetyParameter) {
		this.safetyParameter = safetyParameter;
	}
	
	
	@Override
	public PowerHost findHostForVm(Vm vm, Set<? extends Host> excludedHosts) {
		double minCPU = Double.MAX_VALUE;
		double inactiveCPU = Double.MAX_VALUE;
		PowerHost allocatedHost = null;
		PowerHost inactiveHost = null;

		for (PowerHost host : this.<PowerHost> getHostList()) {
			if (excludedHosts.contains(host)) {
				continue;
			}
			if (host.isSuitableForVm(vm)) {				
				if (getUtilizationOfCpuMips(host) != 0 && isHostOverUtilizedAfterAllocation(host, vm)) {
					continue;
				}
				
				double availableCPU = host.getAvailableMips();
				if(host.getUtilizationMips() == 0) {			
					if (availableCPU < inactiveCPU ) {
						inactiveCPU = availableCPU;
						inactiveHost = host;
						}	
					continue;
					}			
						if (availableCPU < minCPU ) {
					minCPU = availableCPU;
					allocatedHost = host;
					}
				else if (availableCPU == minCPU ) {
					if (host.getRamProvisioner().getAvailableRam() < allocatedHost.getRamProvisioner().getAvailableRam()) {
						allocatedHost = host;							
					}
				}
				
			}
		}
		
		return (allocatedHost!=null) ?allocatedHost:inactiveHost;
	}

}
