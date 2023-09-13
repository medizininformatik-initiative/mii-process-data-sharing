package de.medizininformatik_initiative.process.data_sharing;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.processes.common.crypto.KeyProvider;
import de.medizininformatik_initiative.processes.common.fhir.client.FhirClientFactory;
import dev.dsf.bpe.v1.ProcessPluginDeploymentStateListener;

public class DataSharingProcessPluginDeploymentStateListener
		implements ProcessPluginDeploymentStateListener, InitializingBean
{
	private final FhirClientFactory dicFhirClientFactory;
	private final FhirClientFactory dmsFhirClientFactory;

	private final KeyProvider keyProvider;

	public DataSharingProcessPluginDeploymentStateListener(FhirClientFactory dicFhirClientFactory,
			FhirClientFactory dmsFhirClientConfig, KeyProvider keyProvider)
	{
		this.dicFhirClientFactory = dicFhirClientFactory;
		this.dmsFhirClientFactory = dmsFhirClientConfig;
		this.keyProvider = keyProvider;
	}

	@Override
	public void afterPropertiesSet()
	{
		Objects.requireNonNull(dicFhirClientFactory, "dicFhirClientFactory");
		Objects.requireNonNull(dmsFhirClientFactory, "dmsFhirClientFactory");
		Objects.requireNonNull(keyProvider, "keyProvider");
	}

	@Override
	public void onProcessesDeployed(List<String> activeProcesses)
	{
		if (activeProcesses.contains(ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING))
			dicFhirClientFactory.testConnection();

		if (activeProcesses.contains(ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING))
		{
			dmsFhirClientFactory.testConnection();
			keyProvider.createPublicKeyIfNotExists();
		}
	}
}
