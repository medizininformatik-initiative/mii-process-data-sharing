package de.medizininformatik_initiative.process.data_sharing;

import java.util.List;
import java.util.Objects;

import org.hl7.fhir.r4.model.CapabilityStatement;
import org.springframework.beans.factory.InitializingBean;

import de.medizininformatik_initiative.processes.common.crypto.KeyProvider;
import de.medizininformatik_initiative.processes.common.util.ConstantsBase;
import dev.dsf.bpe.v2.ProcessPluginApi;
import dev.dsf.bpe.v2.ProcessPluginDeploymentListener;

public class DataSharingProcessPluginDeploymentListener implements ProcessPluginDeploymentListener, InitializingBean
{
	private final ProcessPluginApi api;

	private final String fhirStoreIdDic;
	private final String fhirStoreIdDms;

	private final KeyProvider keyProvider;

	public DataSharingProcessPluginDeploymentListener(ProcessPluginApi api, String fhirStoreIdDic,
			String fhirStoreIdDms, KeyProvider keyProvider)
	{
		this.api = api;
		this.fhirStoreIdDic = fhirStoreIdDic;
		this.fhirStoreIdDms = fhirStoreIdDms;
		this.keyProvider = keyProvider;
	}

	@Override
	public void afterPropertiesSet()
	{
		Objects.requireNonNull(api, "api");
		Objects.requireNonNull(keyProvider, "keyProvider");
	}

	@Override
	public void onProcessesDeployed(List<String> activeProcesses)
	{
		if (activeProcesses.contains(ConstantsDataSharing.PROCESS_NAME_FULL_EXECUTE_DATA_SHARING))
			testConnection(fhirStoreIdDic);

		if (activeProcesses.contains(ConstantsDataSharing.PROCESS_NAME_FULL_MERGE_DATA_SHARING))
		{
			testConnection(fhirStoreIdDms);

			Objects.requireNonNull(keyProvider.getPublicKey(), "PublicKey");
			Objects.requireNonNull(keyProvider.getPrivateKey(), "PrivateKey");
			keyProvider.createPublicKeyIfNotExists(
					ConstantsBase.NAMINGSYSTEM_MII_RECEIVER_KEY_ID_VALUE_DEFAULT_KEY_X25519);
		}
	}

	private void testConnection(String fhirStoreId)
	{
		CapabilityStatement conformance = api.getDsfClientProvider().getById(fhirStoreId)
				.orElseThrow(() -> new RuntimeException("DSF FHIR client '" + fhirStoreId + "' not configured"))
				.getConformance();

		Objects.requireNonNull(conformance, "Connection test for DSF FHIR client '" + fhirStoreId + "' failed"
				+ ConstantsBase.EXCEPTION_MESSAGE_DIVIDER + "CapabilityStatement is null");
	}
}
